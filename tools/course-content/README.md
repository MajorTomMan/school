# School 课程包发布

APK 只包含课程结构、渲染器、可视化与确定性算法。课程正文通过远端清单同步到应用私有缓存；云端不可用时继续使用上一次有效缓存或 APK 内置课程。

## Google Drive 目录

建议在 `School 课程发布` 目录中保存：

```text
manifest.json
pep-math-7-1-v1.zip
pep-math-7-1-course-v1.json
```

完整 ZIP 用于首次安装、结构升级和大范围修改；独立 `course.json` 用于小范围增量更新。

## 生成课程发布文件

```bash
python3 tools/course-content/build_course_release.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --output build/course-release/pep-math-7-1 \
  --textbook-version 1 \
  --content-version 1 \
  --minimum-app-version 21
```

第一次运行会生成：

```text
course.json
pep-math-7-1-v1.zip
manifest.json
```

先把 `course.json` 和 ZIP 上传到 Google Drive，并将两个文件设为持有链接的用户可查看。取得文件 ID 后重新运行：

```bash
python3 tools/course-content/build_course_release.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --output build/course-release/pep-math-7-1 \
  --textbook-version 1 \
  --content-version 1 \
  --minimum-app-version 21 \
  --course-file-id GOOGLE_DRIVE_COURSE_JSON_ID \
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

- 本地没有该教材：下载完整 ZIP。
- 数据结构版本变化：下载完整 ZIP。
- 变化文件总大小小于完整包的 60%：增量下载。
- 变化文件总大小达到完整包的 60%：下载完整 ZIP。
- 增量校验或安装失败：自动改用完整 ZIP。
- 下载、SHA-256、JSON 解析或切换失败：保留旧缓存。
- 云端清单无法访问：使用本地缓存；没有缓存时使用 APK 内置课程。

所有文件先写入 `filesDir/course-packs/staging`，完成大小、SHA-256、路径和 JSON 校验后，才会切换到 `active`。旧版本保留在 `backup`，不会在下载过程中覆盖正在使用的课程。

## 内容兼容规则

云端只能声明 APK 已实现的页面块和可视化名称，不能下发 Kotlin、JavaScript 或其他可执行代码。页面、习题和学习进度使用稳定 ID；修改文字或可视化参数时不要更换 ID。
