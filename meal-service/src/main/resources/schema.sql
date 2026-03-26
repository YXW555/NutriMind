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
  UNIQUE KEY `uk_meal_user_date` (`user_id`,`record_date`)
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
  KEY `idx_advisor_user_time` (`user_id`,`created_at`)
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
  UNIQUE KEY `uk_post_user` (`post_id`,`user_id`),
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

CREATE TABLE IF NOT EXISTS `community_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `author_name` VARCHAR(64) NOT NULL,
  `content` VARCHAR(1000) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_time` (`post_id`,`created_at`),
  KEY `idx_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community post comments';

CREATE TABLE IF NOT EXISTS `post_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_post_user` (`post_id`,`user_id`),
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
  UNIQUE KEY `uk_plan_user_date` (`user_id`,`plan_date`),
  KEY `idx_plan_user_time` (`user_id`,`plan_date`)
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
