-- ============================================
-- 金属厂数据管理系统 - Navicat 专用初始化脚本
--
-- 使用方法（重要！）：
--   1. 先在 Navicat 中手动创建数据库:
--      CREATE DATABASE metal_system DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
--   2. 双击打开 metal_system 数据库
--   3. 右键 → "运行 SQL 文件..." → 选择本文件
--   4. 或者: 打开查询编辑器 → 粘贴全部内容 → 执行
-- ============================================

-- ============================================
-- 系统表
-- ============================================

-- 公司表
CREATE TABLE IF NOT EXISTS `company` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `real_name` VARCHAR(100),
    `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色: admin / user',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 操作日志
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT,
    `username` VARCHAR(50),
    `action` VARCHAR(20) NOT NULL COMMENT 'INSERT/UPDATE/DELETE',
    `table_name` VARCHAR(50) NOT NULL,
    `record_id` BIGINT,
    `detail` TEXT,
    `ip` VARCHAR(50),
    `company_id` BIGINT COMMENT '公司ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_ol_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 业务表
-- ============================================

-- 送货记录
CREATE TABLE IF NOT EXISTS `delivery_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `record_date` DATE,
    `category` VARCHAR(100),
    `material_name` VARCHAR(200),
    `spec_model` VARCHAR(300),
    `material_code` VARCHAR(100),
    `material_serial` VARCHAR(100),
    `quantity` INT,
    `unit` VARCHAR(20),
    `brand` VARCHAR(100),
    `product_attr` VARCHAR(20) COMMENT '新品/维修品',
    `factory` VARCHAR(100),
    `shipment_no` VARCHAR(100),
    `remark` TEXT COMMENT '备注',
    `year_month` VARCHAR(20) COMMENT '年月格式: FY2607',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_dr_date` (`record_date`),
    INDEX `idx_dr_mcode` (`material_code`),
    INDEX `idx_dr_cat` (`category`),
    INDEX `idx_dr_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 物料表
CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `category` VARCHAR(100),
    `material_name` VARCHAR(200),
    `spec_model` VARCHAR(300),
    `material_code` VARCHAR(100),
    `remark` VARCHAR(255) COMMENT '备注',
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_m_mcode` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 原始记录
CREATE TABLE IF NOT EXISTS `original_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `year_month` VARCHAR(20),
    `record_date` DATE,
    `shift` VARCHAR(10) COMMENT '白班/夜班',
    `factory` VARCHAR(100),
    `serial_number` VARCHAR(100),
    `machine_no` VARCHAR(100),
    `diagnostician` VARCHAR(50),
    `repair_person` VARCHAR(50),
    `repair_request_time` DATETIME,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `repair_hours` DECIMAL(10,2) COMMENT '自动计算',
    `downtime_hours` DECIMAL(10,2) COMMENT '自动计算',
    `machine_model` VARCHAR(100),
    `fault_phenomenon` VARCHAR(500),
    `fault_description` TEXT,
    `material_code` VARCHAR(100),
    `part_name` VARCHAR(200),
    `quantity` INT,
    `machine_on_material` VARCHAR(200),
    `machine_off_material` VARCHAR(200),
    `remark` TEXT,
    `confirmer` VARCHAR(50),
    `delivery_record_ref` VARCHAR(200),
    `last_machine_on_time` DATE COMMENT '自动查询',
    `is_out_of_warranty` VARCHAR(10) COMMENT '未过保/已过保/无',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_or_date` (`record_date`),
    INDEX `idx_or_mcode` (`material_code`),
    INDEX `idx_or_sn` (`serial_number`),
    INDEX `idx_or_on` (`machine_on_material`),
    INDEX `idx_or_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 未过保物料（与维修记录关联，派生字段由后端自动计算）
