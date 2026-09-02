-- ============================================
-- 金属厂数据管理系统 - 客户物料标记 迁移脚本
-- 日期: 2026-09-02
-- 内容:
--   1. original_record 新增 上机是否客户物料 / 下机是否客户物料
--   2. unwarranted_material 新增 上机判定(mount_judgement)
--   3. unwarranted_material.warranty_status 注释更新为 返修判定
-- ============================================

USE metal_system;

-- 1. 维修记录表：上机/下机是否客户物料（是/否）
ALTER TABLE `original_record`
    ADD COLUMN `machine_on_customer` VARCHAR(10) DEFAULT NULL COMMENT '上机是否客户物料（是/否）' AFTER `machine_on_material`,
    ADD COLUMN `machine_off_customer` VARCHAR(10) DEFAULT NULL COMMENT '下机是否客户物料（是/否）' AFTER `machine_off_material`;

-- 2. 未过保物料表：上机判定（下推自维修记录"上机是否客户物料"，为"是"时填"客户物料"）
ALTER TABLE `unwarranted_material`
    ADD COLUMN `mount_judgement` VARCHAR(20) DEFAULT NULL COMMENT '上机判定（客户物料/空，下推自维修记录上机是否客户物料）' AFTER `repair_material_on`;

-- 3. warranty_status 语义更新：返修判定（未过保/已过保/客户物料；客户物料不按上次上机日期判定过保）
ALTER TABLE `unwarranted_material`
    MODIFY COLUMN `warranty_status` VARCHAR(20) COMMENT '返修判定（未过保/已过保/客户物料；客户物料不按上次上机日期判定过保）';
