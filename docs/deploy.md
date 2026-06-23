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

## 四、域名 + HTTPS(宿主 nginx 反代 + certbot)

前置:DNS 加一条 A 记录把域名指向服务器公网 IP(`dig +short 你的域名` 能解析出 IP),
并在**云安全组放行 80、443**(在云控制台操作,不在服务器里)。

```bash
# 1. 装 nginx(走系统源)
sudo apt update && sudo apt install -y nginx

# 2. 放反代配置(deploy/nginx.conf 里的 server_name 先改成你的域名)
sudo cp deploy/nginx.conf /etc/nginx/conf.d/airportbus.conf
sudo rm -f /etc/nginx/sites-enabled/default      # 关掉 apt 默认欢迎页,避免抢 80
sudo nginx -t && sudo systemctl reload nginx
# 此时 http://你的域名 应可访问(已放行安全组 80)

# 3. 上 HTTPS(自动改 nginx 配置:加 443、装证书、80→443 跳转、自动续期)
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 你的域名
```

完成后 `https://你的域名` 即是前台。宿主 nginx 把 80/443 → `127.0.0.1:8081`;
前端容器内那层 nginx 再把 `/api/` 转给后端,本层只无脑转发。

> 偏好 Caddy(自动 HTTPS,无需 certbot)也行:用 [Caddyfile](../Caddyfile),
> 安装见 https://caddyserver.com/docs/install(Debian/Ubuntu 走官方 apt 源)。
>
> 排错:`curl -I http://127.0.0.1:8081` 通但公网不通 = 安全组没放行 80/443;
> certbot 报 `Timeout during connect` = 安全组没放行 80 或 DNS 没生效。

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
