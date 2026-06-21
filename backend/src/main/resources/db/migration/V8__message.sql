-- V8__message.sql  站内信(模板+参数,前端按locale渲染);version去重键幂等
CREATE TABLE message (
  id                   BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  user_id              BIGINT       NOT NULL                            COMMENT '收信人(app_user.id)',
  template_code        VARCHAR(32)  NOT NULL                            COMMENT '模板码:BUS_UPDATED/BUS_OFFLINE',
  params_json          TEXT         NOT NULL                            COMMENT '渲染参数JSON(前端按locale渲染)',
  related_bus_route_id BIGINT       NULL                                COMMENT '关联线路内部ID(系统消息可空)',
  dedup_key            VARCHAR(128) NOT NULL                            COMMENT '幂等去重键:bus:{id}:v:{version} 或 bus:{id}:offline',
  is_read              TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '已读',
  read_at              DATETIME     NULL                                COMMENT '已读时间',
  created_by           VARCHAR(64)  NULL                                COMMENT '创建人',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by           VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted              TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  UNIQUE KEY uk_msg_dedup (user_id, dedup_key, deleted),
  KEY idx_msg_user_read (user_id, is_read, deleted),
  CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';
