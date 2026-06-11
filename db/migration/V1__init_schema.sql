-- V1__init_schema.sql
-- 全球机场巴士信息 —— 首版数据库 schema
-- 设计来源: docs/design.md(经多 agent 对抗评审修订)
-- 数据库: MySQL 8 / InnoDB / utf8mb4
-- 自然键原则: 保留 data.json 的业务键(bus.source_id / country.code / airport.code)
--            并加唯一索引,使种子导入器可幂等 upsert。
-- 落地后请移动到 backend/src/main/resources/db/migration/ 由 Flyway 接管。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========== 巴士数据(读多写少) ==========

CREATE TABLE country (
  id    BIGINT       NOT NULL AUTO_INCREMENT,
  code  VARCHAR(8)   NOT NULL COMMENT 'ISO 国家码,自然键',
  name  VARCHAR(128) NOT NULL COMMENT '国家名(中文)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_country_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE city (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  country_id BIGINT       NOT NULL,
  name       VARCHAR(128) NOT NULL COMMENT 'data.json 中 city 只有 name',
  PRIMARY KEY (id),
  UNIQUE KEY uk_city_country_name (country_id, name),
  CONSTRAINT fk_city_country FOREIGN KEY (country_id) REFERENCES country (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE airport (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  city_id      BIGINT       NOT NULL,
  code         VARCHAR(8)   NOT NULL COMMENT 'IATA 机场码,全局唯一自然键',
  name         VARCHAR(128) NOT NULL,
  official_url VARCHAR(512) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_airport_code (code),
  KEY idx_airport_city (city_id),
  CONSTRAINT fk_airport_city FOREIGN KEY (city_id) REFERENCES city (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  source_id       VARCHAR(64)  NOT NULL COMMENT 'data.json 业务键, 如 "vie-vab1", 导入幂等键',
  airport_id      BIGINT       NOT NULL,
  route           VARCHAR(128) NULL,
  destination     VARCHAR(255) NULL,
  operator        VARCHAR(255) NULL,
  official_url    VARCHAR(512) NULL,
  duration        VARCHAR(512) NULL COMMENT '展示文本, 不做结构化查询',
  price           VARCHAR(512) NULL COMMENT '展示文本, 带方向/币种/中外文混排',
  operating_hours VARCHAR(512) NULL COMMENT '展示文本',
  last_updated    DATE         NULL COMMENT '日期粒度, 无时间部分',
  fetch_failed    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '无损承载 data.json',
  content_hash    CHAR(64)     NULL COMMENT 'SHA256(规范化 JSON, 含子表), 变更检测',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bus_source (source_id),
  KEY idx_bus_airport (airport_id),
  CONSTRAINT fk_bus_airport FOREIGN KEY (airport_id) REFERENCES airport (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_stop (
  id     BIGINT       NOT NULL AUTO_INCREMENT,
  bus_id BIGINT       NOT NULL,
  seq    INT          NOT NULL COMMENT '停靠顺序, 导入按数组下标从 1 赋值',
  name   VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_bus_seq (bus_id, seq),
  CONSTRAINT fk_stop_bus FOREIGN KEY (bus_id) REFERENCES bus (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_schedule (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  bus_id        BIGINT       NOT NULL,
  time_range    VARCHAR(255) NULL,
  interval_text VARCHAR(255) NULL COMMENT 'interval 是 MySQL 保留字, 故改名',
  note          VARCHAR(512) NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_bus (bus_id),
  CONSTRAINT fk_schedule_bus FOREIGN KEY (bus_id) REFERENCES bus (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_image (
  id      BIGINT       NOT NULL AUTO_INCREMENT,
  bus_id  BIGINT       NOT NULL,
  url     VARCHAR(512) NOT NULL,
  caption VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_image_bus (bus_id),
  CONSTRAINT fk_image_bus FOREIGN KEY (bus_id) REFERENCES bus (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_file (
  id     BIGINT       NOT NULL AUTO_INCREMENT,
  bus_id BIGINT       NOT NULL,
  name   VARCHAR(255) NOT NULL,
  url    VARCHAR(512) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_file_bus (bus_id),
  CONSTRAINT fk_file_bus FOREIGN KEY (bus_id) REFERENCES bus (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_alert (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  bus_id     BIGINT       NOT NULL,
  type       VARCHAR(32)  NULL COMMENT 'info / reroute ...',
  message    VARCHAR(1024) NULL,
  start_date DATE         NULL COMMENT '允许 NULL: 长期提醒无起止',
  end_date   DATE         NULL,
  PRIMARY KEY (id),
  KEY idx_alert_bus (bus_id),
  CONSTRAINT fk_alert_bus FOREIGN KEY (bus_id) REFERENCES bus (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 用户与互动 ==========

CREATE TABLE `user` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  email         VARCHAR(255) NOT NULL,
  nickname      VARCHAR(64)  NULL,
  avatar_url    VARCHAR(512) NULL,
  locale        VARCHAR(16)  NOT NULL DEFAULT 'zh-CN',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE favorite (
  id         BIGINT     NOT NULL AUTO_INCREMENT,
  user_id    BIGINT     NOT NULL,
  bus_id     BIGINT     NOT NULL,
  notify     TINYINT(1) NOT NULL DEFAULT 1 COMMENT '收藏即订阅; 留位以备"收藏但不打扰"',
  created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fav_user_bus (user_id, bus_id),
  KEY idx_fav_bus (bus_id) COMMENT '推送时反查订阅者热路径',
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_fav_bus  FOREIGN KEY (bus_id)  REFERENCES bus (id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  type         VARCHAR(32)  NOT NULL COMMENT 'BUS_UPDATED / BUS_REMOVED / SYSTEM ...',
  title        VARCHAR(255) NULL,
  template_code VARCHAR(64) NULL COMMENT '多语言: 前端按 user.locale 渲染',
  params_json  JSON         NULL,
  ref_bus_id   BIGINT       NULL,
  ref_hash     CHAR(64)     NULL COMMENT '配合唯一键做推送幂等',
  is_read      TINYINT(1)   NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_dedup (user_id, ref_bus_id, ref_hash),
  KEY idx_msg_user_unread (user_id, is_read, created_at),
  CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ticket (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  type       VARCHAR(32)  NOT NULL COMMENT 'SUGGESTION / BUG / OTHER',
  title      VARCHAR(255) NOT NULL,
  content    TEXT         NULL,
  status     VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN -> REPLIED -> CLOSED',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ticket_user (user_id),
  KEY idx_ticket_status (status),
  CONSTRAINT fk_ticket_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ticket_reply (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  ticket_id   BIGINT      NOT NULL,
  author_type VARCHAR(8)  NOT NULL COMMENT 'USER / ADMIN',
  author_id   BIGINT      NOT NULL COMMENT '配合 author_type 指向 user 或 admin_user',
  content     TEXT        NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reply_ticket (ticket_id),
  CONSTRAINT fk_reply_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 后台与审计 ==========

CREATE TABLE admin_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  role          VARCHAR(16)  NOT NULL DEFAULT 'OPERATOR' COMMENT 'SUPER_ADMIN / OPERATOR',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  admin_id    BIGINT       NOT NULL,
  action      VARCHAR(64)  NOT NULL,
  target_type VARCHAR(32)  NULL,
  target_id   BIGINT       NULL,
  detail_json JSON         NULL,
  ip          VARCHAR(45)  NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_admin (admin_id, created_at),
  KEY idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== i18n 预留(先建表不填) ==========

CREATE TABLE i18n_text (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  entity_type VARCHAR(32)  NOT NULL,
  entity_id   BIGINT       NOT NULL,
  field       VARCHAR(64)  NOT NULL,
  locale      VARCHAR(16)  NOT NULL,
  text        TEXT         NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_i18n (entity_type, entity_id, field, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
