-- V10__ticket.sql  用户建议工单 + 气泡线程回复(状态机 OPEN→REPLIED→CLOSED)
CREATE TABLE ticket (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  user_id           BIGINT       NOT NULL                            COMMENT '提单人(app_user.id)',
  related_source_id VARCHAR(64)  NULL                                COMMENT '关联线路业务键source_id(可空;填了由服务端校验存在)',
  status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN'             COMMENT '状态:OPEN/REPLIED/CLOSED',
  last_reply_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '最后一条回复时间(列表排序)',
  created_by        VARCHAR(64)  NULL                                COMMENT '创建人(user:{id})',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by        VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted           TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_ticket_user (user_id, deleted),
  KEY idx_ticket_status (status, deleted),
  CONSTRAINT fk_ticket_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户建议工单';

CREATE TABLE ticket_reply (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  ticket_id   BIGINT       NOT NULL                            COMMENT '所属工单(ticket.id)',
  author_type VARCHAR(8)   NOT NULL                            COMMENT '作者类型:USER/ADMIN(服务端从认证主体取,E10)',
  author_id   BIGINT       NOT NULL                            COMMENT '作者ID(app_user.id 或管理员user.id)',
  body        TEXT         NOT NULL                            COMMENT '回复正文(纯文本)',
  created_by  VARCHAR(64)  NULL                                COMMENT '创建人(user:{id}/admin:{id})',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by  VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_reply_ticket (ticket_id, deleted, id),
  CONSTRAINT fk_reply_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单回复(气泡线程)';
