-- V7__bus_route_version.sql  线路版本快照(只增写;含审计列+逻辑删除)
CREATE TABLE bus_route_version (
  id              BIGINT      PRIMARY KEY AUTO_INCREMENT         COMMENT '主键',
  bus_route_id    BIGINT      NOT NULL                           COMMENT '线路内部ID',
  version         INT         NOT NULL                           COMMENT '版本号(对应 bus_route.version)',
  snapshot_json   LONGTEXT    NOT NULL                           COMMENT '整条线路含全子表的完整编辑DTO JSON(忠实,非canonical归一版)',
  content_hash    CHAR(64)    NOT NULL                           COMMENT '该版本内容哈希',
  changed_summary VARCHAR(1024) NULL                             COMMENT '相对上一版本的字段级diff摘要JSON',
  actor           VARCHAR(64) NULL                               COMMENT '产生该版本的操作人',
  created_by      VARCHAR(64) NULL                               COMMENT '创建人',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by      VARCHAR(64) NULL                               COMMENT '更新人',
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted         TINYINT(1)  NOT NULL DEFAULT 0                 COMMENT '逻辑删除',
  UNIQUE KEY uk_brv (bus_route_id, version, deleted),
  KEY idx_brv_bus (bus_route_id),
  CONSTRAINT fk_brv_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线路版本快照';
