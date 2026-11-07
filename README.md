# ColorOS GMS 修复合集 (cn.sqmydor.cgpf)

把两个 LSPosed 模块合成一个 APK，并迁移到 **libxposed API 102**（现代 Xposed API）：

| 功能 | 来源 | 作用域 | 说明 |
|---|---|---|---|
| GMS 连通性探测修复 | [rkbkosp/cn.rkbkosp.colorosgmsprobefix](https://github.com/rkbkosp/cn.rkbkosp.colorosgmsprobefix)（MIT） | `com.oplus.battery`、`com.oplus.athena` | Hook `NetworkDetector.a(Context,int)`，经 `127.0.0.1:<端口>` HTTP 代理请求 generate_204，只有真实 204 才判定成功；代理就绪后 10 秒起每 30 秒复查，通过原控制器 `R(0L,1)` 排队重检 |
| RKP 自动续签 | 新增 | `system`（系统框架）、`com.android.rkpdapp` | 代理就绪时（之后在线时每天一次）由 system_server 向 rkpd 的 BootReceiver 发送显式 BOOT_COMPLETED，rkpd 重新排队 PeriodicProvisioner：清理过期密钥并向 Google 申请新密钥；可选把 rkpd 访问 googleapis.com 的请求转到本地代理 |
| 隐藏国行 GMS 特征 | 思路来自 [EX3124/UnlockCNGMS](https://github.com/EX3124/UnlockCNGMS)（重写实现） | `com.google.android.gms`、`com.android.vending`（可在 LSPosed 自行添加） | `ApplicationPackageManager.hasSystemFeature(String[,int])` 对 `cn.google.services`、`com.google.android.feature.services_updater` 返回 false，并从 `getSystemAvailableFeatures()` 中过滤 |

## 设置
打开模块（桌面图标或 LSPosed 里的模块设置）：两个功能各有开关，代理端口 1–65535，默认 **7890**。
设置保存在框架的 Remote Preferences（`getRemotePreferences("config")`），被 Hook 进程实时读取；如未生效，重启对应作用域应用。

## 要求
- 支持 libxposed **API 102** 的框架（module.prop: `minApiVersion=102`，旧版 LSPosed / API 82-100 不会加载本模块）
- Android 10+（minSdk 29）

## 构建
JDK 21、Android SDK platform 37 / build-tools 37.0.0：`./gradlew assembleRelease`。
GitHub Actions 见 `.github/workflows/build.yml`（可选通过 secrets 配置签名，否则用 debug 签名）。

## 许可
- 探测修复部分移植自 ColorOS GMS Probe Fix，Copyright (c) 2026 rkbkosp，MIT，见 `LICENSE-ColorOSGmsProbeFix`。
- UnlockCNGMS 仓库未声明许可证；本项目只复用了其公开思路（两个特征名 + 三个方法），代码为重新编写。
