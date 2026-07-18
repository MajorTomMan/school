#!/usr/bin/env python3
"""Build a School cloud-course release from one structured course.json file."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
from datetime import datetime, timezone
import zipfile


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def drive_download_url(file_id: str) -> str:
    value = file_id.strip()
    return f"https://drive.google.com/uc?export=download&id={value}" if value else ""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True, help="Path to course.json")
    parser.add_argument("--output", type=Path, required=True, help="Release output directory")
    parser.add_argument("--textbook-version", type=int, default=1)
    parser.add_argument("--content-version", type=int, default=1)
    parser.add_argument("--minimum-app-version", type=int, default=1)
    parser.add_argument("--full-file-id", default="", help="Google Drive ID of the uploaded ZIP")
    parser.add_argument("--course-file-id", default="", help="Google Drive ID of the uploaded course.json")
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

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    course_output = output / "course.json"
    shutil.copyfile(source, course_output)

    package_name = f"{textbook_id}-v{args.textbook_version}.zip"
    package_path = output / package_name
    with zipfile.ZipFile(package_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
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
                "fullPackage": {
                    "path": package_name,
                    "url": drive_download_url(args.full_file_id),
                    "size": package_path.stat().st_size,
                    "sha256": sha256(package_path),
                },
                "files": [
                    {
                        "path": "course.json",
                        "url": drive_download_url(args.course_file_id),
                        "size": course_output.stat().st_size,
                        "sha256": sha256(course_output),
                    }
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
    if not args.full_file_id or not args.course_file_id:
        print("note: upload course.json and the ZIP to Google Drive, then rerun with their file IDs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
