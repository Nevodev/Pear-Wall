# 自动编译（GitHub Actions）

工作流：[`.github/workflows/build.yml`](../.github/workflows/build.yml)

- 触发：推送到 `master`（仓库默认分支）、推送 `v*` tag（会额外发布 Release）、任意 Pull Request、以及在 Actions 页面手动 `Run workflow`
- 产物：`pearwall-release-apk`（`app/build/outputs/apk/release/*.apk`，保留 14 天，文件名为 `pear-wall-<版本号>.apk`）；tag 构建还会把同一个 APK 挂到对应的 GitHub Release 上
- 校验：对比 keystore 与 APK 的签名证书 SHA-256，确保产物不是被 debug 签名顶替的
- 环境：Ubuntu + JDK 21 + Android SDK（平台与 build-tools 由 AGP 自动下载）+ NDK `28.2.13676358` + Rust `aarch64-linux-android` + `cargo-ndk`

## 一次性配置：把签名放进仓库密钥

工程本身已经支持从环境变量读签名（`app/build.gradle.kts` 里的 `MOMENTO_SIGNING_*`），所以 CI 不需要改代码，只要在 **Settings → Secrets and variables → Actions** 里加四个密钥：

| 密钥 | 内容 |
| --- | --- |
| `MOMENTO_SIGNING_KEYSTORE_BASE64` | 你的 keystore 文件（`.jks` / `.keystore`）的 Base64 文本 |
| `MOMENTO_SIGNING_STORE_PASSWORD` | keystore 密码 |
| `MOMENTO_SIGNING_KEY_ALIAS` | 密钥别名 |
| `MOMENTO_SIGNING_KEY_PASSWORD` | 该别名对应的密码 |

生成 Base64（换行不能丢，所以不要用带自动折行的工具）：

```bash
# macOS / Linux
base64 -i release.jks | tr -d '\n' > keystore.base64
```

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.jks')) | Set-Content keystore.base64 -NoNewline
```

把 `keystore.base64` 的全部内容粘进 `MOMENTO_SIGNING_KEYSTORE_BASE64`。

> `MOMENTO_SIGNING_STORE_FILE` 不需要配成密钥：工作流会把 keystore 解码到 `app/release.jks` 并直接把绝对路径传给 Gradle。本地开发时你仍然可以让它在 `gradle.properties`（该文件已在 `.gitignore` 里）或环境变量里指向自己的 keystore。

## 版本号怎么来的

没有配置文件，全部从 Git 推导（`app/build.gradle.kts`），tag 名同时决定版本名和版本码的基数，再叠加提交总数保证严格递增。工作流里的 `checkout` 因此用了 `fetch-depth: 0` 拉全量历史和 tag：

| 场景 | versionName | versionCode |
| --- | --- | --- |
| 正好在 `v1.1` tag 上，全仓库 37 个提交 | `1.1` | `10100 + 37 = 10137` |
| 该 tag 之后又有 1 个提交 | `1.1-dev.1` | `10100 + 38 = 10138` |
| 在 `v1.2.3` tag 上 | `1.2.3` | `10203 + 提交数` |
| 一个 tag 都没有 | `1.0-dev.<距 tag 提交数>` | `10000 + 提交数` |
| Git 不可用（例如导出的源码包） | `1.0` | `1001` |

- **发新版本 = 打 tag**：`git tag v1.3 && git push origin v1.3`。不需要改任何文件。tag 必须是 `v<数字>` 开头（`v1.1`、`v1.2.3` 都行），否则 `git describe` 会忽略它。
- **版本码单调递增**：`major * 10000 + minor * 100 + patch` 作为基数，加上提交总数，所以每个提交的包版本码都更大，tag 也不会倒退回比开发包更小的版本码。
- **想手动指定**（比如商店要求特定版本码）：设 `VERSION_CODE` / `VERSION_NAME` 环境变量或 Gradle 属性即可覆盖，优先级高于 Git 推导，工作流不需要改。
- 本地开发时版本名带 `-dev.<提交数>` 后缀，方便一眼区分开发包和正式包；不喜欢的话在 `gradle.properties` 里设 `VERSION_NAME` 覆盖。

## 发布 1.1 的完整流程

```bash
git tag v1.1              # 在要发布的那个提交上打 tag（默认就是当前 HEAD）
git push origin v1.1      # 推送 tag，工作流自动开始
```

推完之后：Actions 里会出现一次由 tag 触发的构建 → 编译并用你的 keystore 签名 → **校验签名证书确实来自该 keystore**（对不上就直接失败，不会发布）→ 把 APK 重命名为 `pear-wall-<版本号>.apk` → 创建 `v1.1` Release 并挂上 APK。

Release 说明是**中英双语**的：中文段在前、英文段在后，各自带下载入口和完整改动对比链接，末尾再由 GitHub 自动附上按提交生成的 changelog。想加自己的更新说明，在 Release 页面直接编辑即可。

同一个 tag 重复推送不会重新触发；如果某次构建失败，把那个 tag 删掉重推（`git push origin :v1.1` 后再推），或在 Actions 页面点 **Re-run all jobs**（重跑时 Release 里的 APK 会被覆盖）。

## 注意

- **SDK 平台和 build-tools 由 AGP 自己下载**：工作流只钉 `platform-tools` 和 NDK，`compileSdk` 需要的平台（`compileSdk 37` → `platforms;android-37.0`，注意平台包名带次版本号）与 build-tools 会在构建时自动拉取，所以升级 `compileSdk` 不需要动工作流。唯一要同步的是 NDK：workflow 顶部的 `NDK_VERSION` 必须和 `app/build.gradle.kts` 里的 `classicNdkVersion` 一致。
- **Fork 的 PR 会失败**：GitHub 不会把密钥传给来自 fork 的 PR，所以“校验签名密钥”这一步会明确报错并停在编译前，不会产出未签名的包。想给 fork PR 也跑 CI，就再拆一个只用 `assembleDebug`、不依赖密钥的 job。
- **首次构建较慢**：Gradle 缓存和 Cargo 缓存都是冷启动，Rust 交叉编译和 NDK 下载会占掉大部分时间，第二次起会快很多。
- 失败时先看 “Build release APK” 这一步的日志（已经带 `--stacktrace`）；常见的三类原因是密钥名拼错、NDK 版本与 `app/build.gradle.kts` 里的 `classicNdkVersion` 不一致、以及 `libs.versions.toml` 里新加的依赖在 CI 上拉不到。