CREATE TABLE IF NOT EXISTS `unwarranted_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_uw_unique` (`unique_id`),
    INDEX `idx_uw_mcode` (`material_code`),
    INDEX `idx_uw_date` (`record_date`),
    INDEX `idx_uw_original` (`original_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 上机物料
CREATE TABLE IF NOT EXISTS `machine_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `year_month` VARCHAR(20),
    `record_date` DATE,
    `shift` VARCHAR(10),
    `factory` VARCHAR(100),
    `serial_number` VARCHAR(100),
    `machine_no` VARCHAR(100),
    `repair_person` VARCHAR(50),
    `repair_request_time` DATETIME,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `repair_hours` DECIMAL(10,2),
    `downtime_hours` DECIMAL(10,2),
    `machine_model` VARCHAR(100),
    `fault_phenomenon` VARCHAR(500),
    `fault_description` TEXT,
    `material_code` VARCHAR(100),
    `part_name` VARCHAR(200),
    `quantity` INT,
    `machine_on_material` VARCHAR(200),
    `machine_off_material` VARCHAR(200),
    `remark` TEXT,
    `confirmer` VARCHAR(50),
    `delivery_record_ref` VARCHAR(200),
    `last_machine_on_time` DATE,
    `is_out_of_warranty` VARCHAR(10),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_mm_date` (`record_date`),
    INDEX `idx_mm_mcode` (`material_code`),
    INDEX `idx_mm_on` (`machine_on_material`),
    INDEX `idx_mm_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 送货超比统计
CREATE TABLE IF NOT EXISTS `delivery_stats` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `category` VARCHAR(100),
    `material_code` VARCHAR(100),
    `system_name` VARCHAR(200),
    `part_name` VARCHAR(200),
    `unit_usage` DECIMAL(10,4) COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) COMMENT '比例',
    `unit_price_with_tax` DECIMAL(12,4) COMMENT '含税单价',
    `machine_count` INT COMMENT '机台数',
    `delivery_quantity` INT COMMENT '送货数量',
    `machine_on_quantity` INT COMMENT '上机数量',
    `month_repair` INT COMMENT '当月返修',
    `agreed_ratio_quantity` DECIMAL(12,4) COMMENT '约定比例数量',
    `excess_quantity` DECIMAL(12,4) COMMENT '超比数量合计',
    `excess_amount_with_tax` DECIMAL(14,4) COMMENT '超比含税金额合计',
    `stat_date` DATE,
    `year_month` VARCHAR(20),
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_ds_mcode` (`material_code`),
    INDEX `idx_ds_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 超比统计每日明细
CREATE TABLE IF NOT EXISTS `delivery_stats_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stat_id` BIGINT NOT NULL,
    `day_number` INT NOT NULL COMMENT '1-31',
    `value` DECIMAL(12,4),
    INDEX `idx_dsd_sid` (`stat_id`),
    FOREIGN KEY (`stat_id`) REFERENCES `delivery_stats`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 结算机台数
CREATE TABLE IF NOT EXISTS `settlement_machine` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `material_code` VARCHAR(100),
    `category` VARCHAR(100),
    `part_name` VARCHAR(200),
    `unit_usage` DECIMAL(10,4),
    `ratio` DECIMAL(10,4),
    `unit_price_with_tax` DECIMAL(12,4) COMMENT '价格(含税)',
    `warranty_period` VARCHAR(50),
    `price_type` VARCHAR(50),
    `remark` TEXT,
    `machine_model` VARCHAR(100),
    `settlement_machine_count` INT,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_sm_mcode` (`material_code`),
    INDEX `idx_sm_model` (`machine_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 机型明细
CREATE TABLE IF NOT EXISTS `machine_detail` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `factory` VARCHAR(100),
    `machine_no` VARCHAR(100),
    `plant_machine` VARCHAR(255) COMMENT '厂房+机台（唯一名，格式: A1-B11）',
    `machine_brand` VARCHAR(100),
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 开机数量表
CREATE TABLE IF NOT EXISTS `machine_count` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT COMMENT '公司ID',
    `machine_model` VARCHAR(100),
    `count` INT,
    `ratio_pct` DECIMAL(6,2),
    `stat_month` VARCHAR(10),
    `remark` VARCHAR(255),
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
