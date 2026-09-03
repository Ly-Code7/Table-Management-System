package com.metal.service;

import com.metal.entity.OriginalRecord;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.mapper.OriginalRecordMapper;
import com.metal.mapper.UnwarrantedMaterialMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 链纠偏（2026-09 修复）：unique_id 链上某行被编辑/删除/新增导致"前序记录集合"变化时，
 * 同链上日期在它之后（或同日 id 更大）的行必须自动重算派生字段（返修判定/第几次/上次日期/上次维修人等），
 * 否则出现快照失真：如 1 月行编码改走后，6 月行仍挂着按旧前序算出的"未过保"。
 *
 * 集成测试连真实 MySQL，事务回滚不落库。构造同链：同 厂房+机台号+配件名（unique_id 派生键）。
 */
@SpringBootTest
@Transactional
class UnwarrantedMaterialChainRecalcTest {

    @Autowired
    private OriginalRecordService originalRecordService;

    @Autowired
    private OriginalRecordMapper originalRecordMapper;

    @Autowired
    private UnwarrantedMaterialService unwarrantedMaterialService;

    @Autowired
    private UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    private static final String FACTORY = "测试厂房";
    private static final String MACHINE = "T-CHAIN-01";
    private static final String MACHINE_OTHER = "T-CHAIN-02";

    /** 构造维修记录：同 key（厂房-机台号+配件名）用于构造同 unique_id 链条 */
    private OriginalRecord newRecord(String partName, LocalDate date) {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setRecordDate(date);
        r.setFactory(FACTORY);
        r.setMachineNo(MACHINE);
        r.setFaultDescription("链纠偏测试-处理方式");
        r.setMachineOnMaterial("TMP-CHAIN-ON");
        r.setRepairPerson("tester");
        r.setMaterialCode("TMP-CHAIN-MC");
        r.setMachineOffCode("TMP-CHAIN-OFF");
        r.setPartName(partName);
        r.setQuantity(1);
        return r;
    }

    /** 建一条同链记录（经维修记录下推，与生产路径一致），返回未过保物料行 */
    private UnwarrantedMaterial pushRecord(String partName, LocalDate date) {
        originalRecordService.create(newRecord(partName, date));
        return findByDate(partName, date);
    }

