CREATE TABLE IF NOT EXISTS `food_basics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NOT NULL COMMENT '食物名称',
  `category` VARCHAR(64) DEFAULT NULL COMMENT '类别，如蔬菜/肉类/主食',
  `barcode` VARCHAR(64) DEFAULT NULL COMMENT '条形码',
  `unit` VARCHAR(32) DEFAULT '100g' COMMENT '营养成分计算的基准单位',
  `calories` DECIMAL(8,2) DEFAULT 0 COMMENT '每100g热量(kcal)',
  `protein` DECIMAL(8,2) DEFAULT 0 COMMENT '每100g蛋白质(g)',
  `fat` DECIMAL(8,2) DEFAULT 0 COMMENT '每100g脂肪(g)',
  `carbohydrate` DECIMAL(8,2) DEFAULT 0 COMMENT '每100g碳水化合物(g)',
  `fiber` DECIMAL(8,2) DEFAULT 0 COMMENT '每100g膳食纤维(g)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` TINYINT DEFAULT 1 COMMENT '0=禁用,1=启用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食物基础库';

SET @barcode_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_basics'
    AND COLUMN_NAME = 'barcode'
);
SET @barcode_sql := IF(
  @barcode_exists = 0,
  'ALTER TABLE `food_basics` ADD COLUMN `barcode` VARCHAR(64) DEFAULT NULL COMMENT ''条形码'' AFTER `category`',
  'SELECT 1'
);
PREPARE barcode_stmt FROM @barcode_sql;
EXECUTE barcode_stmt;
DEALLOCATE PREPARE barcode_stmt;
