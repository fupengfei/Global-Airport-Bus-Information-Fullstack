-- V4__favorite.sql  收藏(=订阅);唯一键不含 deleted,靠翻转 deleted 切换收藏态;字段带列注释
CREATE TABLE favorite (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT             COMMENT '主键',
  user_id       BIGINT      NOT NULL                              COMMENT '收藏人(app_user.id)',
  bus_route_id  BIGINT      NOT NULL                              COMMENT '被收藏的巴士线路内部ID(非source_id)',
  created_by    VARCHAR(64) NULL                                  COMMENT '创建人',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  updated_by    VARCHAR(64) NULL                                  COMMENT '更新人',
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT(1)  NOT NULL DEFAULT 0                    COMMENT '逻辑删除/收藏态:0=已收藏,1=已取消',
  UNIQUE KEY uk_fav_user_bus (user_id, bus_route_id),
  KEY idx_fav_user (user_id),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_fav_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏(=订阅)巴士线路';
