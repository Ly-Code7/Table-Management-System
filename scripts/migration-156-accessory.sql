-- ============================================================
-- 156项表字段改名 + 新增"配件"列（2026-08-06）
-- 用途：base_material_156.part_name 语义由"配件名称"改为"156项名称"
--       （显示名/注释同步；列名 part_name 保留，消费方 SQL 不变）；
--       新增 accessory 列（配件），维修记录料号回填时使用。
-- 执行：生产库执行一次即可（幂等：先查后改/加）。
-- ============================================================

-- 1. part_name 注释改为 '156项名称'
SELECT COUNT(*) INTO @cnt FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material_156'
  AND COLUMN_NAME = 'part_name'
  AND COLUMN_COMMENT = '156项名称';

SET @sql = IF(@cnt = 0,
    'ALTER TABLE `base_material_156` MODIFY COLUMN `part_name` VARCHAR(200) DEFAULT NULL COMMENT ''156项名称''',
    'SELECT ''column part_name comment already 156项名称''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 新增 accessory 列（配件）
SELECT COUNT(*) INTO @cnt FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'base_material_156'
  AND COLUMN_NAME = 'accessory';

SET @sql = IF(@cnt = 0,
    'ALTER TABLE `base_material_156` ADD COLUMN `accessory` VARCHAR(200) DEFAULT NULL COMMENT ''配件'' AFTER `part_name`',
    'SELECT ''column accessory already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
