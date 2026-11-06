# AID Studio 项目记忆

更新于 2026-11-06。生产部署使用 SSH 别名 `ai-hk`，与 ArcReel、模型网关共用主机和 HTTPS 入口。

## 生产环境

用户端为 https://43.154.247.11/aid-studio/ 。HTTP 同路径会跳转至 HTTPS。独立运行的 AID 应用只监听 127.0.0.1:18480。管理页面通过 SSH 转发 18489 后访问。两个账号的凭据分别在服务器 /etc/aid-studio/creator-login 和 /etc/aid-studio/admin-login，仅 root 可读。

- 构建用户端时在 frontend/web 下执行 NEXT_PUBLIC_BASE_PATH=/aid-studio npm run generate，部署 dist/public 到 /opt/aid-studio/current/web-prefix。管理端静态目录为 /opt/aid-studio/current/admin，后端为 /opt/aid-studio/current/aid-admin.jar。
- 运行服务为 aid-studio、aid-studio-mysql、aid-studio-redis；配置与凭据在 /etc/aid-studio，应用数据在 /var/lib/aid-studio，数据库与缓存数据在 /var/lib/aid-studio-db。独立 JRE 和数据库程序位于 /opt/aid-studio。
- 当前 HTTPS 站点引用 /etc/aid-studio/public-path.conf。Nginx 主配置引用 /etc/nginx/snippets/aid-studio.conf 和 /etc/aid-studio/http-redirect.conf。日后全量发布网关或重写 HTTPS 配置时须保留这些 include 和现役证书配置。部署前 Nginx 配置副本在 /etc/aid-studio。
- 生产环境使用本地存储。已移除种子数据中指向缺失图片的交流二维码。模型生成及短信、邮件、微信登录待配置。

## 素材恢复与验收

初始化 SQL 提供素材记录，媒体文件需要单独安装。缺文件时，部分图片请求返回 HTTP 200 和包含业务码 404 的 JSON，页面表现为风格卡片和资产预览空白。

本次从官方发布包和官方 CDN 恢复媒体；79 个历史路径按同一数据库记录映射到正式主图。迁移时一并备份数据库与上传目录，恢复时保留已有用户文件。2026-11-06 从公网核对了线上库引用的全部 676 个媒体地址，并在浏览器中验证图片解码、音色试听和分镜播放。

发布后使用普通创作者账号走通“我的作品 → 新建电影 → 项目配置 → 剧本创作 → 流程画布”。在新建作品和资产库中核对真实预览，检查响应类型和文件内容。首页及 FAQ 的可选 SEO 元数据未登记时会回退到内置标题。

[部署与验收记录](tasks/todo.md) · [协作与排障经验](tasks/lessons.md)
