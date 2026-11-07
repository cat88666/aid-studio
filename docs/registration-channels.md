# 注册渠道启用与 Google 接入准备

核验时间：2026-11-07。本文件区分现有能力和待实现方案，不代表 Google 已上线。

## 当前状态

线上 `POST https://thdrhdf.site/aid/auth/public-config` 返回：`smsEnabled=false`、`emailEnabled=false`、`wechatEnabled=false`。这说明三个现有第三方/验证码渠道均关闭；不能把新注册界面视为短信或邮件投递已经可用。账号密码登录仍是独立入口。

手机号和邮箱均已有“验证码验证后自动注册，已有账号直接登录”的后端实现，分别在 `SmsLoginStrategy` 和 `EmailLoginStrategy`。手机号目前只接受中国大陆 11 位号码，正则为 `^1[3-9]\\d{9}$`，并非国际号码服务。Google 登录策略、配置项、公开配置字段和凭证接收接口当前都未实现。

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

## Google：建议的最小实现（尚未开发）

建议使用 Google Identity Services 的官方“Sign in with Google”按钮，采用 JavaScript popup 回调接收 ID token，再向本站后端提交；不需要为了网站登录请求 Drive、Gmail 等 API 权限。按钮可按当前语言渲染。One Tap 可以后续另行评估，不是第一版必须项。[Google 官方按钮指南](https://developers.google.com/identity/gsi/web/guides/display-button)

所需应用配置：在 Google Auth Platform 创建 **Web application** 类型 OAuth 客户端，设置品牌名称、支持邮箱、首页与隐私政策。授权 JavaScript origin 应为 `https://thdrhdf.site`，不带路径；本地开发另加实际使用的 localhost origin。授权域填写 `thdrhdf.site`。`thdrhdf.fun` 当前为运营后台和原版 Web，不应自动加入消费者登录授权范围。若以后采用 redirect 模式，必须等服务端接收路径真正实现后，再登记完全一致的 redirect URI；当前没有可填写的已实现 Google 回调地址。[Google 官方设置指南](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid)

代码接入建议：

1. 新增受配置控制的 Google 凭证接收端点或登录策略，公开配置只提供启用状态和 Web client ID。client ID 是公开标识；任何服务端 secret 都不得下发。前端只在服务真实配置且后端可用时提供可用入口。
2. Java 后端使用官方 `GoogleIdTokenVerifier` 或成熟 JWT 库，校验 Google 签名、`aud`、`iss`、`exp`；处理登录 CSRF，并验证与本次浏览器流程绑定的 nonce，拒绝无效或重放凭证。若选用 HTML POST 接收模式，按官方说明验证 `g_csrf_token` 双提交 cookie；不可直接把此检查套用到没有相同 cookie 的自定义 JS 回调。
3. 身份键使用 `sub`，可复用 `aid_user_social`，设置 `platform_source=google`、`openid=sub`，现有 `(platform_source, openid)` 唯一索引可防重复身份绑定。不要用可变化的邮箱当 Google 身份主键，不存完整 ID token 到 `raw_data` 或日志。
4. 遇到同邮箱已有账号，不应无条件自动合并。Google 对外部邮箱的权威性有条件限制；最保守的第一版是要求先用已有方式登录后绑定。官方推荐验证既有账号的访问权。核对账号停用、删除、注销冷静期，并在事务内创建普通用户和绑定身份，保留邀请码、积分幂等和多端会话策略。
5. 为非法签名、错误 audience、过期 token、nonce 不匹配、重复注册竞争、停用账号、邮箱冲突和配置关闭增加后端测试。部署后完成真实 Google 授权、首次注册、重复登录和取消授权流程，再宣称可用。

验证与身份关联要求依据 [Google 服务端 ID token 验证指南](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)。现有 Google Gemini 模型服务 API key 与 Google 登录 OAuth client 无关，不能复用。
