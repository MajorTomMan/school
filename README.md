# School

一个只服务个人学习过程的 Android 原生 App。它不是网课平台，也不是简单的 PDF 阅读器；目标是把教材变成可以真正走完的学习路径：

```text
教材定位 → 直觉讲解 → 分层提示 → 独立练习 → 错因诊断 → 到期复习
```

## 当前版本：0.2.0

首期范围仍然刻意压小到七年级数学上册第一章「有理数」，但原型已经从纯展示推进到可保存、可连接 AI 的本地学习闭环：

- 今日学习、课程路径、学习页、基础练习、复习和设置
- 练习次数、正确率、最近答案、反馈和掌握状态持久保存
- OpenAI-compatible 客户端，可连接局域网 llama.cpp
- `/v1/models` 连接测试
- AI 结构化批改与错误类型反馈
- 本地检查作为离线兜底
- 明暗主题和 Android CI 自动构建 APK

教材正文和练习目前仍使用示例数据；真实 PDF 教材不会塞进 APK，而会通过独立教材资源包导入。

## llama.cpp 配置

在 App 的「设置」中填写：

```text
接口地址：http://电脑局域网地址:7777/v1
模型名称：与 /v1/models 返回的 id 一致
API Key：局域网服务未启用鉴权时留空
```

手机和电脑需要处于可互访的网络中。测试版为了局域网 llama.cpp 允许 HTTP 明文请求，因此不要把未鉴权接口直接暴露到公网。

## 技术栈

- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- JDK 17
- Kotlin built-in support + Compose compiler 2.3.10
- Jetpack Compose + Material 3
- Preferences DataStore
- `HttpURLConnection` + OpenAI-compatible JSON API
- compileSdk / targetSdk 36，minSdk 26

## 本地运行

使用支持 AGP 9.2 的 Android Studio 打开仓库并运行 `app`。也可以在已安装 Gradle 9.4.1 与 Android SDK 36 的环境中执行：

```bash
gradle :app:assembleDebug
```

仓库暂未提交 Gradle Wrapper 二进制文件，CI 会显式安装 Gradle 9.4.1，并上传 debug APK。

## 接下来

1. 用 Room 建立正式的作答、错题和复习记录表。
2. 定义教材资源包导入协议，并打开真实 PDF 对应页。
3. 从真实教材中导出「有理数」章节、知识点和练习。
4. 加入 SM-2 风格复习调度。
5. 加入数学公式渲染、手写和拍题。

架构约束见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)，实施顺序见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。
