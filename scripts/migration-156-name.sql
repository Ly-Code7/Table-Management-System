-- ============================================================
-- 维修记录新增"156项名称"列（2026-08-05）
-- 用途：新增维修记录输入料号后，将料号在"156项"表对应的名称
--       回填到"156项名称"（不再回填"配件名称"）；导入模板同列。
-- 执行：生产库执行一次即可（幂等：先查后加）。
-- ============================================================
SELECT COUNT(*) INTO @cnt FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'original_record'
  AND COLUMN_NAME = 'material_156_name';

SET @sql = IF(@cnt = 0,
    'ALTER TABLE `original_record` ADD COLUMN `material_156_name` VARCHAR(200) DEFAULT NULL COMMENT ''156项名称'' AFTER `material_code`',
    'SELECT ''column material_156_name already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
