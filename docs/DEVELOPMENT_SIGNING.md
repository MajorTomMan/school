# School 开发版 APK 固定签名

## 问题背景

Android 只允许使用同一应用 ID 且同一签名证书的 APK 覆盖安装。

原 CI 将 `~/.android/debug.keystore` 放入 GitHub Actions 缓存，并在缓存不存在时生成新密钥。Actions 缓存按 Git 引用隔离：拉取请求运行创建的缓存属于 `refs/pull/<number>/merge`，不能被主分支或其他拉取请求恢复。因此不同 PR、主分支和本地构建可能使用不同的 debug 密钥。

这会导致：

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE
Package signatures do not match previously installed version
```

## 修复方案

项目现在使用一把公开、固定、仅供开发版和预览版使用的签名密钥。

源文件：

```text
signing/school-development.jks.b64
```

证书 SHA-256：

```text
7b816cf2873e5d45320015a80dacbf3e9d303f0513e174d8ddf0e69ef1c421b2
```

Gradle 配置时会将 Base64 源恢复到：

```text
build/signing/school-development.jks
```

`debug` 构建类型显式使用该签名配置，不再依赖 `$HOME/.android/debug.keystore` 或 Actions 缓存。

## CI 强制检查

CI 在构建前：

1. 解码固定密钥源。
2. 使用 `keytool` 读取证书 SHA-256。
3. 与 `signing/school-development.cert.sha256` 比较。

CI 在构建后：

1. 使用 Android Build Tools 的 `apksigner verify --print-certs` 读取 APK 证书指纹。
2. 再次与固定指纹比较。
3. 不一致时立即终止构建。

每个构建产物包含：

```text
school-debug.apk
school-debug.apk.sha256
school-debug.apk.cert-sha256
```

其中：

- `apk.sha256` 验证 APK 文件是否完全相同。
- `apk.cert-sha256` 验证 APK 是否使用约定的开发证书。

## 一次性迁移

此前的 APK 已经分别使用多把随机 debug 密钥签名，无法通过新密钥直接覆盖。旧私钥只存在于隔离的 Actions 缓存中，也无法从 APK 证书反推出私钥。

首次迁移到固定开发签名时，需要卸载旧版一次：

```bash
adb uninstall com.majortomman.school
adb install school-debug.apk
```

卸载会删除应用私有数据。需要保留的学习数据，应在卸载前使用应用已有的导出或备份能力保存。

完成这次迁移后，下列来源的开发 APK 将使用同一签名：

- GitHub 主分支滚动开发 Release
- GitHub Actions PR 构建产物
- 对话中转交的 GitHub Actions 原始 APK
- 从仓库源码进行的本地 `assembleDebug` 构建

## 安全边界

这把密钥随公开仓库分发，任何人都能使用，因此只能用于公开开发版和预览版。

正式发布到应用商店或面向真实生产用户时，必须：

- 使用私有生产密钥或商店托管签名；
- 不复用此开发密钥；
- 使用独立发布流程和权限控制；
- 妥善保存生产签名密钥和签名谱系。
