-- MySQL schema for user-service

CREATE TABLE IF NOT EXISTS `user_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL COMMENT 'login identifier',
  `password` VARCHAR(255) NOT NULL COMMENT 'bcrypt password hash',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT 'display name',
  `email` VARCHAR(128) DEFAULT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `role` VARCHAR(32) DEFAULT 'USER' COMMENT 'USER or ADMIN',
  `status` TINYINT DEFAULT 1 COMMENT '0 disabled, 1 active',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user account table';

CREATE TABLE IF NOT EXISTS `user_profile` (
  `user_id` BIGINT NOT NULL COMMENT 'references user_account.id',
  `gender` VARCHAR(16) DEFAULT NULL COMMENT 'MALE or FEMALE or OTHER',
  `birth_date` DATE DEFAULT NULL COMMENT 'birth date',
  `height_cm` DECIMAL(5,2) DEFAULT NULL COMMENT 'height in cm',
  `activity_level` VARCHAR(32) DEFAULT NULL COMMENT 'LOW or MEDIUM or HIGH',
  `dietary_preference` VARCHAR(64) DEFAULT NULL COMMENT 'diet preference',
  `allergies` VARCHAR(255) DEFAULT NULL COMMENT 'allergies',
  `medical_notes` VARCHAR(500) DEFAULT NULL COMMENT 'medical notes',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user health profile';

CREATE TABLE IF NOT EXISTS `health_goal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT 'references user_account.id',
  `goal_type` VARCHAR(32) DEFAULT 'BALANCE' COMMENT 'FAT_LOSS or MUSCLE_GAIN or MAINTAIN or BALANCE',
  `target_calories` DECIMAL(8,2) DEFAULT NULL COMMENT 'target calories',
  `target_protein` DECIMAL(8,2) DEFAULT NULL COMMENT 'target protein in g',
  `target_fat` DECIMAL(8,2) DEFAULT NULL COMMENT 'target fat in g',
  `target_carbohydrate` DECIMAL(8,2) DEFAULT NULL COMMENT 'target carbohydrate in g',
  `target_weight_kg` DECIMAL(6,2) DEFAULT NULL COMMENT 'target weight in kg',
  `weekly_change_kg` DECIMAL(5,2) DEFAULT NULL COMMENT 'expected weekly change in kg',
  `start_date` DATE DEFAULT NULL COMMENT 'goal start date',
  `end_date` DATE DEFAULT NULL COMMENT 'goal end date',
  `note` VARCHAR(500) DEFAULT NULL COMMENT 'goal notes',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goal_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user health goal';

CREATE TABLE IF NOT EXISTS `weight_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT 'references user_account.id',
  `weight_kg` DECIMAL(6,2) NOT NULL COMMENT 'weight in kg',
  `record_date` DATE NOT NULL COMMENT 'record date',
  `note` VARCHAR(255) DEFAULT NULL COMMENT 'note',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weight_user_date` (`user_id`, `record_date`),
  KEY `idx_weight_user_time` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='weight log';
