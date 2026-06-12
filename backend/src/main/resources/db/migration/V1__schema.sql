-- V1__schema.sql
-- 数据层级:country -> city -> airport -> bus_route -> 子表(stop/schedule/image/file/alert)
-- 字符集:utf8mb4 全表统一
-- 审计列:每张表末尾追加 created_by/created_at/updated_by/updated_at/deleted
-- 逻辑删除:deleted TINYINT(1) NOT NULL DEFAULT 0,唯一键并入 deleted

CREATE TABLE country (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  code         VARCHAR(8)   NOT NULL,
  name         VARCHAR(128) NOT NULL,
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_country_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE city (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  country_id   BIGINT       NOT NULL,
  name         VARCHAR(128) NOT NULL,
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_city_country_name (country_id, name, deleted),
  CONSTRAINT fk_city_country FOREIGN KEY (country_id) REFERENCES country(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE airport (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  city_id      BIGINT       NOT NULL,
  code         VARCHAR(8)   NOT NULL,
  name         VARCHAR(128) NOT NULL,
  official_url VARCHAR(512) NULL,
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_airport_code (code, deleted),
  KEY idx_airport_city (city_id),
  CONSTRAINT fk_airport_city FOREIGN KEY (city_id) REFERENCES city(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_route (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  airport_id      BIGINT       NOT NULL,
  source_id       VARCHAR(64)  NOT NULL,
  route           VARCHAR(128) NOT NULL,
  destination     VARCHAR(255) NULL,
  operator        VARCHAR(255) NULL,
  official_url    VARCHAR(512) NULL,
  duration        VARCHAR(255) NULL,
  price           VARCHAR(255) NULL,
  operating_hours VARCHAR(255) NULL,
  last_updated    DATE         NULL,
  fetch_failed    TINYINT(1)   NOT NULL DEFAULT 0,
  content_hash    CHAR(64)     NOT NULL,
  created_by      VARCHAR(64)  NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by      VARCHAR(64)  NULL,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_bus_source_id (source_id, deleted),
  KEY idx_bus_airport (airport_id),
  CONSTRAINT fk_bus_airport FOREIGN KEY (airport_id) REFERENCES airport(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_stop (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  seq          INT          NOT NULL,
  name         VARCHAR(255) NOT NULL,
  direction    VARCHAR(16)  NOT NULL DEFAULT 'TO_AIRPORT',
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_stop_bus (bus_route_id),
  CONSTRAINT fk_stop_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_schedule (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  bus_route_id  BIGINT       NOT NULL,
  direction     VARCHAR(16)  NOT NULL DEFAULT 'TO_AIRPORT',
  time_range    VARCHAR(255) NULL,
  interval_text VARCHAR(255) NULL,
  note          VARCHAR(512) NULL,
  created_by    VARCHAR(64)  NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by    VARCHAR(64)  NULL,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_sched_bus (bus_route_id),
  CONSTRAINT fk_sched_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_image (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  url          VARCHAR(512) NOT NULL,
  caption      VARCHAR(255) NULL,
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_img_bus (bus_route_id),
  CONSTRAINT fk_img_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_file (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  name         VARCHAR(255) NULL,
  url          VARCHAR(512) NOT NULL,
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_file_bus (bus_route_id),
  CONSTRAINT fk_file_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_alert (
  id           BIGINT        PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT        NOT NULL,
  type         VARCHAR(32)   NOT NULL,
  message      VARCHAR(1024) NOT NULL,
  start_date   DATE          NULL,
  end_date     DATE          NULL,
  created_by   VARCHAR(64)   NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)   NULL,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)    NOT NULL DEFAULT 0,
  KEY idx_alert_bus (bus_route_id),
  CONSTRAINT fk_alert_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
