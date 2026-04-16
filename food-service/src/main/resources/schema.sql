CREATE TABLE IF NOT EXISTS `food_categories` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) NOT NULL COMMENT 'category name',
  `parent_id` BIGINT DEFAULT NULL COMMENT 'parent category id',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'category description',
  `sort_order` INT DEFAULT 0 COMMENT 'sort order',
  `status` TINYINT DEFAULT 1 COMMENT '0 disabled, 1 enabled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_category_name` (`name`),
  KEY `idx_food_category_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food categories';

CREATE TABLE IF NOT EXISTS `food_concepts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `concept_code` VARCHAR(64) NOT NULL COMMENT 'stable concept code',
  `canonical_name` VARCHAR(128) NOT NULL COMMENT 'canonical chinese concept name',
  `canonical_name_en` VARCHAR(128) DEFAULT NULL COMMENT 'canonical english concept name',
  `parent_id` BIGINT DEFAULT NULL COMMENT 'parent concept id',
  `category_id` BIGINT DEFAULT NULL COMMENT 'linked food_categories.id',
  `concept_level` INT DEFAULT 1 COMMENT '1 root, larger means deeper node',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'concept description',
  `sort_order` INT DEFAULT 0 COMMENT 'sort order',
  `status` TINYINT DEFAULT 1 COMMENT '0 disabled, 1 enabled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_concept_code` (`concept_code`),
  KEY `idx_food_concept_parent` (`parent_id`),
  KEY `idx_food_concept_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food concepts';

CREATE TABLE IF NOT EXISTS `food_basics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NOT NULL COMMENT 'food name',
  `category` VARCHAR(64) DEFAULT NULL COMMENT 'food category',
  `category_id` BIGINT DEFAULT NULL COMMENT 'linked food_categories.id',
  `concept_id` BIGINT DEFAULT NULL COMMENT 'linked food_concepts.id',
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
  UNIQUE KEY `uk_food_name` (`name`),
  KEY `idx_food_category_id` (`category_id`),
  KEY `idx_food_concept_id` (`concept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food basics';

CREATE TABLE IF NOT EXISTS `food_aliases` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `food_id` BIGINT NOT NULL COMMENT 'linked food_basics.id',
  `alias_name` VARCHAR(128) NOT NULL COMMENT 'alias name',
  `alias_type` VARCHAR(32) DEFAULT 'COMMON' COMMENT 'COMMON, SEARCH, VISION',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_alias` (`food_id`, `alias_name`),
  KEY `idx_food_alias_name` (`alias_name`),
  CONSTRAINT `fk_food_alias_food` FOREIGN KEY (`food_id`) REFERENCES `food_basics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food aliases';

CREATE TABLE IF NOT EXISTS `food_concept_aliases` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `concept_id` BIGINT NOT NULL COMMENT 'linked food_concepts.id',
  `alias_name` VARCHAR(128) NOT NULL COMMENT 'concept alias',
  `alias_lang` VARCHAR(16) DEFAULT 'zh' COMMENT 'zh, en or mixed',
  `alias_type` VARCHAR(32) DEFAULT 'COMMON' COMMENT 'COMMON, SEARCH, MODEL, VISION',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_concept_alias` (`concept_id`, `alias_name`),
  KEY `idx_food_concept_alias_name` (`alias_name`),
  CONSTRAINT `fk_food_concept_alias_concept` FOREIGN KEY (`concept_id`) REFERENCES `food_concepts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food concept aliases';

CREATE TABLE IF NOT EXISTS `food_image_samples` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `food_id` BIGINT NOT NULL COMMENT 'linked food_basics.id',
  `image_url` VARCHAR(512) NOT NULL COMMENT 'sample image url',
  `source` VARCHAR(64) DEFAULT 'SYSTEM' COMMENT 'source type',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'sample description',
  `sort_order` INT DEFAULT 0 COMMENT 'sort order',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_food_image_food` (`food_id`),
  CONSTRAINT `fk_food_image_food` FOREIGN KEY (`food_id`) REFERENCES `food_basics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food image samples';

CREATE TABLE IF NOT EXISTS `food_recognition_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL COMMENT 'user id from user-service',
  `food_id` BIGINT DEFAULT NULL COMMENT 'linked food_basics.id',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT 'uploaded image url',
  `recognized_label` VARCHAR(128) DEFAULT NULL COMMENT 'vision label',
  `recognized_canonical_label` VARCHAR(128) DEFAULT NULL COMMENT 'canonical vision label',
  `matched_food_name` VARCHAR(128) DEFAULT NULL COMMENT 'matched catalog food name',
  `confidence` DECIMAL(5,2) DEFAULT NULL COMMENT '0-1 confidence',
  `recognition_mode` VARCHAR(64) DEFAULT NULL COMMENT 'python, clip, onnx and so on',
  `search_terms` VARCHAR(512) DEFAULT NULL COMMENT 'search terms joined by comma',
  `manual_confirmation_required` TINYINT DEFAULT 1 COMMENT '1 yes, 0 no',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `recognized_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_food_recognition_user` (`user_id`),
  KEY `idx_food_recognition_food` (`food_id`),
  CONSTRAINT `fk_food_recognition_food` FOREIGN KEY (`food_id`) REFERENCES `food_basics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='food recognition logs';

CREATE TABLE IF NOT EXISTS `knowledge_sources` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(255) NOT NULL COMMENT 'source title',
  `organization` VARCHAR(128) DEFAULT NULL COMMENT 'source organization',
  `source_type` VARCHAR(32) DEFAULT 'GUIDELINE' COMMENT 'GUIDELINE, PAPER, WEBSITE, MANUAL',
  `publish_year` INT DEFAULT NULL COMMENT 'publish year',
  `source_url` VARCHAR(512) DEFAULT NULL COMMENT 'source url',
  `credibility_level` VARCHAR(32) DEFAULT 'HIGH' COMMENT 'HIGH, MEDIUM, LOW',
  `summary` VARCHAR(512) DEFAULT NULL COMMENT 'summary',
  `status` TINYINT DEFAULT 1 COMMENT '0 disabled, 1 enabled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_source_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='authoritative knowledge sources';

CREATE TABLE IF NOT EXISTS `food_graph_relations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `source_type` VARCHAR(32) NOT NULL COMMENT 'FOOD, GOAL, CONDITION, NUTRIENT, CATEGORY, CROWD, METHOD',
  `source_key` VARCHAR(64) NOT NULL COMMENT 'stable source key',
  `source_ref_id` BIGINT DEFAULT NULL COMMENT 'optional linked row id',
  `source_name` VARCHAR(128) NOT NULL COMMENT 'source display name',
  `relation_type` VARCHAR(32) NOT NULL COMMENT 'BELONGS_TO, CONTAINS, RECOMMENDS, LIMITS, CAN_REPLACE, PAIR_WITH, SHOULD_LIMIT',
  `target_type` VARCHAR(32) NOT NULL COMMENT 'target node type',
  `target_key` VARCHAR(64) NOT NULL COMMENT 'stable target key',
  `target_ref_id` BIGINT DEFAULT NULL COMMENT 'optional linked row id',
  `target_name` VARCHAR(128) NOT NULL COMMENT 'target display name',
  `relation_value` VARCHAR(128) DEFAULT NULL COMMENT 'optional relation value',
  `evidence_summary` VARCHAR(512) DEFAULT NULL COMMENT 'relation evidence summary',
  `knowledge_source_id` BIGINT DEFAULT NULL COMMENT 'linked knowledge_sources.id',
  `sort_order` INT DEFAULT 0 COMMENT 'sort order',
  `status` TINYINT DEFAULT 1 COMMENT '0 disabled, 1 enabled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_graph_relation` (`source_type`, `source_key`, `relation_type`, `target_type`, `target_key`),
  KEY `idx_food_graph_source_ref` (`source_ref_id`),
  KEY `idx_food_graph_target_ref` (`target_ref_id`),
  KEY `idx_food_graph_relation_type` (`relation_type`),
  KEY `idx_food_graph_knowledge_source` (`knowledge_source_id`),
  CONSTRAINT `fk_food_graph_knowledge_source` FOREIGN KEY (`knowledge_source_id`) REFERENCES `knowledge_sources` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='nutrition knowledge graph relations';

CREATE TABLE IF NOT EXISTS `food_graph_sync_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `sync_type` VARCHAR(32) NOT NULL COMMENT 'BOOTSTRAP, MANUAL, NEO4J_EXPORT',
  `status` VARCHAR(32) NOT NULL COMMENT 'SUCCESS, FAILED, SKIPPED',
  `detail` VARCHAR(512) DEFAULT NULL COMMENT 'sync detail',
  `node_count` INT DEFAULT 0 COMMENT 'related node count',
  `relation_count` INT DEFAULT 0 COMMENT 'related relation count',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_food_graph_sync_type` (`sync_type`),
  KEY `idx_food_graph_sync_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='knowledge graph sync logs';

SET @category_id_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_basics'
    AND COLUMN_NAME = 'category_id'
);
SET @category_id_sql := IF(
  @category_id_exists = 0,
  'ALTER TABLE `food_basics` ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT ''linked food_categories.id'' AFTER `category`',
  'SELECT 1'
);
PREPARE category_id_stmt FROM @category_id_sql;
EXECUTE category_id_stmt;
DEALLOCATE PREPARE category_id_stmt;

SET @concept_id_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_basics'
    AND COLUMN_NAME = 'concept_id'
);
SET @concept_id_sql := IF(
  @concept_id_exists = 0,
  'ALTER TABLE `food_basics` ADD COLUMN `concept_id` BIGINT DEFAULT NULL COMMENT ''linked food_concepts.id'' AFTER `category_id`',
  'SELECT 1'
);
PREPARE concept_id_stmt FROM @concept_id_sql;
EXECUTE concept_id_stmt;
DEALLOCATE PREPARE concept_id_stmt;

SET @barcode_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_basics'
    AND COLUMN_NAME = 'barcode'
);
SET @barcode_sql := IF(
  @barcode_exists = 0,
  'ALTER TABLE `food_basics` ADD COLUMN `barcode` VARCHAR(64) DEFAULT NULL COMMENT ''barcode'' AFTER `concept_id`',
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

SET @recognized_canonical_label_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_recognition_logs'
    AND COLUMN_NAME = 'recognized_canonical_label'
);
SET @recognized_canonical_label_sql := IF(
  @recognized_canonical_label_exists = 0,
  'ALTER TABLE `food_recognition_logs` ADD COLUMN `recognized_canonical_label` VARCHAR(128) DEFAULT NULL COMMENT ''canonical vision label'' AFTER `recognized_label`',
  'SELECT 1'
);
PREPARE recognized_canonical_label_stmt FROM @recognized_canonical_label_sql;
EXECUTE recognized_canonical_label_stmt;
DEALLOCATE PREPARE recognized_canonical_label_stmt;

SET @recognition_mode_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_recognition_logs'
    AND COLUMN_NAME = 'recognition_mode'
);
SET @recognition_mode_sql := IF(
  @recognition_mode_exists = 0,
  'ALTER TABLE `food_recognition_logs` ADD COLUMN `recognition_mode` VARCHAR(64) DEFAULT NULL COMMENT ''python, clip, onnx and so on'' AFTER `confidence`',
  'SELECT 1'
);
PREPARE recognition_mode_stmt FROM @recognition_mode_sql;
EXECUTE recognition_mode_stmt;
DEALLOCATE PREPARE recognition_mode_stmt;

SET @search_terms_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'food_recognition_logs'
    AND COLUMN_NAME = 'search_terms'
);
SET @search_terms_sql := IF(
  @search_terms_exists = 0,
  'ALTER TABLE `food_recognition_logs` ADD COLUMN `search_terms` VARCHAR(512) DEFAULT NULL COMMENT ''search terms joined by comma'' AFTER `recognition_mode`',
  'SELECT 1'
);
PREPARE search_terms_stmt FROM @search_terms_sql;
EXECUTE search_terms_stmt;
DEALLOCATE PREPARE search_terms_stmt;
