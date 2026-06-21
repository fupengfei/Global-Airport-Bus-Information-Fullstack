-- V6__audit_log.sql  管理端写操作审计(只增写;含审计列+逻辑删除)
CREATE TABLE audit_log (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  actor_id    BIGINT       NOT NULL                            COMMENT '操作人(app_user.id)',
  actor_type  VARCHAR(16)  NOT NULL                            COMMENT '操作人类型,如 ADMIN',
  action      VARCHAR(32)  NOT NULL                            COMMENT '动作:CREATE_BUS/UPDATE_BUS/DELETE_BUS/VERIFY_BUS/ROLLBACK_BUS',
  target_type VARCHAR(16)  NOT NULL                            COMMENT '对象类型,如 bus',
  target_id   VARCHAR(64)  NULL                                COMMENT '对象业务键,如 source_id',
  summary     VARCHAR(512) NULL                                COMMENT '变更摘要/说明',
  ip          VARCHAR(45)  NULL                                COMMENT '操作来源 IP',
  created_by  VARCHAR(64)  NULL                                COMMENT '创建人',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by  VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_audit_created (created_at),
  KEY idx_audit_actor (actor_id),
  KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作审计';
