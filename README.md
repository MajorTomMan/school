# School

一个只服务个人学习过程的 Android 原生 App。它不是网课平台，也不是简单的 PDF 阅读器；目标是把课程、知识和教材组织成可以真正走完的个人学习路径：

```text
课程定位 → 动态讲解 → 自适应练习 → 错因诊断 → 到期复习
```

## 当前版本：0.19.0

当前版本完成了从“教材驱动”到“课程树 + 知识图 + 资源绑定”的底层升级：

- 学科定义与教材解耦，内置十八个常见学科并允许动态扩展
- 课程体系使用任意深度父子树，支持年级、学期、课程、单元、章节、课和主题
- 知识点独立于课程目录，支持多个前置知识、相关、组成、等价和扩展关系
- 教材 PDF、预制课程、题库、音视频和文档统一作为可绑定资源
- 掌握度改为绑定学科知识点，切换教材或课程路径后仍然保留
- 旧 `TextbookSlot`、学习状态、数学尝试和错题继续兼容并自动迁移
- 课程路径页显示真实父子结构、知识点、教材绑定状态和当前前置知识
- 首次教材导入不再强制观看多页教程
- 黑、白、红、蓝、黄五色极简场景式 UI
- 小学、初中、高中、大学四级教育阶段
- 内置教材 PDF 浏览器，一次授权目录后可直接搜索全部子目录
- 自动校验 PDF 文件头、页数与可读性
- 文件名、封面、版权页和目录 OCR 联合识别未知教材身份
- 自动推断教材印刷页与 PDF 索引偏移
- 十一册人教版数学教材预制知识包，覆盖二千零五十一页和二百八十个知识单元
- 匹配教材优先按 SHA-256 指纹加载预制目录、页码和原创动态课程
- 中文 OCR 使用 Google Play 服务按需模型，不再把完整识别模型打包进 APK
- 数学题库提供教材同步、薄弱强化、错题重练和综合练习
- Room 保存课程树、知识图、资源绑定、作答、错题、掌握度和复习计划
- Android CI 自动测试、构建 APK，并更新 `dev-latest` 预发布版本

## 课程与知识架构

```text
SubjectDefinition 学科
    ↓
Curriculum 课程体系
    ↓
CurriculumNode 课程目录树
    ↕
KnowledgePoint 知识点
    ↕
KnowledgeRelation 知识图

LearningResource 教材或其他资源
    ↓
ResourceBinding 节点/知识点绑定

KnowledgeMastery 用户掌握度
```

核心原则：

- 学科是定义，不等于某一本教材。
- 课程目录是树，知识依赖是图。
- PDF 是可选资源，没有原书时预制课程仍可学习。
- 掌握度属于知识点，不属于教材。
- 尝试和错题仍保存教材来源，方便返回原页和生成同源变式。

完整设计、Room 表结构和兼容边界见 [`docs/CURRICULUM_ARCHITECTURE.md`](docs/CURRICULUM_ARCHITECTURE.md)。

## 内置学科目录

当前内置：

```text
语文、数学、英语、日语、科学、物理、化学、生物、历史、地理、思想政治
计算机、编程、经济学、法学、音乐、美术、体育与健康
```

每个学科由可组合能力描述，例如数学包含数值、表达式、分步判定和图形能力；英语包含词汇、语法、听力和发音能力。后续新增学科不需要修改课程树、资源绑定或掌握度表。

## 数学预制知识包

当前预制范围：

```text
初中数学：七年级上册、七年级下册、八年级上册、八年级下册、九年级上册、九年级下册
高中数学 A 版：必修第一册、必修第二册、选择性必修第一册、第二册、第三册
```

预制知识包不包含教材 PDF、教材图片或大段教材正文，只包含 PDF 指纹、教材身份、页码范围以及 School 原创讲解、动态步骤、误区和练习模板。

导入匹配 PDF 后，应用会将 PDF 绑定到已有课程节点；未绑定 PDF 时课程、题库、错题和复习仍然可用。

## 导入教材 PDF

在 App 中进入：

```text
学习阶段 → 学科 → 年级或学年 → 册次或学期 → 教材文件
```

首次可以直接授权 `Documents/教材` 等目录，不再强制进入教程。之后 App 会递归扫描目录中的 PDF，并将与当前教材槽位高度匹配的文件排在前面。

导入流程：

```text
复制 PDF
→ 校验文件头、页数和 SHA-256
→ 查找预制知识包
→ 匹配成功：绑定预制课程，不启用 OCR
→ 未匹配：按需下载中文 OCR 模型，识别封面目录并执行本地知识编译
→ 建立课程树节点、知识点、页面索引和题目线索
```

## 可选 OCR 模型

中文 OCR 使用 Google Play 服务按需模型：

- 安装 App 和使用预制数学课程时不会下载 OCR 模型。
- 只有未知教材确实需要扫描时才会触发下载。
- 首次 OCR 需要设备联网，并可能需要等待 Google Play 服务完成模型安装。
- 没有 Google Play 服务的设备仍可使用预制课程、题库、错题和复习。

## 数据迁移

Room 数据库从版本 2 升级到版本 3 时：

- 保留旧课程状态、数学作答和错题。
- 将不同教材下的同名数学知识掌握度按知识点聚合。
- 现有教材和二百八十个数学课程自动重建为稳定课程树。
- 旧 `GeneratedLesson.id` 保存为 `legacyLessonId`，已有学习记录继续命中。
- 旧 `TextbookSlot` 暂时作为导入、文件目录和 WorkManager 兼容键。

## APK 更新与签名

GitHub Actions 会缓存固定的开发调试证书。安装一次由 `master` 生成的 0.18.x 或 0.19.0 APK 后，后续主分支 APK 在证书缓存仍有效时可以直接覆盖安装：

```bash
adb install -r school-debug.apk
```

从此前随机签名的 0.17.0 或更早版本迁移时，可能仍需要最后卸载一次。之后应持续使用主分支 `dev-latest` APK 更新。

## llama.cpp 配置

在 App 的「设置」中填写：

```text
接口地址：http://电脑局域网地址:7777/v1
模型名称：与 /v1/models 返回的 id 一致
API Key：局域网服务未启用鉴权时留空
```

数学预制课程与基础题库不依赖模型；模型不可用时仍可学习、练习和记录错题。

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

仓库暂未提交 Gradle Wrapper 二进制文件，CI 会显式安装 Gradle 9.4.1。

## CI/CD 与自动 APK

每次 Pull Request 都会自动运行单元测试并编译 Debug APK。每次代码进入 `master` 且测试、编译成功后，工作流还会生成稳定签名 APK、SHA-256 校验文件、Actions 构建产物和 `dev-latest` 滚动预发布版本。
