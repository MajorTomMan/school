# School 课程包发布

APK 包含课程解析、严格校验、缓存切换、页面排版、场景渲染和确定性算法。教材目录、课程正文、例题、练习与场景业务参数由课程包发布。

## 发布内容

```text
GitHub Release: course-latest
├── manifest.json
├── pep-math-7-1-course.json
└── pep-math-7-1.zip
```

`course.json` 只保存课程业务数据。ZIP 只包含 `course.json` 和课程资源，不包含教材 PDF。教材 PDF 由 `manifest.json` 作为外部文件声明。

`manifest.json` 只保存更新与完整性校验所需字段：教材 ID、完整包文件信息，以及每个安装文件的相对路径、下载地址、大小、SHA-256 和是否位于完整包中。它不包含 schema、内容版本、最低 APK 版本、生成时间或删除列表。

## 单本教材构建

```bash
python3 tools/course-content/build_course_release.py \
  --source tools/course-content/pep-math-7-1/course.json \
  --output build/course-release/pep-math-7-1 \
  --release-base-url 'https://github.com/MajorTomMan/School/releases/download/course-latest' \
  --pdf '/path/to/义务教育教科书·数学七年级上册.pdf' \
  --pdf-url 'https://drive.google.com/file/d/FILE_ID/view' \
  --pdf-sha256 '11b6f1fbfa46eee4158953ef745ae1e6fbe6b9527a1423d55cbe75729e8210b9'
```

工具会验证课程业务结构和本地 PDF 摘要，但设备上的 APK 仍会在启用课程前重新完成全部校验。

## 自动发布

`.github/workflows/course-release.yml` 会：

1. 从六册教材生成课程骨架；
2. 覆盖人工校对的小节；
3. 验证教材原文与印刷页范围；
4. 审计所有业务字段和场景；
5. 构建六个课程 ZIP 与一个文件完整性清单；
6. 合并到 `master` 后覆盖 `course-latest` Release。

## 客户端更新

- 本地没有教材时，下载课程 ZIP，再补齐外部 PDF；
- 文件摘要不同时，只下载变化文件；
- 增量失败时回退到完整课程 ZIP；
- 已存在且摘要相同的 PDF 会直接复用；
- 远端不再声明的文件由 APK 根据远端与本地文件集合差异删除；
- 下载、摘要、路径、JSON、场景、PDF 或原子切换任一步失败，都保留旧课程。

课程包不能携带可执行代码。新增课程通常只需要组合现有内容块、场景和声明式图元；确实需要新算法时才更新 APK。
