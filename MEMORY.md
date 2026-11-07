# AID Studio 项目记忆

更新于 2026-11-07。生产部署使用 SSH 别名 `ai-hk`，与 ArcReel、模型网关共用主机和 HTTPS 入口。

## 生产环境

正式域名分工如下。核心 C 端使用 https://thdrhdf.site/ ，运营后台使用 https://thdrhdf.fun/admin/ ，备用原版 Web 使用 https://thdrhdf.fun/web/ 。直接访问 .fun 根路径进入运营后台。两个域名的 A 记录均指向 43.154.247.11，原 IP 入口保留兼容。后端监听 127.0.0.1:18480，管理端另有本地维护入口 18489。管理维护凭据在服务器 /etc/aid-studio/admin-login，仅 root 可读。历史 /etc/aid-studio/creator-login 对应的测试账号已失效。

- frontend/creator 根路径静态导出到 current/creator-public，frontend/web 以 /web 为 basePath 导出到 current/web-original（Nginx 回环 18481）。frontend/admin 以 /admin/ 构建到 current/admin-public，请求 /admin/prod-api/；回环管理端使用独立根路径构建 current/admin。
- 运行服务为 aid-studio、aid-studio-mysql、aid-studio-redis；配置与凭据在 /etc/aid-studio，应用数据在 /var/lib/aid-studio，数据库与缓存数据在 /var/lib/aid-studio-db。独立 JRE 和数据库程序位于 /opt/aid-studio。
- 三端统一入口配置 deploy/nginx/ai-hk-frontends.conf 对应服务器 /etc/aid-studio/public-path.conf，由 HTTPS 站点引用。三端切换前配置备份 /etc/aid-studio/before-three-ka6wHHfj，之前管理端产物 /opt/aid-studio/releases/three-2q8h9j2Y/previous-admin。Nginx 主配置引用 /etc/nginx/snippets/aid-studio.conf 和 /etc/aid-studio/http-redirect.conf。日后全量发布网关或重写 HTTPS 配置时须保留这些 include 和现役证书配置。部署前 Nginx 配置副本在 /etc/aid-studio。
- 生产环境使用本地存储。已移除种子数据中指向缺失图片的交流二维码。模型生成及短信、邮件、微信登录待配置。

### 域名与证书

Nginx 主配置在 http 上下文引用 `/etc/aid-studio/domains.conf`（仓库 `deploy/nginx/ai-hk-domains.conf`）。运营路由共用 `/etc/aid-studio/operations-paths.conf`（仓库 `deploy/nginx/ai-hk-operations.conf`）。原 IP 继续引用 `/etc/aid-studio/public-path.conf`。切换前备份位于服务器 `/etc/aid-studio/before-domains.mXVvTBKW`。

两个域名共用 Certbot 证书 `aid-studio-domains`，HTTP-01 验证目录为 `/var/www/letsencrypt`。现役 `openclaw-cert-renew.timer` 定期运行 Certbot renew，证书更新后 reload Nginx。浏览器登录态按域名分别保存。

## 下次发布

在本机仓库中提交要上线的改动后，执行 `./deploy/ai-hk.sh`。脚本通过 SSH 别名 `ai-hk` 发布当前 Git 提交，构建使用 JDK 17、Maven、Node.js 22.13+ 和 npm。`./deploy/ai-hk.sh --check` 用于本机构建、服务器只读预检和现役公网入口检查。

发布时脚本校验上传包，原子切换后端和三个前端，重启 AID 并检查 API、用户端、管理端和一张风格图。服务器验收失败会恢复原程序并再次启动服务。发布期间 API 会短暂中断，建议避开正在生成的任务。旧程序路径会打印在终端。数据库、上传素材和共享 Nginx 均独立维护；SQL 内容变化时，脚本会在切换前停止，需先安排相应迁移。

## 素材恢复与验收

初始化 SQL 提供素材记录，媒体文件需要单独安装。缺文件时，部分图片请求返回 HTTP 200 和包含业务码 404 的 JSON，页面表现为风格卡片和资产预览空白。

本次从官方发布包和官方 CDN 恢复媒体；79 个历史路径按同一数据库记录映射到正式主图。迁移时一并备份数据库与上传目录，恢复时保留已有用户文件。2026-11-06 从公网核对了线上库引用的全部 676 个媒体地址，并在浏览器中验证图片解码、音色试听和分镜播放。

发布后使用普通创作者账号走通“我的作品 → 新建电影 → 项目配置 → 剧本创作 → 流程画布”。在新建作品和资产库中核对真实预览，检查响应类型和文件内容。首页及 FAQ 的可选 SEO 元数据未登记时会回退到内置标题。

[部署与验收记录](tasks/todo.md) · [协作与排障经验](tasks/lessons.md)

- AID Studio 的 C 端定制只修改 frontend/creator（公网根路径）。frontend/web（公网 /web/）保留为作者原版，仅在同步作者代码时更新。

## Creator 首页结构（2026-11-07）

根路径及16语言首页使用 CreatorLanding 产品介绍页：视频首屏、24个视频（6个精选片段及18个公开模型演示）、工作流、运镜预览、4个音色试听、真实计费和8条FAQ。原 CreatorDashboard 输入与TXT上传入口移至 /start/，继续复用原登录续接；该路径允许移动浏览并保留语言与方向。首页 projects=1 项目弹层仍保留。样片为数秒片段，不代表完整剧集；运镜演示使用已恢复的真实素材。

2026-11-07 视频库扩充：新增18个不同场景的Pyramid Flow公开模型演示，本地960px H.264按需播放，封面WebP。utils/sampleLibrary.ts供首页、gallery与case共用；原6精选仍用于创作输入与指南。新增媒体遵循原站CC BY-SA4.0，来源与许可随public/creator-home/library/SOURCES.json和LICENSE.txt发布，播放窗口显示署名与改编说明。不能将这些外部模型示例描述为本站用户生成的作品。

2026-11-07 可读性巡检：公共壳通过html.creator-public-shell隔离浅色菜单、账户和确认浮层；资产表单使用creator-asset-dialog独立portal类；样片使用creator-showcase-modal。/create只对create-main__flow-stack直属toolbar应用浅色顶栏，避免污染深色剧本工具栏。AntD6部分组件DOM已变化，检查实际节点（如checkbox根span、modal-container），不能只按旧class写覆盖。

注册入口：creator使用紧凑的登录/创建账户方式选择弹窗，手机与邮箱分别选择并固定验证码渠道。2026-11-07线上短信、邮箱、微信开关均false，仅已有账号密码登录可用；Google登录尚未开发。不得把UI准备描述为注册服务已开通。配置说明见docs/registration-channels.md，待提供Google OAuth、SMTP/短信服务配置后接入与真实送达验收。
