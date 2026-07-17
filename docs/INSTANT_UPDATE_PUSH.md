# GitHub APK 发布后的即时更新提醒

## 目标

School 的开发通道使用 GitHub `dev-latest` Release 分发 APK。启用本方案后，主分支构建会按以下顺序执行：

```text
构建并测试 APK
→ 生成递增 versionCode
→ 生成 update-manifest.json
→ RSA-SHA256 签名更新清单
→ 上传 APK、哈希、清单和签名到 dev-latest
→ 通过 FCM HTTP v1 向 school_dev_update 主题发送数据消息
→ 设备收到消息后重新下载并验证正式清单
→ 前台弹出升级窗口，后台显示新版本通知
```

FCM 消息只负责提示“可能有新版本”，不作为可信更新数据。App 始终重新下载 GitHub Release 中的更新清单并验证：

- 清单 RSA 签名；
- APK 文件大小；
- APK SHA-256；
- 应用包名；
- `versionCode`；
- APK 固定签名证书。

## 未配置时的行为

Firebase 配置全部为空时：

- Android 构建和 CI 仍然成功；
- 即时推送自动关闭；
- App 继续在回到前台时检查；
- WorkManager 继续每二十四小时兜底检查；
- 设置页显示“即时发布提醒：待配置”。

因此可以先合并代码，再补 Firebase 凭据，不会阻断 GitHub Release。

## 一、创建 Firebase Android 应用

在 Firebase 控制台创建或选择项目，并添加 Android 应用：

```text
包名：com.majortomman.school
```

记录以下字段：

```text
Firebase Project ID
Firebase Android App ID
Web API Key
Project Number / Sender ID
```

本工程不提交 `google-services.json`。GitHub Actions 在构建时把这些值注入 Android 资源和 `BuildConfig`。

## 二、配置 GitHub Repository Variables

进入：

```text
GitHub 仓库
→ Settings
→ Secrets and variables
→ Actions
→ Variables
```

创建：

```text
SCHOOL_FIREBASE_PROJECT_ID
SCHOOL_FIREBASE_APPLICATION_ID
SCHOOL_FIREBASE_API_KEY
SCHOOL_FIREBASE_SENDER_ID
SCHOOL_FIREBASE_UPDATE_TOPIC
```

其中主题可使用：

```text
school_dev_update
```

`SCHOOL_FIREBASE_UPDATE_TOPIC` 不设置时也会使用这个默认值。

只有前四项同时存在时，APK 内的即时推送功能才会启用。

## 三、配置 FCM 服务账号

为 GitHub Actions 创建仅用于发送 FCM 消息的服务账号，并授予发送 Cloud Messaging 消息所需的最小权限。

下载 JSON 密钥后，在本地生成单行 Base64：

Linux：

```bash
base64 -w 0 firebase-service-account.json
```

macOS：

```bash
base64 < firebase-service-account.json | tr -d '\n'
```

PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("firebase-service-account.json"))
```

进入 GitHub：

```text
Settings
→ Secrets and variables
→ Actions
→ Secrets
```

创建 Secret：

```text
SCHOOL_FIREBASE_SERVICE_ACCOUNT_JSON_B64
```

不要把服务账号 JSON 或 Base64 内容提交到仓库。

## 四、更新清单签名密钥

开发通道在未配置 Secret 时使用仓库内公开开发密钥，便于测试。更稳妥的部署方式是在 GitHub Secret 中设置：

```text
SCHOOL_UPDATE_PRIVATE_KEY_B64
```

它必须与 App 内置的更新清单验证公钥匹配。正式发布通道必须改用独立私有密钥，不能继续使用公开开发密钥。

## 五、App 端行为

### 前台

```text
收到 school_update 数据消息
→ 检查消息中的 versionCode 是否可能更新
→ 提交一次唯一 WorkManager 检查任务
→ 下载并验签 update-manifest.json
→ 发现新版本
→ 直接显示升级弹窗
```

### 后台

```text
收到消息
→ WorkManager 下载并验签清单
→ 显示“School 新版本已发布”系统通知
→ 点击通知打开 App 和升级弹窗
```

### 消息丢失

设备离线、Google Play 服务受限或推送消息被删除时，App 仍依赖：

- 冷启动与回前台检查；
- 每二十四小时检查；
- 设置页手动检查。

## 六、验证配置

安装启用 Firebase 变量构建出的 APK 后：

```bash
adb logcat -s SchoolUpdatePush FirebaseMessaging
```

预期看到：

```text
subscribed to update topic
```

设置页应显示：

```text
即时发布提醒：已启用 · school_dev_update
```

随后向 `master` 推送一个会通过 CI 的提交。流水线应依次完成：

```text
Publish rolling development release
Notify installed apps through FCM
```

FCM 步骤输出应包含：

```text
FCM update trigger sent: projects/.../messages/...
```

设备前台时应出现升级弹窗；设备后台时应出现系统通知。

## 七、安全边界

- 推送中的下载地址、版本名称和修改说明均不直接采用；
- App 只信任通过内置公钥验证的 GitHub 更新清单；
- APK 必须匹配当前包名和固定签名；
- 普通 Android App 无法静默安装，最终仍需要用户确认系统安装页面；
- 自动检查关闭时会取消主题订阅，并忽略收到的更新推送；
- 无 Google Play 服务的设备不能依赖 FCM，只能使用轮询兜底或未来接入其他推送通道。
