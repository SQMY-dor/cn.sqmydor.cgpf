# Changelog

## 1.2.0
- 设置页改用 miuix（Compose）重写，修复顶部标题显示问题，跟随系统深色模式与 Monet 取色
- 开关即时保存；端口单独保存并校验 1–65535
- 改为 GitHub Actions 编译，编译完成自动发布 Release（用仓库 Secrets 中的发布密钥签名，可覆盖安装）

## 1.1.0
- 新增 RKP 自动续签：代理就绪时立即触发 rkpd 续签，之后代理在线时每天一次
- 可选让 rkpd 访问 googleapis.com 的请求走本地代理

## 1.0.0
- 合并 ColorOS GMS Probe Fix 与 UnlockCNGMS，迁移到 libxposed API 102
- 代理端口可设置（默认 7890），两个功能分别可开关
