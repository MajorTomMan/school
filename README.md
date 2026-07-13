# School

一个只服务个人学习过程的 Android 原生 App。它不是网课平台，也不是简单的 PDF 阅读器；目标是把教材变成可以真正走完的学习路径：

```text
教材定位 → 动态讲解 → 自适应练习 → 错因诊断 → 到期复习
```

## 当前版本：0.14.0

当前版本已经建立从教材导入、本地 OCR、动态课程到数学题库的完整基础闭环：

- 黑、白、红、蓝、黄五色极简场景式 UI
- 多学科、多年级、上下册教材独立管理
- WorkManager 持久后台导入、OCR、课程分析和原题线索提取
- ML Kit 中文 OCR，本地保存文字、行位置和页面坐标
- OpenAI-compatible 客户端，可连接局域网 llama.cpp 生成课程解释和动画参数
- 数学题库提供教材同步、薄弱强化、错题重练和综合练习
- 参数化题目支持正负数、数轴、相反数、绝对值、大小比较、整式和方程
- 支持选择、数值、表达式、排序、数轴点选和分步解题
- 精确分数、表达式等价和一元一次方程确定性判题
- Room 保存作答、错误类型、知识点掌握度、错题和复习计划
- 自动根据薄弱程度、提示使用、连续表现和到期时间安排下一题
- 教材 OCR 中的例题、练习和习题会生成带页码来源的教材变式线索
- Android CI 自动测试、构建 APK，并更新 `dev-latest` 预发布版本

教材 PDF 不进入 APK，而是通过独立教材包安装到 App 私有目录。资源包规范见 [`docs/MATERIAL_PACK_V1.md`](docs/MATERIAL_PACK_V1.md)。

## 构建教材包

```bash
python scripts/build_material_pack.py \
  --pdf "/path/to/数学七年级上册.pdf" \
  --catalog app/src/main/assets/catalog/math-grade7-volume1.json \
  --output math-grade7-volume1.school.zip \
  --pack-id math-grade7-volume1 \
  --version 1.0.0 \
  --title "七年级数学上册" \
  --subject "数学" \
  --page-index-offset 0
```

生成后在 App 中进入：

```text
学科 → 数学 → 年级与册次 → 导入教科书
```

导入成功后，课程与题库都可以返回教材对应印刷页核对原文。

## llama.cpp 配置

在 App 的「设置」中填写：

```text
接口地址：http://电脑局域网地址:7777/v1
模型名称：与 /v1/models 返回的 id 一致
API Key：局域网服务未启用鉴权时留空
```

手机和电脑需要处于可互访的网络中。测试版为了局域网 llama.cpp 允许 HTTP 明文请求，因此不要把未鉴权接口直接暴露到公网。数学题库的基础判题不依赖模型，即使模型不可用仍能练习和记录错题。

## 技术栈

- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- JDK 17
- Kotlin built-in support + Compose compiler 2.3.10
- Jetpack Compose + Compose Animation + Canvas
- Room 2.8.4 + KSP 2.3.10
- Preferences DataStore
- WorkManager
- Android Storage Access Framework、`ZipInputStream`、`PdfRenderer`
- ML Kit 中文文字识别
- `HttpURLConnection` + OpenAI-compatible JSON API
- JUnit 4 单元测试
- compileSdk / targetSdk 36，minSdk 26

## 本地运行

使用支持 AGP 9.2 的 Android Studio 打开仓库并运行 `app`。也可以在已安装 Gradle 9.4.1 与 Android SDK 36 的环境中执行：

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

仓库暂未提交 Gradle Wrapper 二进制文件，CI 会显式安装 Gradle 9.4.1。

## CI/CD 与自动 APK

每次 Pull Request 都会自动运行单元测试并编译 Debug APK。每次代码进入 `master` 且测试、编译成功后，工作流还会：

1. 生成 `school-debug.apk` 和 SHA-256 校验文件。
2. 上传为 GitHub Actions 构建产物。
3. 更新名为 `dev-latest` 的滚动预发布 Release。
