# School 课程包发布

APK 只包含课程数据结构、解析器、同步缓存、页面渲染器、PDF 渲染器、可视化与确定性算法。教材目录、章节、课程正文、例题、小结和习题由 GitHub Release 发布；教材 PDF 暂时保存在 Google Drive，由课程清单引用并下载到应用私有缓存。

应用不提供系统文件选择器、教材目录授权、PDF 导入、端上 OCR、目录识别或本地抽题。

## 发布位置

```text
Google Drive
└── 义务教育教科书·数学七年级上册.pdf

GitHub Release: course-latest
├── manifest.json
├── pep-math-7-1-course.json
└── pep-math-7-1.zip
```

完整 ZIP 只包含 `course.json` 和其他可发布课程资源，不包含教材 PDF。PDF 在 `manifest.json` 中作为外部文件声明。

## 教材 PDF 元数据

当前七年级上册数学教材：

```text
Drive file ID: 1zPJIoh7Ora3AOMLXfDll8YbAZ8u1v78N
size:          12915486
SHA-256:       11b6f1fbfa46eee4158953ef745ae1e6fbe6b9527a1423d55cbe75729e8210b9
pageCount:     202
pageIndexOffset: 7
```

Google Drive 文件必须设置为“知道链接的任何人可查看”，否则未登录的 APK 无法下载。

## 本地生成

不需要把 PDF 复制进仓库。使用已经确认的 PDF 元数据即可生成课程资源：

```bash
python3 tools/course-content/build_course_release.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --output build/course-release/pep-math-7-1 \
  --textbook-version 1 \
  --content-version 1 \
  --minimum-app-version 22 \
  --release-base-url 'https://github.com/MajorTomMan/School/releases/download/course-latest' \
  --pdf-file-id '1zPJIoh7Ora3AOMLXfDll8YbAZ8u1v78N' \
  --pdf-size 12915486 \
  --pdf-sha256 '11b6f1fbfa46eee4158953ef745ae1e6fbe6b9527a1423d55cbe75729e8210b9' \
  --pdf-page-count 202 \
  --pdf-page-index-offset 7
```

生成结果：

```text
build/course-release/pep-math-7-1/
├── manifest.json
├── pep-math-7-1-course.json
└── pep-math-7-1.zip
```

ZIP 中只有运行时课程文件：

```text
course.json
```

也可以传入 `--pdf /path/to/textbook.pdf`，让工具在本机重新核对 PDF 文件头、大小和 SHA-256。该 PDF 仍不会写入输出目录或 ZIP。

## 自动发布

`.github/workflows/course-release.yml` 会在课程源文件合并到 `master` 后自动：

1. 构建课程 JSON 和完整 ZIP；
2. 校验 PDF 被标记为外部文件；
3. 检查 ZIP 中不含 PDF；
4. 创建或更新 `course-latest` Release；
5. 覆盖上传三个稳定资源。

APK 默认读取：

```text
https://github.com/MajorTomMan/School/releases/download/course-latest/manifest.json
```

仍可通过 `SCHOOL_COURSE_MANIFEST_URL` 或 Gradle 属性 `schoolCourseManifestUrl` 覆盖。

## 客户端更新规则

- 本地没有该教材：下载完整课程 ZIP，再单独下载 Google Drive PDF。
- 数据结构版本变化：下载完整课程 ZIP；摘要未变化的 PDF 从旧缓存复用。
- 只修改课程 JSON：下载 JSON 或完整课程 ZIP，不重新下载 PDF。
- 只替换教材 PDF：下载新的 PDF；更新计划会把外部文件体积计入全量成本。
- 增量校验或安装失败：自动改用完整课程 ZIP，并补齐所需外部文件。
- 下载、SHA-256、JSON、PDF 文件头、PDF 页数或切换失败：保留旧缓存。
- 云端清单无法访问：使用本地缓存；没有缓存时显示同步状态和重试入口。

所有文件先写入 `filesDir/course-packs/staging`。客户端会优先复用 `active` 中摘要一致的教材 PDF，缺失或变化时才从 Drive 下载。完成大小、SHA-256、安全路径、JSON 和 PDF 校验后，才会切换到 `active`；旧版本保留在 `backup`。

## 内容兼容规则

云端只能声明 APK 已实现的页面块和可视化名称，不能下发 Kotlin、JavaScript 或其他可执行代码。页面、习题和学习进度使用稳定 ID；修改文字或可视化参数时不要更换 ID。
