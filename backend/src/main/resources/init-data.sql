-- ============================================
-- 金属厂数据管理系统 - 初始化脚本
-- 一键部署: mysql -u root -p < init-data.sql
-- ============================================

CREATE DATABASE IF NOT EXISTS metal_system
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE metal_system;

-- ============================================
-- 系统表
-- ============================================

-- 公司表
CREATE TABLE IF NOT EXISTS `company` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表（username = 手机号，password 废弃不用，手机验证码登录）
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '手机号',
    `password` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '已废弃，改用验证码登录',
    `real_name` VARCHAR(100) COMMENT '真实姓名',
    `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色: admin / user',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值',
    `description` VARCHAR(200) COMMENT '配置描述',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户在线状态表
CREATE TABLE IF NOT EXISTS `user_online` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名（手机号）',
    `last_active_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    UNIQUE KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '操作用户名',
    `action` VARCHAR(20) NOT NULL COMMENT 'INSERT/UPDATE/DELETE',
    `table_name` VARCHAR(50) NOT NULL COMMENT '操作的表名',
    `record_id` BIGINT COMMENT '操作记录ID',
    `detail` TEXT COMMENT '操作详情',
    `ip` VARCHAR(50) COMMENT '操作IP地址',
    `company_id` BIGINT COMMENT '公司ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_ol_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- OCR 调用日志表
CREATE TABLE IF NOT EXISTS `ocr_call_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `table_type` VARCHAR(50) NOT NULL COMMENT 'original-record / delivery-record',
    `user_id` BIGINT DEFAULT NULL COMMENT '调用用户ID',
    `username` VARCHAR(100) DEFAULT NULL COMMENT '调用用户名',
    `image_size` BIGINT DEFAULT NULL COMMENT '图片大小（字节）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 业务表
-- ============================================

-- 156项基础物料表
CREATE TABLE IF NOT EXISTS `base_material_156` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT NULL COMMENT '公司ID',
    `category` VARCHAR(100) DEFAULT NULL COMMENT '类别',
    `material_code` VARCHAR(100) NOT NULL COMMENT '料号',
    `system_name` VARCHAR(200) DEFAULT NULL COMMENT '系统名称',
    `part_name` VARCHAR(200) DEFAULT NULL COMMENT '配件名称',
    `unit_usage` DECIMAL(10,4) DEFAULT NULL COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) DEFAULT NULL COMMENT '比例（0~1小数）',
    `unit_price_with_tax` DECIMAL(12,4) DEFAULT NULL COMMENT '含税单价',
    `created_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    `updated_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `idx_b156_mcode_company` (`material_code`, `company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 送货记录
CREATE TABLE IF NOT EXISTS `delivery_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `record_date` DATE COMMENT '送货日期',
    `category` VARCHAR(100) COMMENT '类别',
    `material_name` VARCHAR(200) COMMENT '物料名称',
    `spec_model` VARCHAR(300) COMMENT '规格型号',
    `material_code` VARCHAR(100) COMMENT '物料编码（料号）',
    `material_serial` VARCHAR(100) COMMENT '物料序列号',
    `quantity` INT COMMENT '数量',
    `unit` VARCHAR(20) COMMENT '单位',
    `brand` VARCHAR(100) COMMENT '品牌',
    `product_attr` VARCHAR(20) COMMENT '新品/维修品',
    `factory` VARCHAR(100) COMMENT '厂房',
    `shipment_no` VARCHAR(100) COMMENT '送货单号',
    `remark` TEXT COMMENT '备注',
    `year_month` VARCHAR(20) COMMENT '年+月（格式: FYyyMM）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    INDEX `idx_dr_date` (`record_date`),
    INDEX `idx_dr_mcode` (`material_code`),
    INDEX `idx_dr_cat` (`category`),
    INDEX `idx_dr_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 物料表
CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `category` VARCHAR(100) COMMENT '类别',
    `material_name` VARCHAR(200) COMMENT '物料名称',
    `spec_model` VARCHAR(300) COMMENT '规格型号',
    `material_code` VARCHAR(100) COMMENT '物料编码',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    INDEX `idx_m_mcode` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 维修记录（原始记录）
CREATE TABLE IF NOT EXISTS `original_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `year_month` VARCHAR(20) COMMENT '年+月（格式: FYyyMM）',
    `record_date` DATE COMMENT '维修日期',
    `shift` VARCHAR(10) COMMENT '白班/夜班',
    `factory` VARCHAR(100) COMMENT '厂房',
    `serial_number` VARCHAR(100) COMMENT '序号',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `diagnostician` VARCHAR(50) COMMENT '诊断人',
    `repair_person` VARCHAR(50) COMMENT '维修人',
    `repair_request_time` DATETIME COMMENT '报修时间',
    `start_time` DATETIME COMMENT '维修开始时间',
    `end_time` DATETIME COMMENT '维修结束时间',
    `repair_hours` DECIMAL(10,2) COMMENT '维修工时(分钟)',
    `downtime_hours` DECIMAL(10,2) COMMENT '停机工时(分钟)',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `fault_phenomenon` VARCHAR(500) COMMENT '故障现象',
    `fault_description` TEXT COMMENT '故障描述',
    `material_code` VARCHAR(100) COMMENT '物料编码（料号）',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `quantity` INT COMMENT '数量',
    `machine_on_material` VARCHAR(200) COMMENT '上机物料号',
    `machine_off_material` VARCHAR(200) COMMENT '下机物料号',
    `remark` TEXT COMMENT '备注',
    `confirmer` VARCHAR(50) COMMENT '确认人',
    `delivery_record_ref` VARCHAR(200) COMMENT '送货记录引用',
    `document_no` VARCHAR(100) DEFAULT NULL COMMENT '单据号',
    `last_machine_on_time` DATE COMMENT '上次上机时间',
    `is_out_of_warranty` VARCHAR(10) COMMENT '未过保/已过保/无',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    INDEX `idx_or_date` (`record_date`),
    INDEX `idx_or_mcode` (`material_code`),
    INDEX `idx_or_sn` (`serial_number`),
    INDEX `idx_or_on` (`machine_on_material`),
    INDEX `idx_or_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 上机物料（仅管理员可见）
