CREATE TABLE IF NOT EXISTS `meal_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `record_date` DATE NOT NULL COMMENT 'record date',
  `total_calories` DECIMAL(8,2) DEFAULT 0 COMMENT 'total calories',
  `total_protein` DECIMAL(8,2) DEFAULT 0 COMMENT 'total protein(g)',
  `total_fat` DECIMAL(8,2) DEFAULT 0 COMMENT 'total fat(g)',
  `total_carbohydrate` DECIMAL(8,2) DEFAULT 0 COMMENT 'total carbohydrate(g)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meal_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='daily meal summary';

CREATE TABLE IF NOT EXISTS `meal_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `record_id` BIGINT NOT NULL COMMENT 'linked meal_record.id',
  `food_id` BIGINT NOT NULL COMMENT 'linked food_basics.id',
  `meal_type` VARCHAR(32) DEFAULT 'SNACK' COMMENT 'BREAKFAST/LUNCH/DINNER/SNACK',
  `quantity` DECIMAL(8,2) DEFAULT 0 COMMENT 'consumed quantity',
  `calories` DECIMAL(8,2) DEFAULT 0 COMMENT 'calories for this item',
  `protein` DECIMAL(8,2) DEFAULT 0,
  `fat` DECIMAL(8,2) DEFAULT 0,
  `carbohydrate` DECIMAL(8,2) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_food_id` (`food_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='meal details';

SET @meal_type_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'meal_detail'
    AND COLUMN_NAME = 'meal_type'
);
SET @meal_type_sql := IF(
  @meal_type_exists = 0,
  'ALTER TABLE `meal_detail` ADD COLUMN `meal_type` VARCHAR(32) DEFAULT ''SNACK'' COMMENT ''BREAKFAST/LUNCH/DINNER/SNACK'' AFTER `food_id`',
  'SELECT 1'
);
PREPARE meal_type_stmt FROM @meal_type_sql;
EXECUTE meal_type_stmt;
DEALLOCATE PREPARE meal_type_stmt;

CREATE TABLE IF NOT EXISTS `advisor_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT',
  `content` VARCHAR(2000) NOT NULL,
  `references_json` TEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_advisor_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='advisor chat messages';

SET @advisor_message_references_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'advisor_message'
    AND COLUMN_NAME = 'references_json'
);
SET @advisor_message_references_sql := IF(
  @advisor_message_references_exists = 0,
  'ALTER TABLE `advisor_message` ADD COLUMN `references_json` TEXT DEFAULT NULL AFTER `content`',
  'SELECT 1'
);
PREPARE advisor_message_references_stmt FROM @advisor_message_references_sql;
EXECUTE advisor_message_references_stmt;
DEALLOCATE PREPARE advisor_message_references_stmt;

CREATE TABLE IF NOT EXISTS `community_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `author_name` VARCHAR(64) NOT NULL,
  `title` VARCHAR(128) DEFAULT NULL,
  `content` VARCHAR(2000) NOT NULL,
  `image_urls` TEXT DEFAULT NULL,
  `tag` VARCHAR(32) DEFAULT '全部',
  `moderation_status` VARCHAR(16) DEFAULT 'APPROVED',
  `like_count` INT DEFAULT 0,
  `favorite_count` INT DEFAULT 0,
  `comment_count` INT DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_created_at` (`created_at`),
  KEY `idx_post_tag` (`tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community posts';

CREATE TABLE IF NOT EXISTS `community_post_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_like_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community post likes';

SET @community_post_image_urls_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND COLUMN_NAME = 'image_urls'
);
SET @community_post_image_urls_sql := IF(
  @community_post_image_urls_exists = 0,
  'ALTER TABLE `community_post` ADD COLUMN `image_urls` TEXT DEFAULT NULL AFTER `content`',
  'SELECT 1'
);
PREPARE community_post_image_urls_stmt FROM @community_post_image_urls_sql;
EXECUTE community_post_image_urls_stmt;
DEALLOCATE PREPARE community_post_image_urls_stmt;

SET @community_post_favorite_count_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND COLUMN_NAME = 'favorite_count'
);
SET @community_post_favorite_count_sql := IF(
  @community_post_favorite_count_exists = 0,
  'ALTER TABLE `community_post` ADD COLUMN `favorite_count` INT DEFAULT 0 AFTER `like_count`',
  'SELECT 1'
);
PREPARE community_post_favorite_count_stmt FROM @community_post_favorite_count_sql;
EXECUTE community_post_favorite_count_stmt;
DEALLOCATE PREPARE community_post_favorite_count_stmt;

SET @community_post_comment_count_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND COLUMN_NAME = 'comment_count'
);
SET @community_post_comment_count_sql := IF(
  @community_post_comment_count_exists = 0,
  'ALTER TABLE `community_post` ADD COLUMN `comment_count` INT DEFAULT 0 AFTER `favorite_count`',
  'SELECT 1'
);
PREPARE community_post_comment_count_stmt FROM @community_post_comment_count_sql;
EXECUTE community_post_comment_count_stmt;
DEALLOCATE PREPARE community_post_comment_count_stmt;

SET @community_post_moderation_status_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND COLUMN_NAME = 'moderation_status'
);
SET @community_post_moderation_status_sql := IF(
  @community_post_moderation_status_exists = 0,
  'ALTER TABLE `community_post` ADD COLUMN `moderation_status` VARCHAR(16) DEFAULT ''APPROVED'' AFTER `tag`',
  'SELECT 1'
);
PREPARE community_post_moderation_status_stmt FROM @community_post_moderation_status_sql;
EXECUTE community_post_moderation_status_stmt;
DEALLOCATE PREPARE community_post_moderation_status_stmt;

CREATE TABLE IF NOT EXISTS `community_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `author_name` VARCHAR(64) NOT NULL,
  `content` VARCHAR(1000) NOT NULL,
  `moderation_status` VARCHAR(16) DEFAULT 'APPROVED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_time` (`post_id`, `created_at`),
  KEY `idx_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community post comments';

SET @community_comment_moderation_status_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_comment'
    AND COLUMN_NAME = 'moderation_status'
);
SET @community_comment_moderation_status_sql := IF(
  @community_comment_moderation_status_exists = 0,
  'ALTER TABLE `community_comment` ADD COLUMN `moderation_status` VARCHAR(16) DEFAULT ''APPROVED'' AFTER `content`',
  'SELECT 1'
);
PREPARE community_comment_moderation_status_stmt FROM @community_comment_moderation_status_sql;
EXECUTE community_comment_moderation_status_stmt;
DEALLOCATE PREPARE community_comment_moderation_status_stmt;

CREATE TABLE IF NOT EXISTS `post_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_post_user` (`post_id`, `user_id`),
  KEY `idx_favorite_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community post favorites';

