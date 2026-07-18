# School 课程包发布

APK 只包含课程数据结构、解析器、同步缓存、页面渲染器、PDF 渲染器、可视化与确定性算法。教材目录、章节、课程正文、例题、小结、习题和教材 PDF 全部通过远端清单同步到应用私有缓存；云端不可用时使用上一次有效缓存，没有缓存时显示课程数据缺失页面。

应用不再提供系统文件选择器、教材目录授权、PDF 导入、端上 OCR、目录识别或本地抽题。

## Google Drive 目录

建议在 `School 课程发布` 目录中保存：

```text
manifest.json
pep-math-7-1-v1.zip
pep-math-7-1-course-v1.json
pep-math-7-1-textbook-v1.pdf
```

完整 ZIP 用于首次安装、结构升级和大范围修改；独立 `course.json` 与教材 PDF 用于文件级增量更新。只修改课程文字时不会重新下载 PDF；PDF 变化时才下载新的 PDF。

## 生成课程发布文件

```bash
python3 tools/course-content/build_course_release.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --pdf '/path/to/义务教育教科书·数学七年级上册.pdf' \
  --pdf-page-count 202 \
  --pdf-page-index-offset 7 \
  --output build/course-release/pep-math-7-1 \
  --textbook-version 1 \
  --content-version 1 \
  --minimum-app-version 21
```

第一次运行会生成：

```text
course.json
assets/textbook.pdf
pep-math-7-1-v1.zip
manifest.json
```

发布脚本会计算教材 PDF 的 SHA-256，并把以下运行时元数据写入输出版 `course.json`：

```json
{
  "textbook": {
    "pdf": {
      "path": "assets/textbook.pdf",
      "sha256": "...",
      "pageCount": 202,
      "pageIndexOffset": 7
    }
  }
}
```

先把 `course.json`、`assets/textbook.pdf` 和完整 ZIP 上传到 Google Drive，并将三个文件设为持有链接的用户可查看。取得文件 ID 后重新运行：

```bash
python3 tools/course-content/build_course_release.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --pdf '/path/to/义务教育教科书·数学七年级上册.pdf' \
  --pdf-page-count 202 \
  --pdf-page-index-offset 7 \
  --output build/course-release/pep-math-7-1 \
  --textbook-version 1 \
  --content-version 1 \
  --minimum-app-version 21 \
  --course-file-id GOOGLE_DRIVE_COURSE_JSON_ID \
  --pdf-file-id GOOGLE_DRIVE_TEXTBOOK_PDF_ID \
  --full-file-id GOOGLE_DRIVE_FULL_ZIP_ID
```

再上传新生成的 `manifest.json`，并将清单文件设为持有链接的用户可查看。

## 配置 APK

构建时通过环境变量传入清单共享链接或直接下载链接：

```bash
export SCHOOL_COURSE_MANIFEST_URL='https://drive.google.com/file/d/MANIFEST_FILE_ID/view'
./gradlew :app:assembleDebug
```

也可以使用 Gradle 属性：

```bash
./gradlew :app:assembleDebug \
  -PschoolCourseManifestUrl='https://drive.google.com/file/d/MANIFEST_FILE_ID/view'
```

客户端会把常见 Google Drive 分享链接转换为直接下载地址。

## 客户端更新规则

- 本地没有该教材：下载包含 `course.json` 和 PDF 的完整 ZIP。
- 数据结构版本变化：下载完整 ZIP。
- 变化文件总大小小于完整包的 60%：增量下载。
- 只修改课程 JSON：只下载 JSON。
- 只替换教材 PDF：只下载 PDF；若体积接近完整包则自动改用 ZIP。
- 增量文件总大小达到完整包的 60%：下载完整 ZIP。
- 增量校验或安装失败：自动改用完整 ZIP。
- 下载、SHA-256、JSON、PDF 文件头、PDF 页数或切换失败：保留旧缓存。
- 云端清单无法访问：使用本地缓存；没有缓存时显示同步状态和重试入口。

所有文件先写入 `filesDir/course-packs/staging`，完成大小、SHA-256、安全路径、JSON 和 PDF 校验后，才会切换到 `active`。旧版本保留在 `backup`，不会在下载过程中覆盖正在使用的课程。

同步完成后，客户端从 `course.json` 构建教材、章节和小节导航，并直接读取课程包中的 PDF。APK 不包含替代目录、教材正文或本地导入入口，因此每个课程包必须提供完整且稳定的 ID、标题、页码、页面块和 PDF 元数据。

## 内容兼容规则

云端只能声明 APK 已实现的页面块和可视化名称，不能下发 Kotlin、JavaScript 或其他可执行代码。页面、习题和学习进度使用稳定 ID；修改文字或可视化参数时不要更换 ID。
