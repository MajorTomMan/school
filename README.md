# School

一个只服务个人学习过程的 Android 原生 App。它不是网课平台，也不是简单的 PDF 阅读器；目标是把教材变成可以真正走完的学习路径：

```text
教材定位 → 动态讲解 → 自适应练习 → 错因诊断 → 到期复习
```

## 当前版本：0.15.0

当前版本已经建立从 PDF 直导、本地 OCR、动态课程到数学题库的完整基础闭环：

- 黑、白、红、蓝、黄五色极简场景式 UI
- 小学、初中、高中、大学四级教育阶段
- 多学科、多年级、学期教材独立管理
- 文件选择器只允许选择 PDF，不要求用户制作教材压缩包
- 自动校验 PDF 文件头、页数与可读性
- 文件名、封面、版权页和目录 OCR 联合识别教材身份
- 自动推断教材印刷页与 PDF 索引偏移
- 自动提取目录课程；识别不足时按阶段建立可继续分析的课程区段
- WorkManager 持久后台复制、校验、目录扫描、OCR、课程分析和原题线索提取
- ML Kit 中文 OCR，本地保存文字、行位置和页面坐标
- OpenAI-compatible 客户端，可连接局域网 llama.cpp 生成课程解释和动画参数
- 数学题库提供教材同步、薄弱强化、错题重练和综合练习
- 参数化题目支持正负数、数轴、相反数、绝对值、大小比较、整式和方程
- 支持选择、数值、表达式、排序、数轴点选和分步解题
- 精确分数、表达式等价和一元一次方程确定性判题
- Room 保存作答、错误类型、知识点掌握度、错题和复习计划
- Android CI 自动测试、构建 APK，并更新 `dev-latest` 预发布版本

## 导入教材 PDF

在 App 中进入：

```text
学习阶段 → 学科 → 年级或学年 → 册次或学期 → 选择 PDF
```

导入时会依次执行：

```text
复制 PDF
→ 校验文件头与页数
→ OCR 封面和目录
→ 检查学科、年级与册次
→ 推断印刷页偏移
→ 生成目录、课程和页面索引
→ 后台分析正文与题目线索
```

所选文件必须是真实 PDF。仅修改扩展名不会通过 `%PDF-` 文件头和 `PdfRenderer` 可读性校验。识别到明确的学科、年级或册次冲突时，导入会停止，避免覆盖错误教材。

导入成功后，课程与题库都可以返回教材对应印刷页核对原文。PDF 和生成结果保存在 App 私有目录中，不会进入 APK。

仓库仍保留 [`docs/MATERIAL_PACK_V1.md`](docs/MATERIAL_PACK_V1.md) 与教材包构建脚本，供开发和离线预处理使用；普通 App 导入入口只接受 PDF。

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
- Android Storage Access Framework、`PdfRenderer`
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
