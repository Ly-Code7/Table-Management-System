-- 维修记录图片上传：original_record 表新增 image_key 列（存 OSS object key，展示时经 image-url 接口签临时 URL）
ALTER TABLE original_record
    ADD COLUMN `image_key` VARCHAR(255) DEFAULT NULL COMMENT '维修图片 OSS object key（私有读，经签名 URL 访问）' AFTER `document_no`;
