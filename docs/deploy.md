# 部署到 Linux VPS / 云主机

全栈已容器化:一台装了 Docker 的 Linux 服务器,`git clone` 后 `docker compose` 一键起。
生产用 [docker-compose.prod.yml](../docker-compose.prod.yml)(内部端口不暴露公网、密码走 `.env.prod`、
前端只绑回环由 Caddy 反代 + HTTPS)。

## 前置

- 一台 Linux 服务器(Ubuntu/Debian 22.04+,≥ 2GB 内存),有公网 IP
- 一个域名,A 记录指向服务器 IP(要 HTTPS 时)
- 放行安全组/防火墙的 **80、443**(SSH 22 自留;不要放行 8080/8081/3307/6380)

## 一、装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 之后重新登录,免 sudo 跑 docker
docker compose version          # 确认 compose 插件可用
```

## 二、拉代码 + 配密码

```bash
git clone https://github.com/fupengfei/Global-Airport-Bus-Information-Fullstack.git
cd Global-Airport-Bus-Information-Fullstack

cp .env.prod.example .env.prod
vi .env.prod        # 必填:MYSQL_ROOT_PASSWORD / DB_PASSWORD / ADMIN_PASSWORD(强随机串)
                    # 可选:MAIL_* 真发邮件验证码
```

> 生成强随机串:`openssl rand -base64 24`

## 三、起服务

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml ps          # 4 个服务都 healthy
docker compose -f docker-compose.prod.yml logs -f app  # 看种子导入完成
```

此时前端在 `127.0.0.1:8081`(只本机可达),后端/DB/Redis 全在容器内网,**不对公网**。

## 四、域名 + HTTPS(Caddy 反代)

```bash
curl -fsSL https://get.caddyserver.com | bash    # 或见 caddyserver.com/docs/install
vi Caddyfile                                      # 把 yourdomain.com 换成你的域名
sudo cp Caddyfile /etc/caddy/Caddyfile
sudo systemctl reload caddy                       # Caddy 自动签 Let's Encrypt 证书
```

打开 `https://yourdomain.com` 即是前台。Caddy 把 443 → `127.0.0.1:8081`,自动续证书。

> 偏好 nginx + certbot 也行:nginx 反代 `proxy_pass http://127.0.0.1:8081;`,再 `certbot --nginx -d yourdomain.com`。

## 五、首次登录后台

- 管理后台:`https://yourdomain.com/admin/login`
- 账号 = `.env.prod` 里的 `ADMIN_USERNAME`,密码 = `ADMIN_PASSWORD`

## 运维常用

```bash
# 拉最新代码后重建上线
git pull && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 看日志 / 重启单个服务
docker compose -f docker-compose.prod.yml logs -f app
docker compose -f docker-compose.prod.yml restart app

# 停 / 清(保留数据卷)
docker compose -f docker-compose.prod.yml down

# 备份数据库(mysql_data 卷 + 逻辑备份)
docker compose -f docker-compose.prod.yml exec mysql \
  sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" airportbus' > backup-$(date +%F).sql
```

## 安全清单(上线前过一遍)

- [ ] `.env.prod` 三个密码都改成强随机串(没改 `ADMIN_PASSWORD` = 后台默认弱口令)
- [ ] 防火墙只放行 80/443/22,**没有**放行 8080/8081/3307/6380
- [ ] 用的是 `docker-compose.prod.yml`(开发版 `docker-compose.yml` 会把 DB/Redis/后端暴露到宿主公网)
- [ ] `.env.prod` 不在 git 里(`git status` 应看不到它)
- [ ] 定期备份 `mysql_data` 卷
