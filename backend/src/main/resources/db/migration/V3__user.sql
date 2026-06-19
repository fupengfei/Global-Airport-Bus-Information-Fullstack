-- V3__user.sql  用户与可撤销 refresh token;审计列 + 逻辑删除,唯一键并入 deleted
CREATE TABLE app_user (
  id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
  username       VARCHAR(32)  NOT NULL,
  email          VARCHAR(255) NOT NULL,
  password_hash  VARCHAR(100) NOT NULL,
  locale         VARCHAR(8)   NOT NULL DEFAULT 'zh-CN',
  role           VARCHAR(16)  NOT NULL DEFAULT 'USER',
  email_verified TINYINT(1)   NOT NULL DEFAULT 0,
  created_by     VARCHAR(64)  NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by     VARCHAR(64)  NULL,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_username (username, deleted),
  UNIQUE KEY uk_user_email (email, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_token (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  token_hash  CHAR(64)    NOT NULL,
  expires_at  DATETIME    NOT NULL,
  revoked     TINYINT(1)  NOT NULL DEFAULT 0,
  created_by  VARCHAR(64) NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by  VARCHAR(64) NULL,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT(1)  NOT NULL DEFAULT 0,
  UNIQUE KEY uk_rt_hash (token_hash),
  KEY idx_rt_user (user_id),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
