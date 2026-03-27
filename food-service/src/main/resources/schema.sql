CREATE TABLE IF NOT EXISTS `food_basics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NOT NULL COMMENT 'food name',
  `category` VARCHAR(64) DEFAULT NULL COMMENT 'food category',
  `barcode` VARCHAR(64) DEFAULT NULL COMMENT 'barcode',
  `unit` VARCHAR(32) DEFAULT '100g' COMMENT 'nutrition unit',
  `calories` DECIMAL(8,2) DEFAULT 0 COMMENT 'calories per 100g',
  `protein` DECIMAL(8,2) DEFAULT 0 COMMENT 'protein per 100g',
  `fat` DECIMAL(8,2) DEFAULT 0 COMMENT 'fat per 100g',
  `carbohydrate` DECIMAL(8,2) DEFAULT 0 COMMENT 'carbohydrate per 100g',
  `fiber` DECIMAL(8,2) DEFAULT 0 COMMENT 'fiber per 100g',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` TINYINT DEFAULT 1 COMMENT '0 disabled, 1 enabled',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food basics';

SET @barcode_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_basics'
    AND COLUMN_NAME = 'barcode'
);
SET @barcode_sql := IF(
  @barcode_exists = 0,
  'ALTER TABLE `food_basics` ADD COLUMN `barcode` VARCHAR(64) DEFAULT NULL COMMENT ''barcode'' AFTER `category`',
  'SELECT 1'
);
PREPARE barcode_stmt FROM @barcode_sql;
EXECUTE barcode_stmt;
DEALLOCATE PREPARE barcode_stmt;

SET @unit_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_basics'
    AND COLUMN_NAME = 'unit'
);
SET @unit_sql := IF(
  @unit_exists = 0,
  'ALTER TABLE `food_basics` ADD COLUMN `unit` VARCHAR(32) DEFAULT ''100g'' COMMENT ''nutrition unit'' AFTER `barcode`',
  'SELECT 1'
);
PREPARE unit_stmt FROM @unit_sql;
EXECUTE unit_stmt;
DEALLOCATE PREPARE unit_stmt;
