#!/usr/bin/env python3
"""Build one business-only School course package and file-integrity manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import zipfile

from course_package import validate_course

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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


def archive_spec(path: str, local_file: Path, url: str) -> dict[str, object]:
    return {
        "path": path,
        "url": url,
        "size": local_file.stat().st_size,
        "sha256": sha256(local_file),
    }


def file_spec(path: str, local_file: Path, url: str, bundled: bool) -> dict[str, object]:
    return {
        **archive_spec(path, local_file, url),
        "bundled": bundled,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--release-base-url", default="https://github.com/MajorTomMan/School/releases/download/course-latest")
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--pdf-url", required=True)
    parser.add_argument("--pdf-sha256", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = args.source.resolve()
    pdf = args.pdf.resolve()
    if not source.is_file() or not pdf.is_file():
        raise SystemExit("course source or textbook PDF is missing")
    payload = json.loads(source.read_text(encoding="utf-8"))
    validate_course(payload)
    textbook = payload["textbook"]
    textbook_id = textbook["id"]
    pdf_path = safe_relative_path(textbook["pdf"]["path"])
    with pdf.open("rb") as stream:
        if stream.read(5) != b"%PDF-":
            raise SystemExit("--pdf does not point to a PDF file")
    digest = sha256(pdf)
    if digest != validate_sha256(args.pdf_sha256):
        raise SystemExit("PDF SHA-256 mismatch")

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    course_name = f"{textbook_id}-course.json"
    course_output = output / course_name
    course_output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    package_name = f"{textbook_id}.zip"
    package_path = output / package_name
    with zipfile.ZipFile(package_path, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
        archive.write(course_output, "course.json")

    manifest = {
        "textbooks": [{
            "id": textbook_id,
            "package": archive_spec(package_name, package_path, release_url(args.release_base_url, package_name)),
            "files": [
                file_spec("course.json", course_output, release_url(args.release_base_url, course_name), True),
                {
                    "path": pdf_path,
                    "url": args.pdf_url.strip(),
                    "size": pdf.stat().st_size,
                    "sha256": digest,
                    "bundled": False,
                },
            ],
        }],
    }
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"course:   {course_output}")
    print(f"package:  {package_path}")
    print(f"manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
