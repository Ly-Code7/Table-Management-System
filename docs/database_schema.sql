-- ============================================================
-- 金属厂数据管理系统 数据库规范
-- 数据库名: metal_system
-- 字符集: utf8mb4 / utf8mb4_0900_ai_ci
-- 引擎: InnoDB
-- 导出时间: 2026-07-21
-- ============================================================

CREATE DATABASE IF NOT EXISTS metal_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE metal_system;

-- ============================================================
-- 1. 公司表
-- ============================================================
CREATE TABLE `company` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '公司名称',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司';

-- ============================================================
-- 2. 用户表
-- ============================================================
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录名',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt加密密码',
  `real_name` varchar(100) DEFAULT NULL COMMENT '真实姓名',
  `role` varchar(20) NOT NULL DEFAULT 'user' COMMENT '角色: admin / user',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

-- ============================================================
-- 3. 系统配置表
-- ============================================================
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) NOT NULL COMMENT '配置值',
  `description` varchar(200) DEFAULT NULL COMMENT '说明',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置';

-- 默认数据
INSERT INTO `sys_config` VALUES (1,'scheduler.cron','0 0 3 * * *','超比统计定时任务cron表达式',NOW());

-- ============================================================
-- 4. 156项（基础物料156项）
-- ============================================================
CREATE TABLE `base_material_156` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `category` varchar(100) DEFAULT NULL COMMENT '类别',
  `material_code` varchar(100) NOT NULL COMMENT '料号',
  `system_name` varchar(200) DEFAULT NULL COMMENT '系统名称',
  `part_name` varchar(200) DEFAULT NULL COMMENT '配件名称',
  `unit_usage` decimal(10,4) DEFAULT NULL COMMENT '单台机用量',
  `ratio` decimal(10,4) DEFAULT NULL COMMENT '比例（存储0~1小数，如0.15=15%）',
  `unit_price_with_tax` decimal(12,4) DEFAULT NULL COMMENT '含税单价',
  `created_by` varchar(50) DEFAULT NULL,
  `updated_by` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_b156_mcode_company` (`material_code`,`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础物料156项';

-- ============================================================
-- 5. 送货记录
-- ============================================================
CREATE TABLE `delivery_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `record_date` date DEFAULT NULL COMMENT '日期',
  `category` varchar(100) DEFAULT NULL COMMENT '类别',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `spec_model` varchar(300) DEFAULT NULL COMMENT '规格型号',
  `material_code` varchar(100) DEFAULT NULL COMMENT '物料编码',
  `material_serial` varchar(100) DEFAULT NULL COMMENT '物料序列号',
  `quantity` int DEFAULT NULL COMMENT '数量',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `brand` varchar(100) DEFAULT NULL COMMENT '品牌',
  `product_attr` varchar(20) DEFAULT NULL COMMENT '产品属性: 新品/维修品',
  `factory` varchar(100) DEFAULT NULL COMMENT '厂房',
  `shipment_no` varchar(100) DEFAULT NULL COMMENT '送货单号/出厂单号',
  `remark` text COMMENT '备注',
  `year_month` varchar(20) DEFAULT NULL COMMENT '年月(FY2607格式)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(50) DEFAULT NULL,
  `updated_by` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_dr_date` (`record_date`),
  KEY `idx_dr_mcode` (`material_code`),
  KEY `idx_dr_cat` (`category`),
  KEY `idx_dr_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='送货记录';

-- ============================================================
-- 6. 原始记录（维修工单）
-- ============================================================
CREATE TABLE `original_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `year_month` varchar(20) DEFAULT NULL COMMENT '年月(FY2607)',
  `record_date` date DEFAULT NULL COMMENT '日期',
  `shift` varchar(10) DEFAULT NULL COMMENT '班次: 白班/夜班',
  `factory` varchar(100) DEFAULT NULL COMMENT '厂房',
  `serial_number` varchar(100) DEFAULT NULL COMMENT '序号',
  `machine_no` varchar(100) DEFAULT NULL COMMENT '机台号',
  `plant_machine` varchar(100) DEFAULT NULL COMMENT '厂房+机台号（自动计算：厂房-机台号）',
  `diagnostician` varchar(50) DEFAULT NULL COMMENT '诊断人',
  `repair_person` varchar(50) DEFAULT NULL COMMENT '维修人',
  `repair_request_time` datetime DEFAULT NULL COMMENT '报修时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始维修时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间（跨天自动加1天）',
  `repair_hours` decimal(10,2) DEFAULT NULL COMMENT '维修工时（分钟数）',
  `downtime_hours` decimal(10,2) DEFAULT NULL COMMENT '停机工时（分钟数）',
  `machine_model` varchar(100) DEFAULT NULL COMMENT '机型',
  `fault_phenomenon` varchar(500) DEFAULT NULL COMMENT '故障现象',
  `fault_description` text COMMENT '维修描述',
  `material_code` varchar(100) DEFAULT NULL COMMENT '物料编码/料号',
  `material_156_name` varchar(200) DEFAULT NULL COMMENT '156项名称',
  `part_name` varchar(200) DEFAULT NULL COMMENT '配件名称',
  `quantity` int DEFAULT NULL COMMENT '数量',
  `machine_on_material` varchar(200) DEFAULT NULL COMMENT '上机物料号',
  `machine_off_material` varchar(200) DEFAULT NULL COMMENT '下机物料号',
  `machine_off_code` varchar(100) DEFAULT NULL COMMENT '下机料号（自动回填：下机物料号模糊匹配送货记录）',
  `remark` text COMMENT '备注',
  `confirmer` varchar(50) DEFAULT NULL COMMENT '确认人',
  `delivery_record_ref` varchar(200) DEFAULT NULL COMMENT '送货记录引用',
  `image_key` varchar(255) DEFAULT NULL COMMENT '维修图片 OSS object key（私有读，经签名 URL 访问）',
  `last_machine_on_time` date DEFAULT NULL COMMENT '上次上机时间（自动查询）',
  `is_out_of_warranty` varchar(10) DEFAULT NULL COMMENT '是否过保: 未过保/已过保/无',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(50) DEFAULT NULL,
  `updated_by` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_or_date` (`record_date`),
  KEY `idx_or_mcode` (`material_code`),
  KEY `idx_or_sn` (`serial_number`),
  KEY `idx_or_on` (`machine_on_material`),
  KEY `idx_or_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始记录（维修工单）';

