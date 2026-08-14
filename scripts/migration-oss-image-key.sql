-- 维修记录图片上传：original_record 表新增 image_key 列（存 OSS object key，展示时经 image-url 接口签临时 URL）
-- 注意：不要加 AFTER 子句指定列位置！MySQL 8.0 只有"追加到表末尾"才走 INSTANT 算法（秒级），
--      指定 AFTER 会退化为 INPLACE/COPY 全表重建，大表（数十万行）会长时间锁表/超时。
--      代码按列名引用 image_key，不依赖物理列位置。
ALTER TABLE original_record
    ADD COLUMN `image_key` VARCHAR(255) DEFAULT NULL COMMENT '维修图片 OSS object key（私有读，经签名 URL 访问）';
