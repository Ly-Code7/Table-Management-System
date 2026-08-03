-- 维修记录表增加"厂房+机台号"字段（规则：厂房-机台号，添加/编辑/导入时自动计算）
-- 执行方式：应用重启前手动执行（或由运维在 Navicat 中执行）

ALTER TABLE `original_record`
    ADD COLUMN `plant_machine` varchar(100) DEFAULT NULL COMMENT '厂房+机台号（自动计算：厂房-机台号）' AFTER `machine_no`;

-- 历史数据回填：已存在且厂房/机台号均非空的记录
UPDATE `original_record`
SET `plant_machine` = CONCAT(TRIM(`factory`), '-', TRIM(`machine_no`))
WHERE `plant_machine` IS NULL
  AND `factory` IS NOT NULL AND TRIM(`factory`) != ''
  AND `machine_no` IS NOT NULL AND TRIM(`machine_no`) != '';