-- ============================================================
-- 7. 上机物料
-- ============================================================
CREATE TABLE `machine_material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `year_month` varchar(20) DEFAULT NULL COMMENT '年月(FY2607)',
  `record_date` date DEFAULT NULL,
  `shift` varchar(10) DEFAULT NULL COMMENT '班次',
  `factory` varchar(100) DEFAULT NULL COMMENT '厂房',
  `serial_number` varchar(100) DEFAULT NULL COMMENT '序号',
  `machine_no` varchar(100) DEFAULT NULL COMMENT '机台号',
  `repair_person` varchar(50) DEFAULT NULL COMMENT '维修人',
  `repair_request_time` datetime DEFAULT NULL COMMENT '报修时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `repair_hours` decimal(10,2) DEFAULT NULL COMMENT '维修工时',
  `downtime_hours` decimal(10,2) DEFAULT NULL COMMENT '停机工时',
  `machine_model` varchar(100) DEFAULT NULL COMMENT '机型',
  `fault_phenomenon` varchar(500) DEFAULT NULL COMMENT '故障现象',
  `fault_description` text COMMENT '维修描述',
  `material_code` varchar(100) DEFAULT NULL COMMENT '物料编码',
  `part_name` varchar(200) DEFAULT NULL COMMENT '配件名称',
  `quantity` int DEFAULT NULL COMMENT '数量',
  `machine_on_material` varchar(200) DEFAULT NULL COMMENT '上机物料号',
  `machine_off_material` varchar(200) DEFAULT NULL COMMENT '下机物料号',
  `remark` text COMMENT '备注',
  `confirmer` varchar(50) DEFAULT NULL COMMENT '确认人',
  `delivery_record_ref` varchar(200) DEFAULT NULL COMMENT '送货记录引用',
  `last_machine_on_time` date DEFAULT NULL,
  `is_out_of_warranty` varchar(10) DEFAULT NULL COMMENT '是否过保',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(50) DEFAULT NULL,
  `updated_by` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mm_date` (`record_date`),
  KEY `idx_mm_mcode` (`material_code`),
  KEY `idx_mm_on` (`machine_on_material`),
  KEY `idx_mm_off` (`machine_off_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上机物料';

-- ============================================================
-- 8. 送货超比统计
-- ============================================================
CREATE TABLE `delivery_stats` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `category` varchar(100) DEFAULT NULL COMMENT '类别',
  `material_code` varchar(100) DEFAULT NULL COMMENT '物料编码',
  `system_name` varchar(200) DEFAULT NULL COMMENT '系统名称',
  `part_name` varchar(200) DEFAULT NULL COMMENT '配件名称',
  `unit_usage` decimal(10,4) DEFAULT NULL COMMENT '单台机用量',
  `ratio` decimal(10,4) DEFAULT NULL COMMENT '比例（存储0~1小数，如0.15=15%）',
  `unit_price_with_tax` decimal(12,4) DEFAULT NULL COMMENT '含税单价',
  `machine_count` int DEFAULT NULL COMMENT '机台数',
  `delivery_quantity` int DEFAULT NULL COMMENT '送货数量',
  `machine_on_quantity` int DEFAULT NULL COMMENT '上机数量',
  `month_repair` int DEFAULT NULL COMMENT '当月返修',
  `agreed_ratio_quantity` decimal(12,4) DEFAULT NULL COMMENT '约定比例数量（自动计算）',
  `excess_quantity` decimal(12,4) DEFAULT NULL COMMENT '超比数量合计（自动计算）',
  `excess_amount_with_tax` decimal(14,4) DEFAULT NULL COMMENT '超比含税金额合计（自动计算）',
  `stat_date` date DEFAULT NULL COMMENT '统计日期',
  `year_month` varchar(20) DEFAULT NULL COMMENT '年月(2026-07格式)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(100) DEFAULT NULL,
  `updated_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ds_mcode` (`material_code`),
  KEY `idx_ds_ym` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='送货超比统计';

-- ============================================================
-- 9. 送货超比统计 — 每日明细
-- ============================================================
CREATE TABLE `delivery_stats_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_id` bigint NOT NULL COMMENT '关联 delivery_stats.id',
  `day_number` int NOT NULL COMMENT '日: 1-31',
  `value` decimal(12,4) DEFAULT NULL COMMENT '当日送货数量',
  PRIMARY KEY (`id`),
  KEY `idx_dsd_sid` (`stat_id`),
  CONSTRAINT `delivery_stats_daily_ibfk_1` FOREIGN KEY (`stat_id`) REFERENCES `delivery_stats` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='送货超比统计每日明细';

-- ============================================================
-- 10. 结算机台数
-- ============================================================
CREATE TABLE `settlement_machine` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `material_code` varchar(100) DEFAULT NULL COMMENT '物料编码',
  `category` varchar(100) DEFAULT NULL COMMENT '类别',
  `part_name` varchar(200) DEFAULT NULL COMMENT '配件名称',
  `unit_usage` decimal(10,4) DEFAULT NULL COMMENT '单台机用量',
  `ratio` decimal(10,4) DEFAULT NULL COMMENT '比例（存储0~1小数，如0.15=15%）',
  `unit_price_with_tax` decimal(12,4) DEFAULT NULL COMMENT '含税单价',
  `warranty_period` varchar(50) DEFAULT NULL COMMENT '保修期',
  `price_type` varchar(50) DEFAULT NULL COMMENT '价格类型',
  `remark` text COMMENT '备注',
  `machine_model` varchar(100) DEFAULT NULL COMMENT '机型',
  `settlement_machine_count` int DEFAULT NULL COMMENT '结算机台数量',
  `stat_month` varchar(7) DEFAULT NULL COMMENT '统计月份(格式yyyy-MM)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(100) DEFAULT NULL,
  `updated_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sm_mcode` (`material_code`),
  KEY `idx_sm_model` (`machine_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算机台数';

-- ============================================================
-- 11. 机型明细
-- ============================================================
CREATE TABLE `machine_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `factory` varchar(100) DEFAULT NULL COMMENT '厂房',
  `machine_no` varchar(100) DEFAULT NULL COMMENT '机台号',
  `machine_brand` varchar(100) DEFAULT NULL COMMENT '机台品牌',
  `created_by` varchar(100) DEFAULT NULL,
  `updated_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机型明细';

-- ============================================================
-- 12. 开机数量
-- ============================================================
CREATE TABLE `machine_count` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `machine_model` varchar(100) DEFAULT NULL COMMENT '机型',
  `count` int DEFAULT NULL COMMENT '开机数量',
  `ratio_pct` decimal(6,2) DEFAULT NULL COMMENT '占比（0~100范围，如50.00=50%）',
  `stat_month` varchar(10) DEFAULT NULL COMMENT '统计月份',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(100) DEFAULT NULL,
  `updated_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='开机数量';

-- ============================================================
-- 13. 物料表
-- ============================================================
CREATE TABLE `material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `category` varchar(100) DEFAULT NULL COMMENT '类别',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `spec_model` varchar(300) DEFAULT NULL COMMENT '规格型号',
  `material_code` varchar(100) DEFAULT NULL COMMENT '物料编码',
  `created_by` varchar(100) DEFAULT NULL,
  `updated_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_m_mcode` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料表';

-- ============================================================
-- 14. 操作日志
-- ============================================================
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT '1',
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `action` varchar(20) NOT NULL COMMENT '操作类型: INSERT/UPDATE/DELETE',
  `table_name` varchar(50) NOT NULL COMMENT '操作表名',
  `record_id` bigint DEFAULT NULL COMMENT '操作记录ID',
  `detail` text COMMENT '操作详情(JSON)',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ol_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

-- ============================================================
-- 15. OCR 调用日志（新增）
-- ============================================================
CREATE TABLE `ocr_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_type` varchar(50) NOT NULL COMMENT '表类型: original-record / delivery-record',
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(100) DEFAULT NULL COMMENT '操作人用户名',
  `image_size` bigint DEFAULT NULL COMMENT '图片字节数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OCR调用日志';

-- ============================================================
-- 16. 用户在线状态（新增）
-- ============================================================
CREATE TABLE `user_online` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `last_active_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户在线状态';

-- ============================================================
-- 注意事项
-- ============================================================
-- 1. 所有百分比字段（ratio）统一存储 0~1 小数
--    例外: machine_count.ratio_pct 存储 0~100 百分比值
-- 2. original_record.repair_hours 和 downtime_hours 存储的是分钟数，不是小时
-- 3. 所有时间字段（created_at, updated_at）由数据库自动维护
-- 4. base_material_156 唯一约束是 (material_code, company_id) 联合约束
--    不同公司可以有相同的料号
-- 5. 外键仅 delivery_stats_daily.stat_id → delivery_stats.id (级联删除)
