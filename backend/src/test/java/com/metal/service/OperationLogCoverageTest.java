package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.MachineCount;
import com.metal.entity.OperationLog;
import com.metal.entity.OriginalRecord;
import com.metal.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 操作日志覆盖验证：
 * 1. 单条 create/update/delete 后 operation_log 出现对应 action，且 record_id = 被操作记录主键、company_id 正确
 * 2. clearByMonth 记 DELETE 日志（detail 含月份/条数，recordId 为 null）
 * 3. Excel 导入不写日志（用户决策：避免上万条导入放大日志写入开销）
 */
@SpringBootTest
@Transactional
class OperationLogCoverageTest {

    @Autowired
    private OriginalRecordService originalRecordService;

    @Autowired
    private MachineCountService machineCountService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    private List<OperationLog> findLogs(String tableName, String action) {
        return operationLogMapper.search(null, tableName, action, null, null, 0, 100);
    }

    private OriginalRecord buildRecord() {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setRecordDate(LocalDate.of(2026, 8, 1));
        r.setFactory("B5");
        r.setMachineNo("H1");
        r.setShift("白班");
        r.setSerialNumber("001");
        r.setRepairPerson("测试维修人");
        r.setMachineModel("FANUC");
        r.setFaultPhenomenon("异响");
        r.setFaultDescription("换件");
        r.setMaterialCode("MC-LOG-TEST");
        r.setPartName("测试配件");
        r.setQuantity(0); // 0 不触发未过保物料下推，聚焦日志断言
        return r;
    }

    @Test
    void create_shouldWriteInsertLogWithRecordIdAndCompanyId() {
        OriginalRecord created = originalRecordService.create(buildRecord());
        assertNotNull(created.getId(), "insert 后主键应回填");

        List<OperationLog> logs = findLogs("original_record", "INSERT");
        assertFalse(logs.isEmpty(), "create 后应产生 INSERT 日志");
        OperationLog log = logs.get(0);
        assertEquals(created.getId(), log.getRecordId(), "record_id 应等于新记录主键");
        assertEquals(1L, log.getCompanyId(), "company_id 应为记录所属公司");
    }

    @Test
    void update_shouldWriteUpdateLogWithRecordId() {
        OriginalRecord created = originalRecordService.create(buildRecord());
        created.setRemark("修改后的备注");
        originalRecordService.update(created);

        List<OperationLog> logs = findLogs("original_record", "UPDATE");
        assertFalse(logs.isEmpty(), "update 后应产生 UPDATE 日志");
        OperationLog log = logs.get(0);
        assertEquals(created.getId(), log.getRecordId(), "record_id 应等于被修改记录主键");
        assertNotNull(log.getDetail(), "UPDATE 日志应带 detail");
    }

    @Test
    void delete_shouldWriteDeleteLogWithRecordId() {
        OriginalRecord created = originalRecordService.create(buildRecord());
        Long id = created.getId();
        originalRecordService.delete(id);

        List<OperationLog> logs = findLogs("original_record", "DELETE");
        assertFalse(logs.isEmpty(), "delete 后应产生 DELETE 日志");
        OperationLog log = logs.get(0);
        assertEquals(id, log.getRecordId(), "record_id 应等于被删除记录主键");
        assertNull(log.getDetail(), "DELETE 日志不记 detail（与既有行为一致）");
    }

    @Test
    void clearByMonth_shouldWriteDeleteLogWithMonthInDetail() {
        int deleted = machineCountService.clearByMonth("2026-08", 1L);
        assertTrue(deleted >= 0);

        List<OperationLog> logs = findLogs("machine_count", "DELETE");
        assertFalse(logs.isEmpty(), "clearByMonth 后应产生 DELETE 日志");
        OperationLog log = logs.get(0);
        assertNull(log.getRecordId(), "按月份批量删除无单条主键，record_id 应为 null");
        assertNotNull(log.getDetail(), "detail 应含月份与删除条数");
        assertTrue(log.getDetail().contains("2026-08"), "detail 应含统计月份，实际: " + log.getDetail());
    }

    @Test
    void importExcel_shouldNotWriteInsertLogs() {
        // 先清掉历史日志干扰：仅断言导入前后 original_record 的 INSERT 日志条数不增加
        int before = findLogs("original_record", "INSERT").size();

        MockMultipartFile file = buildImportFile(100);
        ImportResultDTO res = originalRecordService.importExcel(file, 1L);
        assertEquals(100, res.getSuccess(), "100 行应全部导入成功");

        int after = findLogs("original_record", "INSERT").size();
        assertEquals(before, after, "导入不写日志：INSERT 日志条数不应增加（实际增加 " + (after - before) + " 条）");
    }

    private MockMultipartFile buildImportFile(int rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            data.add(List.of(
                    "FY2608", "B5-H1", "2026-08-01", "白班", "B5", String.format("%03d", i), "H1", "张", "李",
                    "2026-08-01 08:00:00", "2026-08-01 08:30:00", "2026-08-01 09:00:00", 60, 30, "FANUC", "异响",
                    "换件", "MC-LOG-" + i, "名称", "配件", 0, "ON", "OFF", "备注", "王", "2", "DOC" + i));
        }
        List<String> head = List.of(
                "年+月", "厂房+机台", "日期", "班次", "厂房", "序号", "机台号", "诊断人", "维修人",
                "报修时间", "开始时间", "结束时间", "维修工时", "停机工时", "机型", "故障现象",
                "维修描述", "物料编码", "156项名称", "零件名称", "数量", "上机物料", "下机物料",
                "备注", "确认人", "送货记录引用", "单据号");
        EasyExcel.write(bos).head(head.stream().map(h -> List.of(h)).toList())
                .sheet("维修记录").doWrite(data);
        return new MockMultipartFile("file", "log-coverage.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }
}
