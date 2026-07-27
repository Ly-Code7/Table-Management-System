-- ============================================================
-- 金属厂数据管理系统 - 完整初始化脚本
-- 版本: 2026-07-27
-- 数据库: metal_system (MySQL 5.7+ / 8.0+)
-- 字符集: utf8mb4
-- 引擎: InnoDB
--
-- 使用方法:
--   mysql -u root -p < init-data.sql
--
-- 注意: 此脚本使用 IF NOT EXISTS，可重复执行不会报错
-- ============================================================

CREATE DATABASE IF NOT EXISTS `metal_system`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `metal_system`;

-- ============================================================
-- 1. 公司表 company
-- ============================================================
CREATE TABLE IF NOT EXISTS `company` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司';

-- ============================================================
-- 2. 用户表 sys_user（手机验证码登录）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '手机号',
    `password` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '已废弃，改用验证码登录',
    `real_name` VARCHAR(100) COMMENT '真实姓名',
    `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色: admin / user',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ============================================================
-- 3. 系统配置表 sys_config
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值',
    `description` VARCHAR(200) COMMENT '说明',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- ============================================================
-- 4. 用户在线状态表 user_online
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_online` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `username` VARCHAR(100) NOT NULL,
    `last_active_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户在线状态';

-- ============================================================
-- 5. 操作日志表 operation_log
-- ============================================================
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT COMMENT '操作人ID',
    `username` VARCHAR(50) COMMENT '操作人用户名',
    `action` VARCHAR(20) NOT NULL COMMENT '操作类型: INSERT/UPDATE/DELETE',
    `table_name` VARCHAR(50) NOT NULL COMMENT '操作表名',
    `record_id` BIGINT COMMENT '记录ID',
    `detail` TEXT COMMENT '操作详情',
    `ip` VARCHAR(50) COMMENT 'IP地址',
    `company_id` BIGINT COMMENT '公司ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_ol_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ============================================================
-- 6. OCR 调用日志表 ocr_call_log
-- ============================================================
CREATE TABLE IF NOT EXISTS `ocr_call_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `table_type` VARCHAR(50) NOT NULL COMMENT '表类型: original-record / delivery-record',
    `user_id` BIGINT DEFAULT NULL,
    `username` VARCHAR(100) DEFAULT NULL,
    `image_size` BIGINT DEFAULT NULL COMMENT '图片字节数',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR调用日志';

