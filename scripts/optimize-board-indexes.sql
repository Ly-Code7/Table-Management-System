-- ============================================
-- 金属厂数据管理系统 - 数据看板聚合性能索引
-- 日期: 2026-08-04
-- 说明: 加速数据看板按 公司+年+月 维度的聚合查询。
--       已于 2026-08-04 直接在生产库执行，此脚本用于环境重建/迁移时复现。
--       注意: 当前数据量下优化器可能不选用（company_id 选择性低），属保险型索引。
-- 执行方式: Navicat 手动执行（与既有 migration 脚本一致）
-- ============================================

USE metal_system;

-- 未过保物料: 看板维修金额/物料频次/返修频次聚合（company_id + year_month + plant_machine）
ALTER TABLE `unwarranted_material`
    ADD INDEX `idx_uw_company_ym_plant` (`company_id`, `year_month`, `plant_machine`);

-- 维修记录: 看板故障频次/机台列表聚合（company_id + year_month）
ALTER TABLE `original_record`
    ADD INDEX `idx_or_company_ym` (`company_id`, `year_month`);

-- 验证
SHOW INDEX FROM `unwarranted_material`;
SHOW INDEX FROM `original_record`;
