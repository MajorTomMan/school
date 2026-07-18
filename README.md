# School

一个只服务个人学习过程的 Android 原生 App。它不是网课平台，也不是简单的 PDF 阅读器；目标是把课程、知识和教材组织成可以真正走完的个人学习路径：

```text
课程定位 → 动态讲解 → 自适应练习 → 错因诊断 → 到期复习
```

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
- Google Play 服务按需 ML Kit 中文文字识别
- `HttpURLConnection` + OpenAI-compatible JSON API
- JUnit 4 单元测试
- compileSdk / targetSdk 36，minSdk 26

## 本地运行

使用支持 AGP 9.2 的 Android Studio 打开仓库并运行 `app`。也可以在已安装 Gradle 9.4.1 与 Android SDK 36 的环境中执行：

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```


## CI/CD 与自动 APK

每次 Pull Request 都会自动运行单元测试并编译 Debug APK。每次代码进入 `master` 且测试、编译成功后，工作流还会生成稳定签名 APK、SHA-256 校验文件、Actions 构建产物和 `dev-latest` 滚动预发布版本。
