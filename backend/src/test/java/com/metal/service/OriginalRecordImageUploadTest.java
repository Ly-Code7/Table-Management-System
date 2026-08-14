package com.metal.service;

import com.metal.common.BizException;
import com.metal.entity.OriginalRecord;
import com.metal.mapper.OriginalRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维修记录图片上传：
 * - 上传接口服务端校验（非图片类型 / 超 10MB 拒绝）——不依赖真实 OSS
 * - 记录 imageKey 字段随 insert/update 入库——真实数据库事务
 * （真实 OSS 上传/签名读回已由连接探针单独验证，见交付报告）
 */
@SpringBootTest
@Transactional
class OriginalRecordImageUploadTest {

    @Autowired
    private OssService ossService;

    @Autowired
    private OriginalRecordMapper mapper;

    @Test
    void uploadRejectsNonImageType() {
        MockMultipartFile txt = new MockMultipartFile("file", "a.txt",
                "text/plain", "hello".getBytes());
        BizException e = assertThrows(BizException.class, () -> ossService.upload(txt));
        assertTrue(e.getMessage().contains("jpg/png"), "应提示仅支持 jpg/png，实际: " + e.getMessage());
    }

    @Test
    void uploadRejectsOversize() {
        byte[] big = new byte[11 * 1024 * 1024];
        MockMultipartFile img = new MockMultipartFile("file", "big.png",
                "image/png", big);
        BizException e = assertThrows(BizException.class, () -> ossService.upload(img));
        assertTrue(e.getMessage().contains("10MB"), "应提示超 10MB，实际: " + e.getMessage());
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.png",
                "image/png", new byte[0]);
        BizException e = assertThrows(BizException.class, () -> ossService.upload(empty));
        assertTrue(e.getMessage().contains("选择"), "应提示选择图片，实际: " + e.getMessage());
    }

    @Test
    void signUrlRejectsKeyOutsidePrefix() {
        BizException e = assertThrows(BizException.class, () -> ossService.signUrl("other-bucket/abc.jpg"));
        assertTrue(e.getMessage().contains("非法"), "应拒绝非 original-record/ 前缀 key，实际: " + e.getMessage());
        // null key 同样拒绝
        assertThrows(BizException.class, () -> ossService.signUrl(null));
    }

    @Test
    void recordImageKeyPersistsThroughInsertAndUpdate() {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setYearMonth("FY2608");
        r.setRecordDate(java.time.LocalDate.of(2026, 8, 8));
        r.setFactory("A");
        r.setMachineNo("M01");
        r.setImageKey("original-record/20260808/unit-test-key.jpg");

        mapper.insert(r);
        assertNotNull(r.getId());
        OriginalRecord loaded = mapper.findById(r.getId());
        assertEquals("original-record/20260808/unit-test-key.jpg", loaded.getImageKey(),
                "insert 后 image_key 应可读回");

        // update 换图
        r.setImageKey("original-record/20260808/unit-test-key-2.jpg");
        mapper.update(r);
        OriginalRecord updated = mapper.findById(r.getId());
        assertEquals("original-record/20260808/unit-test-key-2.jpg", updated.getImageKey(),
                "update 后 image_key 应可读回");
    }
}
