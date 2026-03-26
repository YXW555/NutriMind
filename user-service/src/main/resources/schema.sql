-- MySQL 数据库表结构（参考）
-- 1. 用户表：存储用户基础信息、登录凭证、角色等
CREATE TABLE IF NOT EXISTS `user_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号（可用于手机号/邮箱/用户名）',
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `email` VARCHAR(128) DEFAULT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `role` VARCHAR(32) DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
  `status` TINYINT DEFAULT 1 COMMENT '0=禁用,1=正常',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号表';

CREATE TABLE IF NOT EXISTS `user_profile` (
  `user_id` BIGINT NOT NULL COMMENT '关联 user_account.id',
  `gender` VARCHAR(16) DEFAULT NULL COMMENT 'MALE/FEMALE/OTHER',
  `birth_date` DATE DEFAULT NULL COMMENT '出生日期',
  `height_cm` DECIMAL(5,2) DEFAULT NULL COMMENT '身高(cm)',
  `activity_level` VARCHAR(32) DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH',
  `dietary_preference` VARCHAR(64) DEFAULT NULL COMMENT '饮食偏好',
  `allergies` VARCHAR(255) DEFAULT NULL COMMENT '过敏原',
  `medical_notes` VARCHAR(500) DEFAULT NULL COMMENT '健康备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户健康档案';

CREATE TABLE IF NOT EXISTS `health_goal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '关联 user_account.id',
  `goal_type` VARCHAR(32) DEFAULT 'BALANCE' COMMENT 'FAT_LOSS/MUSCLE_GAIN/MAINTAIN/BALANCE',
  `target_calories` DECIMAL(8,2) DEFAULT NULL COMMENT '目标热量',
  `target_protein` DECIMAL(8,2) DEFAULT NULL COMMENT '目标蛋白质(g)',
  `target_fat` DECIMAL(8,2) DEFAULT NULL COMMENT '目标脂肪(g)',
  `target_carbohydrate` DECIMAL(8,2) DEFAULT NULL COMMENT '目标碳水(g)',
  `target_weight_kg` DECIMAL(6,2) DEFAULT NULL COMMENT '目标体重(kg)',
  `weekly_change_kg` DECIMAL(5,2) DEFAULT NULL COMMENT '每周期望变化(kg)',
  `start_date` DATE DEFAULT NULL COMMENT '目标开始日期',
  `end_date` DATE DEFAULT NULL COMMENT '目标结束日期',
  `note` VARCHAR(500) DEFAULT NULL COMMENT '目标备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goal_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户健康目标';

CREATE TABLE IF NOT EXISTS `weight_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '关联 user_account.id',
  `weight_kg` DECIMAL(6,2) NOT NULL COMMENT '体重(kg)',
  `record_date` DATE NOT NULL COMMENT '记录日期',
  `note` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weight_user_date` (`user_id`,`record_date`),
  KEY `idx_weight_user_time` (`user_id`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体重记录';
