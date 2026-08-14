package com.metal.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.metal.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云 OSS 存储服务（维修记录图片上传）
 * 私有读：上传后返回 object key，展示时按 key 生成临时签名 URL（1 小时有效）。
 * key 规则：original-record/{yyyyMMdd}/{UUID}.{ext}，按日期分目录便于管理。
 */
@Service
public class OssService {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);

    /** 允许的图片类型（服务端校验，不信任前端） */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final long SIGN_URL_EXPIRE_MS = 60 * 60 * 1000; // 1 小时
    /** key 白名单前缀：image-url 接口只允许签这个目录下的对象 */
    private static final String KEY_PREFIX = "original-record/";

    private final String endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String accessKeySecret;

    public OssService(@Value("${oss.endpoint}") String endpoint,
                      @Value("${oss.bucket}") String bucket,
                      @Value("${oss.access-key-id}") String accessKeyId,
                      @Value("${oss.access-key-secret}") String accessKeySecret) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    private OSS client() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    /**
     * 上传图片到 OSS，返回 object key。
     * recordId 非 null 时以记录 id 命名（original-record/{yyyyMMdd}/{recordId}.{ext}，
     * 便于按维修记录定位图片）；为 null 时用 UUID 命名（向后兼容）。
     */
    public String upload(MultipartFile file, Long recordId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BizException("仅支持 jpg/png 格式图片");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BizException("图片大小不能超过 10MB");
        }
        String ext = "image/png".equals(contentType) ? "png" : "jpg";
        String key = buildKey(ext, recordId);

        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType);
            meta.setContentLength(file.getSize());
            OSS oss = client();
            try {
                oss.putObject(bucket, key, in, meta);
            } finally {
                oss.shutdown();
            }
            log.info("OSS 上传成功: bucket={}, key={}, size={}", bucket, key, file.getSize());
            return key;
        } catch (IOException e) {
            log.error("OSS 上传读取文件失败: {}", e.getMessage(), e);
            throw new BizException("图片读取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("OSS 上传失败: {}", e.getMessage(), e);
            throw new BizException("图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 生成 OSS object key。recordId 非空时以记录 id 命名（便于按维修记录定位），否则 UUID。
     * 包级可见便于单测（不触发真实 OSS 上传）。
     */
    String buildKey(String ext, Long recordId) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return recordId != null
                ? KEY_PREFIX + dateDir + "/" + recordId + "." + ext
                : KEY_PREFIX + dateDir + "/" + UUID.randomUUID() + "." + ext;
    }

    /**
     * 生成私有读签名 URL（1 小时有效）
     */
    public String signUrl(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            throw new BizException("非法的图片 key");
        }
        OSS oss = client();
        try {
            Date expire = new Date(System.currentTimeMillis() + SIGN_URL_EXPIRE_MS);
            return oss.generatePresignedUrl(bucket, key, expire).toString();
        } catch (Exception e) {
            log.error("OSS 签名 URL 生成失败: key={}, {}", key, e.getMessage(), e);
            throw new BizException("图片地址生成失败: " + e.getMessage());
        } finally {
            oss.shutdown();
        }
    }

    /**
     * 删除 OSS 对象（换图/清理用）
     */
    public void delete(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            log.warn("OSS 删除跳过非法 key: {}", key);
            return;
        }
        OSS oss = client();
        try {
            oss.deleteObject(bucket, key);
            log.info("OSS 删除成功: key={}", key);
        } catch (Exception e) {
            // 删除失败不阻塞主流程，仅记日志（孤儿文件可后续清理）
            log.warn("OSS 删除失败: key={}, {}", key, e.getMessage());
        } finally {
            oss.shutdown();
        }
    }
}
