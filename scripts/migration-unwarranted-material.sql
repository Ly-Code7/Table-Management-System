-- ============================================
-- 金属厂数据管理系统 - 物料备注 / 机型厂房+机台 / 未过保物料 迁移脚本
-- 日期: 2026-08-01
-- ============================================

USE metal_system;

-- 1. material 表新增 备注 字段
ALTER TABLE `material`
    ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注'
    AFTER `material_code`;

-- 2. machine_detail 表新增 厂房+机台 字段
ALTER TABLE `machine_detail`
    ADD COLUMN `plant_machine` VARCHAR(255) COMMENT '厂房+机台'
    AFTER `machine_no`;

-- 3. 未过保物料表
CREATE TABLE IF NOT EXISTS `unwarranted_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `record_date` DATE COMMENT '日期',
    `factory` VARCHAR(100) COMMENT '厂房',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `equip_repair_debugging` TEXT COMMENT '设备维修调试',
    `repair_material_on` VARCHAR(200) COMMENT '维修物料装上',
    `repair_person` VARCHAR(50) COMMENT '维修人',
    `warranty_status` VARCHAR(20) COMMENT '未过保（回填自维修记录是否过保）',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `quantity` INT COMMENT '数量',
    `material_code` VARCHAR(100) COMMENT '物料编码',
    `unique_id` VARCHAR(255) COMMENT '唯一标识编号（厂房-机台物料编码）',
    `last_date_no` VARCHAR(300) COMMENT '上次日期+编号',
    `current_date_no` VARCHAR(300) COMMENT '本次日期+编号',
    `plant_machine` VARCHAR(255) COMMENT '厂房+机台号',
    `year_month` VARCHAR(20) COMMENT '年+月',
    `repair_amount` DECIMAL(12,2) COMMENT '维修金额（合约）',
    `total_count` INT COMMENT '总次数',
    `occurrence_no` INT COMMENT '第几次',
    `last_date` DATE COMMENT '上次日期',
    `current_date` DATE COMMENT '本次日期',
    `over_six_months` VARCHAR(10) COMMENT '超六个月(N/Y/1st)',
    `usage_months` VARCHAR(20) COMMENT '使用时长/月(数字或1st)',
    `last_repair_person` VARCHAR(50) COMMENT '上次维修人',
    `original_record_id` BIGINT DEFAULT NULL COMMENT '关联的维修记录ID（一个维修记录只允许关联一条未过保物料）',
    `category` VARCHAR(100) DEFAULT NULL COMMENT '类别（按物料编码从物料表自动回填）',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_uw_unique` (`unique_id`),
    INDEX `idx_uw_mcode` (`material_code`),
    INDEX `idx_uw_date` (`record_date`),
    INDEX `idx_uw_original` (`original_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 未过保物料表新增 关联维修记录ID / 类别（已建表的库执行）
ALTER TABLE `unwarranted_material`
    ADD COLUMN `original_record_id` BIGINT DEFAULT NULL COMMENT '关联的维修记录ID（一个维修记录只允许关联一条未过保物料）' AFTER `last_repair_person`,
    ADD COLUMN `category` VARCHAR(100) DEFAULT NULL COMMENT '类别（按物料编码从物料表自动回填）' AFTER `original_record_id`,
    ADD INDEX `idx_uw_original` (`original_record_id`);