    /** 按日期过滤链上记录（search 反查后取指定日期那条） */
    private UnwarrantedMaterial findByDate(String partName, LocalDate date) {
        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, partName, null, null, null, null, "id", "desc");
        return hits.stream()
                .filter(u -> date.equals(u.getRecordDate()))
                .findFirst().orElse(null);
    }

    @Test
    void update_moveCodeAway_chainFollowersRecalc() {
        // 场景：1 月与 6 月同链，6 月行因距上次 <6 个月判"未过保"；
        // 把 1 月行编码（机台号）改走 → 6 月行应自动重算为无前序（返修判定空、第几次 1、上次信息清空）
        String part = "TMPCHAIN-MV-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial uwJun = pushRecord(part, LocalDate.of(2026, 6, 10));
        // 前置：6 月行保存时确实判了"未过保"（距 1 月 5 个月）
        assertEquals("未过保", uwJun.getWarrantyStatus());
        assertEquals(2, uwJun.getOccurrenceNo());
        assertEquals(LocalDate.of(2026, 1, 10), uwJun.getLastDate());

        // 把 1 月行机台号改走（unique_id 变化，脱离旧链）
        UnwarrantedMaterial uwJan = findByDate(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial edit = unwarrantedMaterialService.getById(uwJan.getId());
        edit.setMachineNo(MACHINE_OTHER);
        unwarrantedMaterialService.update(edit);

        // 6 月行自动纠偏：无前序 → 首修语义
        UnwarrantedMaterial after = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals("", after.getWarrantyStatus(), "旧链前序脱离后返修判定应清空（不再'未过保'）");
        assertEquals(1, after.getOccurrenceNo());
        assertNull(after.getLastDate());
        assertNull(after.getLastRepairPerson());
        assertEquals(1, after.getTotalCount());
        // 移走的行在新链上仍是首修，不受影响
        assertEquals(1, edit.getOccurrenceNo());
    }

    @Test
    void update_moveCodeBack_chainRecover() {
        // 反向：编码改回原链 → 6 月行应重新纳入前序（恢复"未过保"、第几次 2、上次日期 1 月）
        String part = "TMPCHAIN-BACK-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        pushRecord(part, LocalDate.of(2026, 6, 10));

        UnwarrantedMaterial uwJan = findByDate(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial edit = unwarrantedMaterialService.getById(uwJan.getId());
        edit.setMachineNo(MACHINE_OTHER);
        unwarrantedMaterialService.update(edit);
        // 前置：改走后 6 月行已清空
        UnwarrantedMaterial uwJun = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals("", uwJun.getWarrantyStatus());
        assertEquals(1, uwJun.getOccurrenceNo());

        // 改回原链
        edit = unwarrantedMaterialService.getById(uwJan.getId());
        edit.setMachineNo(MACHINE);
        unwarrantedMaterialService.update(edit);

        UnwarrantedMaterial after = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals("未过保", after.getWarrantyStatus(), "前序回归后应恢复'未过保'判定");
        assertEquals(2, after.getOccurrenceNo());
        assertEquals(LocalDate.of(2026, 1, 10), after.getLastDate());
    }

    @Test
    void delete_midChain_followersRecalc() {
        // 场景：1/3/6 月三条，删掉 3 月中间行 → 6 月行上次信息应回落为 1 月、第几次减一
        String part = "TMPCHAIN-DEL-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        pushRecord(part, LocalDate.of(2026, 3, 10));
        UnwarrantedMaterial uwJun = pushRecord(part, LocalDate.of(2026, 6, 10));
        assertEquals(LocalDate.of(2026, 3, 10), uwJun.getLastDate());
        assertEquals(3, uwJun.getOccurrenceNo());

        UnwarrantedMaterial uwMar = findByDate(part, LocalDate.of(2026, 3, 10));
        unwarrantedMaterialService.delete(uwMar.getId());

        UnwarrantedMaterial after = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals(LocalDate.of(2026, 1, 10), after.getLastDate(), "中间行删除后上次日期应回落为 1 月");
        assertEquals(2, after.getOccurrenceNo());
        // 1 月行（前序）不受影响
        assertEquals(1, findByDate(part, LocalDate.of(2026, 1, 10)).getOccurrenceNo());
    }

    @Test
    void create_insertMidChain_followersRecalc() {
        // 场景：链上已有 1/6 月，手动新增一条 3 月记录插到中间 → 6 月行上次信息应前移为 3 月
        String part = "TMPCHAIN-INS-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        pushRecord(part, LocalDate.of(2026, 6, 10));

        UnwarrantedMaterial mid = new UnwarrantedMaterial();
        mid.setCompanyId(1L);
        mid.setRecordDate(LocalDate.of(2026, 3, 10));
        mid.setFactory(FACTORY);
        mid.setMachineNo(MACHINE);
        mid.setPartName(part);
        mid.setMaterialCode("TMP-CHAIN-MC");
        mid.setRepairPerson("mid-tester");
        mid.setQuantity(1);
        unwarrantedMaterialService.create(mid);

        UnwarrantedMaterial after = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals(LocalDate.of(2026, 3, 10), after.getLastDate(), "插入链中后 6 月行上次日期应前移为 3 月");
        assertEquals(3, after.getOccurrenceNo());
        assertEquals("未过保", after.getWarrantyStatus());
        assertEquals(3, after.getTotalCount());
    }

    @Test
    void batchDelete_multi_chainFollowerResetToFirst() {
        // 场景：1/3/6 月三条，批量删 1 月 + 3 月 → 6 月行重算为首修
        String part = "TMPCHAIN-BDEL-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        pushRecord(part, LocalDate.of(2026, 3, 10));
        pushRecord(part, LocalDate.of(2026, 6, 10));

        UnwarrantedMaterial uwJan = findByDate(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial uwMar = findByDate(part, LocalDate.of(2026, 3, 10));
        unwarrantedMaterialService.batchDelete(List.of(uwJan.getId(), uwMar.getId()));

        UnwarrantedMaterial after = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals("", after.getWarrantyStatus(), "前序全删后 6 月行应重算为首修（返修判定空）");
        assertEquals(1, after.getOccurrenceNo());
        assertNull(after.getLastDate());
        assertEquals(1, after.getTotalCount());
    }

    @Test
    void update_unchangedKey_noSideEffectOnEarlierRows() {
        // 回归：链上最晚行编辑自身（键与日期不变）→ 前序行派生值不变（无链效应扩散）
        String part = "TMPCHAIN-NOSIDE-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial uwJun = pushRecord(part, LocalDate.of(2026, 6, 10));

        UnwarrantedMaterial edit = unwarrantedMaterialService.getById(uwJun.getId());
        edit.setRepairPerson("edited-tester");
        unwarrantedMaterialService.update(edit);

        UnwarrantedMaterial uwJan = findByDate(part, LocalDate.of(2026, 1, 10));
        assertEquals(1, uwJan.getOccurrenceNo());
        assertNull(uwJan.getLastDate());
        assertNull(uwJan.getLastRepairPerson());
        assertEquals(1, uwJan.getTotalCount());
        assertEquals("edited-tester", findByDate(part, LocalDate.of(2026, 6, 10)).getRepairPerson());
    }

    @Test
    void update_editRepairPerson_chainFollowersLastRepairPersonSynced() {
        // 场景：1/6 月两条，编辑 1 月行维修人 → 6 月行的"上次维修人"快照应随之更新
        String part = "TMPCHAIN-RP-" + System.nanoTime();
        pushRecord(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial uwJun = pushRecord(part, LocalDate.of(2026, 6, 10));
        assertEquals("tester", uwJun.getLastRepairPerson());

        UnwarrantedMaterial uwJan = findByDate(part, LocalDate.of(2026, 1, 10));
        UnwarrantedMaterial edit = unwarrantedMaterialService.getById(uwJan.getId());
        edit.setRepairPerson("new-tester");
        unwarrantedMaterialService.update(edit);

        UnwarrantedMaterial after = findByDate(part, LocalDate.of(2026, 6, 10));
        assertEquals("new-tester", after.getLastRepairPerson(), "前序行维修人变更后，后续行'上次维修人'应同步");
    }
}
