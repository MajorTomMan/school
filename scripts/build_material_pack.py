#!/usr/bin/env python3
"""Build a School Material Pack v1 ZIP archive."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

PACK_ID_PATTERN = re.compile(r"[a-z0-9][a-z0-9._-]{2,63}\Z")
BUFFER_SIZE = 1024 * 1024


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(BUFFER_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def existing_file(value: str) -> Path:
    path = Path(value).expanduser().resolve()
    if not path.is_file():
        raise argparse.ArgumentTypeError(f"文件不存在：{path}")
    return path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="构建 School Material Pack v1")
    parser.add_argument("--pdf", required=True, type=existing_file, help="教材 PDF")
    parser.add_argument("--catalog", required=True, type=existing_file, help="catalog.json")
    parser.add_argument("--output", required=True, type=Path, help="输出 ZIP")
    parser.add_argument("--pack-id", required=True, help="稳定资源包标识")
    parser.add_argument("--version", required=True, help="资源包版本")
    parser.add_argument("--title", required=True, help="教材标题")
    parser.add_argument("--subject", required=True, help="科目")
    parser.add_argument("--page-index-offset", type=int, default=0, help="印刷页码到 PDF 索引的偏移")
    parser.add_argument("--force", action="store_true", help="覆盖已有输出文件")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if not PACK_ID_PATTERN.fullmatch(args.pack_id):
        raise SystemExit("pack-id 只能包含小写字母、数字、点、下划线和短横线，长度 3—64")
    if not -10_000 <= args.page_index_offset <= 10_000:
        raise SystemExit("page-index-offset 超出允许范围")

    output = args.output.expanduser().resolve()
    if output.exists() and not args.force:
        raise SystemExit(f"输出文件已存在：{output}；使用 --force 覆盖")
    output.parent.mkdir(parents=True, exist_ok=True)

    try:
        catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"catalog.json 无效：{error}") from error
    if not isinstance(catalog, dict):
        raise SystemExit("catalog.json 根节点必须是对象")

    pdf_digest = sha256(args.pdf)
    manifest = {
        "schemaVersion": 1,
        "packId": args.pack_id,
        "version": args.version.strip(),
        "title": args.title.strip(),
        "subject": args.subject.strip(),
        "catalog": "catalog.json",
        "pdf": {
            "path": "books/textbook.pdf",
            "sha256": pdf_digest,
            "pageIndexOffset": args.page_index_offset,
        },
    }
    if not all((manifest["version"], manifest["title"], manifest["subject"])):
        raise SystemExit("version、title 和 subject 不能为空")

    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
            archive.writestr(
                "manifest.json",
                json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8"),
            )
            archive.writestr(
                "catalog.json",
                json.dumps(catalog, ensure_ascii=False, indent=2).encode("utf-8"),
            )
            archive.write(args.pdf, "books/textbook.pdf")
        temporary.replace(output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise

    print(f"已生成：{output}")
    print(f"PDF SHA-256：{pdf_digest}")
    print(f"文件大小：{output.stat().st_size / (1024 * 1024):.2f} MB")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("已取消", file=sys.stderr)
        raise SystemExit(130)
