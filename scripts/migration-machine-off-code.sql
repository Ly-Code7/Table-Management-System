-- 维修记录新增"下机料号"列（与已有"下机物料号" machine_off_material 并存）
-- 下机料号 = 填写下机物料号后模糊匹配送货记录，命中则回填送货记录的物料编码，未命中为空
ALTER TABLE `original_record`
  ADD COLUMN `machine_off_code` varchar(100) DEFAULT NULL COMMENT '下机料号（自动回填：下机物料号模糊匹配送货记录）' AFTER `machine_off_material`;
