#!/usr/bin/env python3
"""Build a School course release whose textbook PDF remains an external cloud asset."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import zipfile


SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def drive_download_url(file_id: str) -> str:
    value = file_id.strip()
    return f"https://drive.google.com/file/d/{value}/view" if value else ""


def safe_relative_path(value: str) -> str:
    normalized = value.strip().replace("\\", "/")
    path = PurePosixPath(normalized)
    if not normalized or path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise SystemExit(f"unsafe package path: {value}")
    return path.as_posix()


def validate_sha256(value: str) -> str:
    normalized = "".join(value.lower().split())
    if not SHA256_PATTERN.fullmatch(normalized):
        raise SystemExit("textbook PDF SHA-256 must contain exactly 64 hexadecimal characters")
    return normalized


def release_url(base_url: str, file_name: str) -> str:
    base = base_url.strip().rstrip("/")
    if not base:
        raise SystemExit("--release-base-url is required")
    return f"{base}/{file_name}"


def local_file_spec(
    package_path: str,
    local_file: Path,
    url: str,
    *,
    in_full_package: bool = True,
) -> dict[str, object]:
    return {
        "path": package_path,
        "url": url,
        "size": local_file.stat().st_size,
        "sha256": sha256(local_file),
        "inFullPackage": in_full_package,
    }


def external_file_spec(
    package_path: str,
    url: str,
    size: int,
    digest: str,
) -> dict[str, object]:
    return {
        "path": package_path,
        "url": url,
        "size": size,
        "sha256": digest,
        "inFullPackage": False,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True, help="Path to source course.json")
    parser.add_argument("--output", type=Path, required=True, help="Release output directory")
    parser.add_argument("--textbook-version", type=int, required=True)
    parser.add_argument("--content-version", type=int, required=True)
    parser.add_argument("--minimum-app-version", type=int, default=1)
    parser.add_argument(
        "--release-base-url",
        default="https://github.com/MajorTomMan/School/releases/download/course-latest",
        help="Stable base URL used by manifest assets",
    )
    parser.add_argument("--pdf", type=Path, default=None, help="Optional local PDF used only to verify metadata")
    parser.add_argument("--pdf-file-id", default="", help="Google Drive file ID of the textbook PDF")
    parser.add_argument("--pdf-url", default="", help="Google Drive sharing or direct download URL")
    parser.add_argument("--pdf-size", type=int, default=0, help="Textbook PDF size in bytes")
    parser.add_argument("--pdf-sha256", default="", help="Textbook PDF SHA-256")
    parser.add_argument("--pdf-page-count", type=int, default=0, help="Textbook PDF page count")
    parser.add_argument("--pdf-page-index-offset", type=int, default=None, help="Printed-page to PDF-index offset")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = args.source.resolve()
    if not source.is_file():
        raise SystemExit(f"course source not found: {source}")

    payload = json.loads(source.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != 1:
        raise SystemExit("only schemaVersion=1 is supported")
    textbook = payload.get("textbook") or {}
    textbook_id = str(textbook.get("id") or "").strip()
    textbook_title = str(textbook.get("title") or "").strip()
    if not textbook_id or not textbook_title:
        raise SystemExit("textbook.id and textbook.title are required")

    pdf_metadata = textbook.get("pdf") or {}
    pdf_path = safe_relative_path(str(pdf_metadata.get("path") or "assets/textbook.pdf"))
    if not pdf_path.lower().endswith(".pdf"):
        raise SystemExit("textbook.pdf.path must end with .pdf")

    pdf_url = (
        args.pdf_url.strip()
        or drive_download_url(args.pdf_file_id)
        or str(pdf_metadata.get("url") or "").strip()
    )
    if not pdf_url:
        raise SystemExit("provide --pdf-file-id, --pdf-url, or textbook.pdf.url")

    pdf_size = args.pdf_size or int(pdf_metadata.get("size") or 0)
    pdf_digest_raw = args.pdf_sha256 or str(pdf_metadata.get("sha256") or "")
    local_pdf = args.pdf.resolve() if args.pdf is not None else None
    if local_pdf is not None:
        if not local_pdf.is_file():
            raise SystemExit(f"textbook PDF not found: {local_pdf}")
        with local_pdf.open("rb") as stream:
            if stream.read(5) != b"%PDF-":
                raise SystemExit("--pdf does not point to a PDF file")
        actual_size = local_pdf.stat().st_size
        actual_digest = sha256(local_pdf)
        if pdf_size and pdf_size != actual_size:
            raise SystemExit(f"PDF size mismatch: declared {pdf_size}, actual {actual_size}")
        if pdf_digest_raw and validate_sha256(pdf_digest_raw) != actual_digest:
            raise SystemExit("PDF SHA-256 mismatch")
        pdf_size = actual_size
        pdf_digest_raw = actual_digest

    if pdf_size <= 0:
        raise SystemExit("provide --pdf-size, --pdf, or textbook.pdf.size")
    pdf_digest = validate_sha256(pdf_digest_raw)
    page_count = args.pdf_page_count or int(pdf_metadata.get("pageCount") or 0)
    if page_count <= 0:
        raise SystemExit("provide --pdf-page-count or textbook.pdf.pageCount")
    page_index_offset = (
        args.pdf_page_index_offset
        if args.pdf_page_index_offset is not None
        else int(pdf_metadata.get("pageIndexOffset") or 0)
    )
    if not -10_000 <= page_index_offset <= 10_000:
        raise SystemExit("textbook.pdf.pageIndexOffset is out of range")

    textbook["pdf"] = {
        "path": pdf_path,
        "url": pdf_url,
        "size": pdf_size,
        "sha256": pdf_digest,
        "pageCount": page_count,
        "pageIndexOffset": page_index_offset,
    }
    payload["textbook"] = textbook

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    course_asset_name = f"{textbook_id}-course.json"
    course_output = output / course_asset_name
    course_output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    package_name = f"{textbook_id}.zip"
    package_path = output / package_name
    with zipfile.ZipFile(package_path, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
        archive.write(course_output, arcname="course.json")

    manifest = {
        "schemaVersion": 1,
        "contentVersion": args.content_version,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "textbooks": [
            {
                "id": textbook_id,
                "title": textbook_title,
                "version": args.textbook_version,
                "minimumAppVersion": args.minimum_app_version,
                "fullPackage": local_file_spec(
                    package_name,
                    package_path,
                    release_url(args.release_base_url, package_name),
                ),
                "files": [
                    local_file_spec(
                        "course.json",
                        course_output,
                        release_url(args.release_base_url, course_asset_name),
                        in_full_package=True,
                    ),
                    external_file_spec(pdf_path, pdf_url, pdf_size, pdf_digest),
                ],
                "deletedFiles": [],
            }
        ],
    }
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"course:   {course_output}")
    print(f"full ZIP: {package_path}")
    print(f"manifest: {manifest_path}")
    print(f"PDF:      external ({pdf_url})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
