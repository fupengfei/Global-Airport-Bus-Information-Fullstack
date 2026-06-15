-- V2__airport_search_stat.sql
-- 机场搜索热度:只增统计表(与 audit_log 分开,只计数不记录用户/IP)。
-- Redis 为加速、MySQL 为权威;@Scheduled 周期把 Redis 增量按天 upsert 累加进本表。
-- 同样带全套审计列 + 逻辑删除;唯一键并入 deleted 以支持按天 upsert 累加。

CREATE TABLE airport_search_stat (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  airport_id   BIGINT       NOT NULL,
  day          DATE         NOT NULL,
  cnt          BIGINT       NOT NULL DEFAULT 0,
  created_by   VARCHAR(64)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   VARCHAR(64)  NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_stat_airport_day (airport_id, day, deleted),
  KEY idx_stat_airport (airport_id),
  CONSTRAINT fk_stat_airport FOREIGN KEY (airport_id) REFERENCES airport(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
