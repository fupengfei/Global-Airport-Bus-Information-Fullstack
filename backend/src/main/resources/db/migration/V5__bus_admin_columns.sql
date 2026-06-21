-- V5__bus_admin_columns.sql  巴士维护:乐观锁版本号 + 人工核对时间
ALTER TABLE bus_route
  ADD COLUMN version          INT          NOT NULL DEFAULT 0  COMMENT '乐观锁版本号/历史版本号,内容变化时+1',
  ADD COLUMN last_verified_at DATETIME     NULL                COMMENT '人工核对无误时间(与内容变更正交)',
  ADD COLUMN last_verified_by VARCHAR(64)  NULL                COMMENT '核对人';
