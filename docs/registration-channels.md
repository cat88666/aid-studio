# 注册渠道启用与 Google 接入准备

核验时间：2026-11-07。代码接入默认关闭；本文件不代表真实 Google 授权或短信/邮件投递已验收。

## 当前状态

线上 `POST https://thdrhdf.site/aid/auth/public-config` 返回：`smsEnabled=false`、`emailEnabled=false`、`wechatEnabled=false`。这说明三个现有第三方/验证码渠道均关闭；不能把新注册界面视为短信或邮件投递已经可用。账号密码登录仍是独立入口。

手机号和邮箱均已有“验证码验证后自动注册，已有账号直接登录”的后端实现，分别在 `SmsLoginStrategy` 和 `EmailLoginStrategy`。手机号目前只接受中国大陆 11 位号码，正则为 `^1[3-9]\\d{9}$`，并非国际号码服务。Google 接入代码已准备，需完成下面的 OAuth 配置和真实授权验收后才能启用。

## 启用现有邮箱与短信

进入 [运营后台](https://thdrhdf.fun/admin/)，打开“全局业务配置”，选择“邮箱配置”或“短信配置”。对应前端为 `frontend/admin/src/views/aidconfig/aidconfig/index.tsx`，字段说明与渠道选项在同目录 `maps.ts`，数据存储于 `aid_config`。

邮箱分类 `mail`：

| 字段 | 用途 |
| --- | --- |
| `enabled` | 启用邮箱服务 |
| `host` / `port` | SMTP 主机和端口，当前实现默认 465，启用 SSL |
| `from` | 发件人地址 |
| `user` / `pass` | SMTP 账号与授权码/应用密码 |
| `code_length` / `code_expire_minutes` | 验证码长度与有效期 |
| `send_interval_seconds` / `daily_limit` | 同目标、同 IP 的发送间隔与每日上限 |

短信分类 `sms`：

| 字段 | 用途 |
| --- | --- |
| `enabled` / `providerType` | 启用开关与 `aliyun`、`tencent`、`smsbao` 渠道 |
| `accessKeyId` / `accessKeySecret` | 阿里云或腾讯云凭据 |
| `endpoint` / `signName` | 云短信端点、已审核短信签名 |
| `defaultTemplateId` / `codeParamName` | 已审核验证码模板及参数名 |
| `sdkAppId` | 腾讯云短信应用 ID |
| `smsBaoUsername` / `smsBaoApiKey` | 短信宝账号与 API Key |
| `smsBaoProductId` / `smsBaoContentTemplate` | 短信宝产品与含签名模板 |
| `code_length` / `code_expire_minutes` / `send_interval_seconds` / `daily_limit` | 验证码和限流策略 |

填写对应供应商字段后，先保存，再点击“同步配置”使进程内配置生效。公开配置另有最长约 30 秒缓存。后台有 SMTP 连通性测试和短信测试入口；连通成功不等于邮件进入收件箱，短信供应商受理也不等于实际送达。

启用后应使用获授权的测试邮箱/手机号完整验证：实际收到验证码、错误码被拒绝、首次注册、重复登录回到同一账号、验证码消费、发送限流。不得把真实凭据写进 Git、截图或日志。

现有接口：`POST /aid/auth/sendCode`，参数 `codeType=sms|email`、`target`、`scene=login`，可选 `inviteCode`；随后 `POST /aid/auth/login`，参数 `loginType=sms|email`、`account`、`code`。接口带现有行为验证码校验；Redis 中按场景隔离验证码和 TTL，并对发送目标/IP 限流。现有注册事务关联邀请和注册送积分，成功提交后消费验证码，应保留这些行为。

## Google：默认关闭的接入

采用 Google Identity Services 官方按钮，popup 返回 ID token 后提交本站，不申请 Drive、Gmail 等额外权限，不使用 Client Secret，也不提供自动 One Tap 登录。[官方按钮指南](https://developers.google.com/identity/gsi/web/guides/display-button)

配置模板在 `deploy/google-login.env.example`。本服务器由 systemd 读取 `/etc/aid-studio/app.env`，以后启用时填入：

```dotenv
GOOGLE_LOGIN_ENABLED=false
GOOGLE_CLIENT_ID=你的Web客户端ID.apps.googleusercontent.com
GOOGLE_LOGIN_ORIGIN=https://thdrhdf.site
```

准备完成前保持 false。正式启用前，至少先开通并验证一个邮箱/短信验证码服务，保证 Google 专用账号可以绑定联系方式、设置密码和自助注销；本版本没有 Google 近期重认证注销流程。Google 注册不默认发放注册奖励，不能套用邮箱渠道的赠送配置。

在 Google Auth Platform 创建 Web application OAuth 客户端并配置品牌、支持邮箱、真实首页和隐私政策，授权 JavaScript origin 填 `https://thdrhdf.site`（不带路径），授权域为 `thdrhdf.site`。本实现只支持 HTTPS 同源，开发验收应使用 HTTPS 测试域名和对应独立客户端。不要把运营后台域名自动加入授权。本实现采用 JS popup，无需填写虚构的 redirect callback URL。[官方客户端设置](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid)

代码接口：

- `POST /aid/auth/public-config` 的 `login.googleEnabled` 和 `login.googleClientId` 控制入口；关闭时不展示按钮、不加载 Google 脚本。
- `POST /aid/auth/google/challenge` 返回 `nonce`、`expiresIn`，设置 HttpOnly/Secure/SameSite 浏览器绑定 cookie；nonce 只保留短时有效期。
- `POST /aid/auth/login` 提交 `{loginType: "google", credential, googleNonce, inviteCode?}`，复用现有 token、会话和登录回跳。

服务端校验来源、浏览器绑定、nonce、签名、audience、issuer 和有效期，nonce 一次性消费。身份使用 Google `sub` 并复用 social 唯一索引；不会无条件按邮箱合并。遇到已有邮箱账号应使用原有方式登录，本版不提供已有账号的 Google 绑定入口。非 Google 权威邮箱不应作为本站的已验证恢复邮箱。参考 [官方服务端验证指南](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)。Google Gemini 模型 API key 不能用作 Google 登录配置。

启用时：备份 app.env，填好客户端和域名并设为 true，重启 `aid-studio`；通过 public-config 核对开关后，使用授权测试账号完成首次注册、重复登录、取消授权、邮箱冲突、登录回跳、绑定联系方式和注销入口验收。OAuth 应用在测试模式时只允许已加入的测试用户；需要公开使用时按 Google 控制台要求完成发布。失败时先恢复 `GOOGLE_LOGIN_ENABLED=false` 并重启。

当前没有真实 OAuth 客户端、短信或 SMTP 配置；自动测试不能代替 Google 真机授权和验证码实际送达验收。