-- ============================================================
-- 7. 156项基础物料表 base_material_156
-- ============================================================
CREATE TABLE IF NOT EXISTS `base_material_156` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT NULL COMMENT '公司ID',
    `category` VARCHAR(100) DEFAULT NULL COMMENT '类别',
    `material_code` VARCHAR(100) NOT NULL COMMENT '料号',
    `system_name` VARCHAR(200) DEFAULT NULL COMMENT '系统名称',
    `part_name` VARCHAR(200) DEFAULT NULL COMMENT '配件名称',
    `unit_usage` DECIMAL(10,4) DEFAULT NULL COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) DEFAULT NULL COMMENT '比例（0~1小数，如0.15=15%）',
    `unit_price_with_tax` DECIMAL(12,4) DEFAULT NULL COMMENT '含税单价',
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `idx_b156_mcode_company` (`material_code`, `company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基础物料156项';

-- ============================================================
-- 8. 送货记录表 delivery_record
-- ============================================================
CREATE TABLE IF NOT EXISTS `delivery_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `record_date` DATE COMMENT '日期',
    `category` VARCHAR(100) COMMENT '类别',
    `material_name` VARCHAR(200) COMMENT '物料名称',
    `spec_model` VARCHAR(300) COMMENT '规格型号',
    `material_code` VARCHAR(100) COMMENT '物料编码',
    `material_serial` VARCHAR(100) COMMENT '物料序列号',
    `quantity` INT COMMENT '数量',
    `unit` VARCHAR(20) COMMENT '单位',
    `brand` VARCHAR(100) COMMENT '品牌',
    `product_attr` VARCHAR(20) COMMENT '产品属性: 新品/维修品/免费',
    `factory` VARCHAR(100) COMMENT '厂房',
    `shipment_no` VARCHAR(100) COMMENT '出厂单号/送货单号',
    `remark` TEXT COMMENT '备注',
    `year_month` VARCHAR(20) COMMENT '年+月（格式: FYyyMM，如FY2607）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    INDEX `idx_dr_date` (`record_date`),
    INDEX `idx_dr_mcode` (`material_code`),
    INDEX `idx_dr_cat` (`category`),
    INDEX `idx_dr_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货记录';

-- ============================================================
-- 9. 物料表 material
-- ============================================================
CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1',
    `category` VARCHAR(100) COMMENT '类别',
    `material_name` VARCHAR(200) COMMENT '物料名称',
    `spec_model` VARCHAR(300) COMMENT '规格型号',
    `material_code` VARCHAR(100) COMMENT '物料编码',
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    INDEX `idx_m_mcode` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料表';

-- ============================================================
-- 10. 原始记录表（维修工单） original_record
-- ============================================================
CREATE TABLE IF NOT EXISTS `original_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `year_month` VARCHAR(20) COMMENT '年+月（FYyyMM格式）',
    `record_date` DATE COMMENT '日期',
    `shift` VARCHAR(10) COMMENT '班次: 白班/夜班',
    `factory` VARCHAR(100) COMMENT '厂房',
    `serial_number` VARCHAR(100) COMMENT '序号',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `diagnostician` VARCHAR(50) COMMENT '诊断人',
    `repair_person` VARCHAR(50) COMMENT '维修人',
    `repair_request_time` DATETIME COMMENT '报修时间',
    `start_time` DATETIME COMMENT '开始维修时间',
    `end_time` DATETIME COMMENT '结束时间',
    `repair_hours` DECIMAL(10,2) COMMENT '维修工时（分钟数）',
    `downtime_hours` DECIMAL(10,2) COMMENT '停机工时（分钟数）',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `fault_phenomenon` VARCHAR(500) COMMENT '故障现象',
    `fault_description` TEXT COMMENT '维修描述',
    `material_code` VARCHAR(100) COMMENT '物料编码/料号',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `quantity` INT COMMENT '数量',
    `machine_on_material` VARCHAR(200) COMMENT '上机物料号',
    `machine_off_material` VARCHAR(200) COMMENT '下机物料号',
    `remark` TEXT COMMENT '备注',
    `confirmer` VARCHAR(50) COMMENT '确认人',
    `delivery_record_ref` VARCHAR(200) COMMENT '送货记录引用',
    `document_no` VARCHAR(100) DEFAULT NULL COMMENT '单据号',
    `last_machine_on_time` DATE COMMENT '上次上机时间',
    `is_out_of_warranty` VARCHAR(10) COMMENT '是否过保: 未过保/已过保/无',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    INDEX `idx_or_date` (`record_date`),
    INDEX `idx_or_mcode` (`material_code`),
    INDEX `idx_or_sn` (`serial_number`),
    INDEX `idx_or_on` (`machine_on_material`),
    INDEX `idx_or_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原始记录（维修工单）';

-- ============================================================
-- 11. 上机物料表 machine_material
-- ============================================================
CREATE TABLE IF NOT EXISTS `machine_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `year_month` VARCHAR(20) COMMENT '年+月（FYyyMM格式）',
    `record_date` DATE COMMENT '日期',
    `shift` VARCHAR(10) COMMENT '班次',
    `factory` VARCHAR(100) COMMENT '厂房',
    `serial_number` VARCHAR(100) COMMENT '序号',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `repair_person` VARCHAR(50) COMMENT '维修人',
    `repair_request_time` DATETIME COMMENT '报修时间',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `repair_hours` DECIMAL(10,2) COMMENT '维修工时',
    `downtime_hours` DECIMAL(10,2) COMMENT '停机工时',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `fault_phenomenon` VARCHAR(500) COMMENT '故障现象',
    `fault_description` TEXT COMMENT '维修描述',
    `material_code` VARCHAR(100) COMMENT '物料编码/料号',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `quantity` INT COMMENT '数量',
    `machine_on_material` VARCHAR(200) COMMENT '上机物料号',
    `machine_off_material` VARCHAR(200) COMMENT '下机物料号',
    `remark` TEXT COMMENT '备注',
    `confirmer` VARCHAR(50) COMMENT '确认人',
    `delivery_record_ref` VARCHAR(200) COMMENT '送货记录引用',
    `last_machine_on_time` DATE COMMENT '上次上机时间',
    `is_out_of_warranty` VARCHAR(10) COMMENT '是否过保',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    INDEX `idx_mm_date` (`record_date`),
    INDEX `idx_mm_mcode` (`material_code`),
    INDEX `idx_mm_on` (`machine_on_material`),
    INDEX `idx_mm_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上机物料';

-- ============================================================
-- 12. 送货超比统计主表 delivery_stats
-- ============================================================
CREATE TABLE IF NOT EXISTS `delivery_stats` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `category` VARCHAR(100) COMMENT '类别',
    `material_code` VARCHAR(100) COMMENT '物料编码/料号',
    `system_name` VARCHAR(200) COMMENT '系统名称',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `unit_usage` DECIMAL(10,4) COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) COMMENT '比例（0~1小数，如0.15=15%）',
    `unit_price_with_tax` DECIMAL(12,4) COMMENT '含税单价',
    `machine_count` INT COMMENT '机台数',
    `delivery_quantity` INT COMMENT '送货数量',
    `machine_on_quantity` INT COMMENT '上机数量',
    `month_repair` INT COMMENT '当月返修',
    `agreed_ratio_quantity` DECIMAL(12,4) COMMENT '约定比例数量（自动计算）',
    `excess_quantity` DECIMAL(12,4) COMMENT '超比数量合计（自动计算）',
    `excess_amount_with_tax` DECIMAL(14,2) COMMENT '超比含税金额合计（自动计算）',
    `stat_date` DATE COMMENT '统计日期',
    `year_month` VARCHAR(20) COMMENT '年月（yyyy-MM格式，如2026-07）',
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_ds_mcode` (`material_code`),
    INDEX `idx_ds_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货超比统计';

-- ============================================================
-- 13. 送货超比统计每日明细 delivery_stats_daily
-- ============================================================
CREATE TABLE IF NOT EXISTS `delivery_stats_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stat_id` BIGINT NOT NULL COMMENT '关联 delivery_stats.id',
    `day_number` INT NOT NULL COMMENT '日期: 1-31',
    `value` DECIMAL(12,4) DEFAULT NULL COMMENT '当日送货数量',
    INDEX `idx_dsd_sid` (`stat_id`),
    FOREIGN KEY (`stat_id`) REFERENCES `delivery_stats`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货超比统计每日明细';

-- ============================================================
-- 14. 结算机台数表 settlement_machine
-- ============================================================
CREATE TABLE IF NOT EXISTS `settlement_machine` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `material_code` VARCHAR(100) COMMENT '物料编码',
    `category` VARCHAR(100) COMMENT '类别',
    `part_name` VARCHAR(200) COMMENT '配件名称',
    `unit_usage` DECIMAL(10,4) COMMENT '单台机用量',
    `ratio` DECIMAL(10,4) COMMENT '比例（0~1小数）',
    `unit_price_with_tax` DECIMAL(12,4) COMMENT '含税单价',
    `warranty_period` VARCHAR(50) COMMENT '质保期',
    `price_type` VARCHAR(50) COMMENT '价格类型',
    `remark` TEXT COMMENT '备注',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `settlement_machine_count` INT COMMENT '结算机台数量',
    `stat_month` VARCHAR(7) DEFAULT NULL COMMENT '统计月份（yyyy-MM格式）',
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_sm_mcode` (`material_code`),
    INDEX `idx_sm_model` (`machine_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算机台数';

-- ============================================================
-- 15. 机型明细表 machine_detail
-- ============================================================
CREATE TABLE IF NOT EXISTS `machine_detail` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `factory` VARCHAR(100) COMMENT '厂房',
    `machine_no` VARCHAR(100) COMMENT '机台号',
    `machine_brand` VARCHAR(100) COMMENT '机台品牌',
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机型明细';

-- ============================================================
-- 16. 开机数量表 machine_count
-- ============================================================
CREATE TABLE IF NOT EXISTS `machine_count` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` BIGINT DEFAULT '1' COMMENT '公司ID',
    `machine_model` VARCHAR(100) COMMENT '机型',
    `count` INT COMMENT '开机数量',
    `ratio_pct` DECIMAL(6,2) COMMENT '占比（0~100，如50.00=50%）',
    `stat_month` VARCHAR(10) COMMENT '统计月份',
    `remark` VARCHAR(255) COMMENT '备注',
    `created_by` VARCHAR(50) DEFAULT NULL,
    `updated_by` VARCHAR(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开机数量';

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认公司
INSERT IGNORE INTO `company` (`id`, `name`) VALUES (1, '默认公司');

-- 默认配置
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
    ('scheduler.cron', '0 0 3 * * *', '超比统计定时任务cron表达式');

-- 管理员账号（手机验证码登录）
-- 通过 /api/auth/register 注册，管理员在后台设置 role='admin'
-- INSERT IGNORE INTO `sys_user` (`username`, `password`, `real_name`, `role`) VALUES
--     ('13800138000', '', '管理员', 'admin');

-- ============================================================
-- 字段约定
-- ============================================================
-- 1. 百分比字段（ratio）统一存储 0~1 小数
--    例外: machine_count.ratio_pct 存储 0~100 百分比值
-- 2. original_record / machine_material 的 repair_hours 存储的是分钟数
-- 3. 时间字段（created_at, updated_at）由数据库自动维护
-- 4. base_material_156 唯一约束: (material_code, company_id)
-- 5. 外键仅 delivery_stats_daily.stat_id → delivery_stats.id（级联删除）
-- 6. year_month 格式因表而异:
--    - delivery_record, original_record, machine_material: FYyyMM（如FY2607）
--    - delivery_stats: yyyy-MM（如2026-07）
--    - settlement_machine.stat_month: yyyy-MM
--    - machine_count.stat_month: 自由格式