CREATE TABLE IF NOT EXISTS `machine_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `year_month` VARCHAR(20) COMMENT '年+月（格式: FYyyMM）',
    `record_date` DATE COMMENT '记录日期',
    `shift` VARCHAR(10) COMMENT '白班/夜班',
    `factory` VARCHAR(100) COMMENT '厂房',
    `serial_number` VARCHAR(100) COMMENT '序号',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `repair_person` VARCHAR(50) COMMENT '维修人',
    `repair_request_time` DATETIME COMMENT '报修时间',
    `start_time` DATETIME COMMENT '维修开始时间',
    `end_time` DATETIME COMMENT '维修结束时间',
    `repair_hours` DECIMAL(10,2) COMMENT '维修工时(分钟)',
    `downtime_hours` DECIMAL(10,2) COMMENT '停机工时(分钟)',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `fault_phenomenon` VARCHAR(500) COMMENT '故障现象',
    `fault_description` TEXT COMMENT '故障描述',
    `material_code` VARCHAR(100) COMMENT '物料编码（料号）',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `quantity` INT COMMENT '数量',
    `machine_on_material` VARCHAR(200) COMMENT '上机物料号',
    `machine_off_material` VARCHAR(200) COMMENT '下机物料号',
    `remark` TEXT COMMENT '备注',
    `confirmer` VARCHAR(50) COMMENT '确认人',
    `delivery_record_ref` VARCHAR(200) COMMENT '送货记录引用',
    `last_machine_on_time` DATE COMMENT '上次上机时间',
    `is_out_of_warranty` VARCHAR(10) COMMENT '未过保/已过保/无',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    INDEX `idx_mm_date` (`record_date`),
    INDEX `idx_mm_mcode` (`material_code`),
    INDEX `idx_mm_on` (`machine_on_material`),
    INDEX `idx_mm_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 送货超比统计主表
CREATE TABLE IF NOT EXISTS `delivery_stats` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `category` VARCHAR(100) COMMENT '类别',
    `material_code` VARCHAR(100) COMMENT '料号',
    `system_name` VARCHAR(200) COMMENT '系统名称',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `unit_usage` DECIMAL(10,4) COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) COMMENT '比例（0~1小数）',
    `unit_price_with_tax` DECIMAL(12,4) COMMENT '含税单价',
    `machine_count` INT COMMENT '机台数',
    `delivery_quantity` INT COMMENT '送货数量',
    `machine_on_quantity` INT COMMENT '上机数量',
    `month_repair` INT COMMENT '当月返修',
    `agreed_ratio_quantity` DECIMAL(12,4) COMMENT '约定比例数量',
    `excess_quantity` DECIMAL(12,4) COMMENT '超比数量合计',
    `excess_amount_with_tax` DECIMAL(14,2) COMMENT '超比含税金额合计',
    `stat_date` DATE COMMENT '统计日期',
    `year_month` VARCHAR(20) COMMENT '年+月（格式: FYyyMM）',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_ds_mcode` (`material_code`),
    INDEX `idx_ds_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 超比统计每日明细
CREATE TABLE IF NOT EXISTS `delivery_stats_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `stat_id` BIGINT NOT NULL COMMENT '统计主表ID',
    `day_number` INT NOT NULL COMMENT '1-31',
    `value` DECIMAL(12,4) COMMENT '当日数值',
    INDEX `idx_dsd_sid` (`stat_id`),
    FOREIGN KEY (`stat_id`) REFERENCES `delivery_stats`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 结算机台数
CREATE TABLE IF NOT EXISTS `settlement_machine` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `material_code` VARCHAR(100) COMMENT '料号',
    `category` VARCHAR(100) COMMENT '类别',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `unit_usage` DECIMAL(10,4) COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) COMMENT '比例（0~1小数）',
    `unit_price_with_tax` DECIMAL(12,4) COMMENT '含税单价',
    `warranty_period` VARCHAR(50) COMMENT '保修期',
    `price_type` VARCHAR(50) COMMENT '价格类型',
    `remark` TEXT COMMENT '备注',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `settlement_machine_count` INT COMMENT '结算机台数',
    `stat_month` VARCHAR(7) DEFAULT NULL COMMENT '统计月份(格式yyyy-MM)',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_sm_mcode` (`material_code`),
    INDEX `idx_sm_model` (`machine_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 机型明细
CREATE TABLE IF NOT EXISTS `machine_detail` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `factory` VARCHAR(100) COMMENT '厂房',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `machine_brand` VARCHAR(100) COMMENT '机台品牌',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 开机数量
CREATE TABLE IF NOT EXISTS `machine_count` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `count` INT COMMENT '开机数量',
    `ratio_pct` DECIMAL(6,2) COMMENT '占比百分比',
    `stat_month` VARCHAR(10) COMMENT '统计月份',
    `remark` VARCHAR(255) COMMENT '备注',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `updated_by` VARCHAR(50) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 初始数据
-- ============================================

-- 默认配置
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
    ('scheduler.cron', '0 0 3 * * *', '超比统计定时任务cron表达式');

-- 默认公司
INSERT IGNORE INTO `company` (`id`, `name`) VALUES
    (1, '默认公司');

-- 管理员（手机验证码登录，请联系管理员获取手机号注册）
-- 管理员通过 /api/auth/register 接口注册新用户（手机号+姓名即可）
