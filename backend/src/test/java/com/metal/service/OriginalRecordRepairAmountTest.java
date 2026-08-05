package com.metal.service;

import com.metal.entity.BaseMaterial156;
import com.metal.entity.OriginalRecord;
import com.metal.mapper.BaseMaterial156Mapper;
import com.metal.mapper.UnwarrantedMaterialMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维修金额口径测试：下推未过保物料时 repairAmount = 156项表同料号含税单价 × 数量
 * （不再依赖当月超比统计表单价）。
 */
@SpringBootTest
@Transactional
class OriginalRecordRepairAmountTest {

    @Autowired
    private OriginalRecordService originalRecordService;

    @Autowired
    private UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    @Autowired
    private BaseMaterial156Mapper baseMaterial156Mapper;

    @Test
    void repairAmount_uses156UnitPriceTimesQuantity() {
        // 从 156 项表取公司 14 的一个真实料号及其含税单价
        List<BaseMaterial156> list = baseMaterial156Mapper.search(14L, "", "id", "desc");
        assertFalse(list.isEmpty(), "156项表公司14应有数据");
        BaseMaterial156 item = list.get(0);
        assertNotNull(item.getUnitPriceWithTax(), "156项表该料号应有含税单价");

        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(14L);
        r.setRecordDate(LocalDate.of(2026, 6, 14));
        r.setFactory("测试厂房");
        r.setMachineNo("T-AMT-01");
        r.setMaterialCode(item.getMaterialCode());
        r.setQuantity(2);
        r.setRepairPerson("tester");
        r.setFaultDescription("金额口径验证");
        OriginalRecord saved = originalRecordService.create(r);

        List<com.metal.entity.UnwarrantedMaterial> hits =
                unwarrantedMaterialMapper.search(14L, r.getPartName() == null ? item.getMaterialCode() : r.getPartName(),
                        null, null, null, null, "id", "desc");
        assertFalse(hits.isEmpty(), "数量>=1 应下推未过保物料");
        com.metal.entity.UnwarrantedMaterial uw = hits.get(0);
        assertEquals(saved.getId(), uw.getOriginalRecordId());

        BigDecimal expected = item.getUnitPriceWithTax().multiply(BigDecimal.valueOf(2));
        assertNotNull(uw.getRepairAmount(), "维修金额不应为空");
        assertEquals(0, expected.compareTo(uw.getRepairAmount()),
                "维修金额应等于 156项表单价×数量, 期望 " + expected + " 实际 " + uw.getRepairAmount());
    }
}
