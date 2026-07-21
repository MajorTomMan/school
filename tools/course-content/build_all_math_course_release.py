#!/usr/bin/env python3
"""Build one file-integrity manifest and six business-only course packages."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import zipfile

from course_package import validate_course
from generate_math_courses import BOOKS, file_sha256


def release_url(base_url: str, name: str) -> str:
    return f"{base_url.rstrip('/')}/{name}"


def archive_spec(path: str, source: Path, url: str) -> dict[str, object]:
    return {
        "path": path,
        "url": url,
        "size": source.stat().st_size,
        "sha256": file_sha256(source),
    }


def file_spec(path: str, source: Path, url: str, bundled: bool) -> dict[str, object]:
    return {
        **archive_spec(path, source, url),
        "bundled": bundled,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--pdf-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--release-base-url", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output = args.output.resolve()
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    textbooks: list[dict[str, object]] = []

    for spec in BOOKS:
        source = args.source_root.resolve() / spec.textbook_id / "course.json"
        pdf = args.pdf_root.resolve() / spec.filename
        if not source.is_file():
            raise SystemExit(f"missing course source: {source}")
        if not pdf.is_file() or file_sha256(pdf) != spec.sha256:
            raise SystemExit(f"textbook PDF digest mismatch: {pdf.name}")
        payload = json.loads(source.read_text(encoding="utf-8"))
        validate_course(payload)
        if payload["textbook"]["id"] != spec.textbook_id:
            raise SystemExit(f"textbook id mismatch: {source}")

        course_name = f"{spec.textbook_id}-course.json"
        course_output = output / course_name
        shutil.copyfile(source, course_output)
        zip_name = f"{spec.textbook_id}.zip"
        zip_path = output / zip_name
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
            archive.write(course_output, "course.json")

        pdf_url = f"https://drive.google.com/file/d/{spec.drive_id}/view"
        textbooks.append({
            "id": spec.textbook_id,
            "package": archive_spec(zip_name, zip_path, release_url(args.release_base_url, zip_name)),
            "files": [
                file_spec("course.json", course_output, release_url(args.release_base_url, course_name), True),
                {
                    "path": "assets/textbook.pdf",
                    "url": pdf_url,
                    "size": pdf.stat().st_size,
                    "sha256": spec.sha256,
                    "bundled": False,
                },
            ],
        })

    (output / "manifest.json").write_text(
        json.dumps({"textbooks": textbooks}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"built {len(textbooks)} textbooks in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
