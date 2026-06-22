-- V9__correction_report.sql  匿名数据纠错上报(零登录;管理员后台处理)
CREATE TABLE correction_report (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  related_source_id VARCHAR(64)  NULL                                COMMENT '关联线路业务键source_id(可空;填了由服务端校验存在)',
  description       TEXT         NOT NULL                            COMMENT '问题描述(旅客填,必填)',
  contact           VARCHAR(128) NULL                                COMMENT '联系方式(可选,邮箱/电话;本期不外发)',
  status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN'             COMMENT '状态:OPEN/RESOLVED/DISMISSED',
  resolution_note   TEXT         NULL                                COMMENT '管理员内部处理备注',
  reporter_ip       VARCHAR(64)  NULL                                COMMENT '上报来源IP(限流/审计;admin可见)',
  created_by        VARCHAR(64)  NULL                                COMMENT '创建人(匿名为anonymous)',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by        VARCHAR(64)  NULL                                COMMENT '更新人(管理员用户名)',
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted           TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_corr_status (status, deleted),
  KEY idx_corr_source (related_source_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名数据纠错上报';