CREATE TABLE IF NOT EXISTS `meal_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `plan_date` DATE NOT NULL COMMENT 'plan date',
  `title` VARCHAR(128) DEFAULT NULL,
  `notes` VARCHAR(500) DEFAULT NULL,
  `status` VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/READY/APPLIED',
  `total_calories` DECIMAL(8,2) DEFAULT 0,
  `total_protein` DECIMAL(8,2) DEFAULT 0,
  `total_fat` DECIMAL(8,2) DEFAULT 0,
  `total_carbohydrate` DECIMAL(8,2) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_user_date` (`user_id`, `plan_date`),
  KEY `idx_plan_user_time` (`user_id`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='daily meal plan';

CREATE TABLE IF NOT EXISTS `meal_plan_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_id` BIGINT NOT NULL,
  `food_id` BIGINT NOT NULL,
  `meal_type` VARCHAR(32) DEFAULT 'SNACK',
  `quantity` DECIMAL(8,2) DEFAULT 0,
  `note` VARCHAR(255) DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `calories` DECIMAL(8,2) DEFAULT 0,
  `protein` DECIMAL(8,2) DEFAULT 0,
  `fat` DECIMAL(8,2) DEFAULT 0,
  `carbohydrate` DECIMAL(8,2) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plan_item_plan` (`plan_id`),
  KEY `idx_plan_item_food` (`food_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='meal plan items';

CREATE TABLE IF NOT EXISTS `agent_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `scene_type` VARCHAR(64) NOT NULL COMMENT 'ADVISOR_CHAT/MEAL_PLAN_DAILY/MEAL_PLAN_WEEK',
  `request_summary` VARCHAR(500) DEFAULT NULL,
  `generation_mode` VARCHAR(64) DEFAULT NULL,
  `final_status` VARCHAR(32) DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
  `final_summary` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_execution_scene_time` (`scene_type`, `created_at`),
  KEY `idx_agent_execution_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='multi-agent execution sessions';

CREATE TABLE IF NOT EXISTS `agent_execution_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `execution_id` BIGINT NOT NULL,
  `step_order` INT DEFAULT 0,
  `agent_name` VARCHAR(64) NOT NULL,
  `stage_name` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/SKIPPED',
  `input_summary` TEXT DEFAULT NULL,
  `output_summary` TEXT DEFAULT NULL,
  `reference_summary` TEXT DEFAULT NULL,
  `duration_ms` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_execution_step_execution` (`execution_id`, `step_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='multi-agent execution steps';

CREATE TABLE IF NOT EXISTS `reward_account` (
  `user_id` BIGINT NOT NULL,
  `total_points` INT DEFAULT 0,
  `badge_count` INT DEFAULT 0,
  `current_streak` INT DEFAULT 0,
  `last_check_in_date` DATE DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='lightweight reward account';

CREATE TABLE IF NOT EXISTS `reward_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `biz_key` VARCHAR(128) NOT NULL,
  `points` INT DEFAULT 0,
  `title` VARCHAR(64) DEFAULT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `record_date` DATE DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_log_biz_key` (`biz_key`),
  KEY `idx_reward_log_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='reward points log';

CREATE TABLE IF NOT EXISTS `user_badge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `badge_code` VARCHAR(64) NOT NULL,
  `badge_name` VARCHAR(64) NOT NULL,
  `badge_description` VARCHAR(255) DEFAULT NULL,
  `earned_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge` (`user_id`, `badge_code`),
  KEY `idx_user_badge_user_time` (`user_id`, `earned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='earned badges';
