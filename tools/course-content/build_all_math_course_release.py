#!/usr/bin/env python3
"""Build one manifest and six independent junior-high mathematics course packages."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_path(value: str) -> str:
    path = PurePosixPath(value.strip().replace("\\", "/"))
    if not value or path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise SystemExit(f"unsafe path: {value}")
    return path.as_posix()


def file_spec(path: str, source: Path, url: str, in_full_package: bool) -> dict[str, object]:
    return {
        "path": path,
        "url": url,
        "size": source.stat().st_size,
        "sha256": sha256(source),
        "inFullPackage": in_full_package,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--registry", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--pdf-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--textbook-version", type=int, required=True)
    parser.add_argument("--content-version", type=int, required=True)
    parser.add_argument("--minimum-app-version", type=int, default=1)
    parser.add_argument(
        "--release-base-url",
        default="https://github.com/MajorTomMan/School/releases/download/course-latest",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    registry = json.loads(args.registry.read_text(encoding="utf-8"))
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    base_url = args.release_base_url.rstrip("/")
    manifest_textbooks: list[dict[str, object]] = []

    for item in registry.get("textbooks", []):
        textbook_id = str(item["id"])
        source = args.source_root / str(item["coursePath"])
        if not source.is_file():
            raise SystemExit(f"missing course source: {source}")
        course = json.loads(source.read_text(encoding="utf-8"))
        if course.get("schemaVersion") != 1 or course.get("textbook", {}).get("id") != textbook_id:
            raise SystemExit(f"invalid course source: {source}")

        pdf_meta = item["pdf"]
        pdf_name = str(course["textbook"]["title"]) + ".pdf"
        pdf = args.pdf_root / pdf_name
        if not pdf.is_file():
            raise SystemExit(f"missing PDF: {pdf}")
        if pdf.stat().st_size != int(pdf_meta["size"]) or sha256(pdf) != str(pdf_meta["sha256"]):
            raise SystemExit(f"PDF metadata mismatch: {pdf}")

        pdf_path = safe_path(str(course["textbook"].get("pdf", {}).get("path") or "assets/textbook.pdf"))
        pdf_url = f"https://drive.google.com/file/d/{pdf_meta['fileId']}/view"
        course["textbook"]["pdf"] = {
            "path": pdf_path,
            "url": pdf_url,
            "size": int(pdf_meta["size"]),
            "sha256": str(pdf_meta["sha256"]),
            "pageCount": int(pdf_meta["pageCount"]),
            "pageIndexOffset": int(pdf_meta["pageIndexOffset"]),
        }

        course_asset_name = f"{textbook_id}-course.json"
        course_asset = output / course_asset_name
        course_asset.write_text(json.dumps(course, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        zip_name = f"{textbook_id}.zip"
        zip_path = output / zip_name
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
            archive.write(course_asset, arcname="course.json")

        manifest_textbooks.append({
            "id": textbook_id,
            "title": str(item["title"]),
            "version": args.textbook_version,
            "minimumAppVersion": args.minimum_app_version,
            "fullPackage": file_spec(zip_name, zip_path, f"{base_url}/{zip_name}", True),
            "files": [
                file_spec("course.json", course_asset, f"{base_url}/{course_asset_name}", True),
                {
                    "path": pdf_path,
                    "url": pdf_url,
                    "size": int(pdf_meta["size"]),
                    "sha256": str(pdf_meta["sha256"]),
                    "inFullPackage": False,
                },
            ],
            "deletedFiles": [],
        })

    manifest = {
        "schemaVersion": 1,
        "contentVersion": args.content_version,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "textbooks": manifest_textbooks,
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"built {len(manifest_textbooks)} textbook packages in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
