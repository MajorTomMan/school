# School 初中数学课程包

APK 只保留课程协议、解析器、页面渲染器、教材 PDF 渲染器和确定性可视化。六册人教版初中数学课程正文由 `course-latest` Release 发布；教材 PDF 保存在 Google Drive，并由课程清单作为外部文件下载。

## 教材与课程源

```text
tools/course-content/
├── math-textbooks.json
├── pep-math-7-1/course.json
├── pep-math-7-2/course.json
├── pep-math-8-1/course.json
├── pep-math-8-2/course.json
├── pep-math-9-1/course.json
└── pep-math-9-2/course.json
```

课程源由教材 PDF 自动生成。生成器保留教材印刷页顺序，将过长页面按教材环节拆分，并为每个课程页写入 `sourceAnchors`。课程发布前，校验器会在对应 PDF 印刷页上核对锚点及每个教材来源块。

## 本地生成与验证

```bash
python3 tools/course-content/generate_all_math_courses.py \
  --pdf-dir /path/to/math-pdfs \
  --output-dir tools/course-content

python3 tools/course-content/validate_course_sources.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --pdf '/path/to/math-pdfs/义务教育教科书·数学七年级上册.pdf' \
  --page-index-offset 7 \
  --expected-page-count 202
```

六册教材都使用 `pageIndexOffset = 7`。`math-textbooks.json` 保存 Drive 文件 ID、大小、SHA-256、页数及课程源路径。

## 构建发布资源

```bash
python3 tools/course-content/build_all_math_course_release.py \
  --registry tools/course-content/math-textbooks.json \
  --source-root tools/course-content \
  --pdf-root /path/to/math-pdfs \
  --output build/course-release/math \
  --textbook-version 1 \
  --content-version 1 \
  --minimum-app-version 26
```

生成一个包含六册教材声明的 `manifest.json`，以及每册独立的 `*-course.json` 和 `*.zip`。课程 ZIP 只包含 `course.json`，不重复携带教材 PDF。
