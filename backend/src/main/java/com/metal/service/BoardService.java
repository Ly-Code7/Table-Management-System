package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.metal.common.BizException;
import com.metal.entity.BoardRow;
import com.metal.mapper.BoardMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板聚合（实时聚合，不建物化表）。
 * 口径与 Excel 原表公式逐格对齐：
 *  - 维修金额 = SUMIFS(未过保物料 维修金额, 年+月, 厂房+机台号) → unwarranted_material GROUP BY plant_machine
 *  - 故障频次 = COUNTIFS(总维修明细 年+月, 厂房+机台) → original_record GROUP BY 厂房-机台
 *  - 物料频次 = COUNTIFS(未过保物料 年+月, 物料编码) → unwarranted_material GROUP BY material_code
 *  - 返修频次初版同物料频次（Excel 辅助列-T0 为物料编码冗余副本，用户确认；对账差异时分析判别条件）
 *  - 返修率 = 返修频次 ÷ 物料频次（IFERROR 语义：分母 0 → 空）
 * 年度滚动：year_month 前缀（FY26）过滤，2027 年录入数据自动生成 FY27xx，无需迁移。
 */
@Service
public class BoardService {

    @Autowired
    private BoardMapper mapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 年度前缀：2026 -> FY26 */
    private String ymPrefix(int year) {
        return "FY" + String.format("%02d", year % 100);
    }

    /** 月份 key 列表：FY2601..FY2612 */
    private List<String> monthKeys(int year) {
        String p = ymPrefix(year);
        List<String> keys = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            keys.add(p + String.format("%02d", m));
        }
        return keys;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(String.valueOf(v));
    }

    /**
     * 机台看板（维修金额 amount / 故障频次 count）。
     * 行列表 = original_record 机台去重（与 Excel UNIQUE(总维修明细 B 列) 一致）；列 = 12 月 + 小计。
     */
    public List<BoardRow> machineBoard(Long companyId, int year, String kind) {
        Long cid = companyId != null ? companyId : 1L;
        String prefix = ymPrefix(year);
        List<String> keys = monthKeys(year);
        List<String> machines = mapper.machineList(cid);
        List<Map<String, Object>> rows = "amount".equals(kind)
                ? mapper.repairAmountSum(cid, prefix)
                : mapper.faultCount(cid, prefix);
        Map<String, BigDecimal> agg = new HashMap<>();
        for (Map<String, Object> r : rows) {
            agg.put(r.get("k") + "|" + r.get("ym"), toBigDecimal(r.get("v")));
        }
        List<BoardRow> out = new ArrayList<>(machines.size());
        for (String m : machines) {
            BoardRow row = new BoardRow();
            row.setKey(m);
            int idx = m.indexOf('-');
            row.setFactory(idx > 0 ? m.substring(0, idx) : m);
            BigDecimal total = ZERO;
            for (String k : keys) {
                BigDecimal v = agg.getOrDefault(m + "|" + k, ZERO);
                row.getMonths().put(k, v);
                total = total.add(v);
            }
            row.setTotal(total);
            out.add(row);
        }
        return out;
    }

    /**
     * 料号看板（物料频次 material / 返修频次 repair）。
     * 行列表 = base_material_156（156 项）；列 = 12 月 + 合计 + 金额（单价 × 合计）。
     * 返修频次以「非首次维修（last_date 非空）」近似（用户确认，覆盖 Excel T0 标记 96.7%）。
     */
    public List<BoardRow> materialBoard(Long companyId, int year, String kind) {
        Long cid = companyId != null ? companyId : 1L;
        String prefix = ymPrefix(year);
        List<String> keys = monthKeys(year);
        List<Map<String, Object>> materials = mapper.materialList(cid);
        List<Map<String, Object>> rows = "repair".equals(kind)
                ? mapper.repairCount(cid, prefix)
                : mapper.materialCount(cid, prefix);
        Map<String, BigDecimal> agg = new HashMap<>();
        for (Map<String, Object> r : rows) {
            if (r.get("k") == null) continue;
            agg.put(r.get("k") + "|" + r.get("ym"), toBigDecimal(r.get("v")));
        }
        List<BoardRow> out = new ArrayList<>(materials.size());
        for (Map<String, Object> m : materials) {
            BoardRow row = new BoardRow();
            row.setKey(String.valueOf(m.get("code")));
            row.setCategory(m.get("category") != null ? String.valueOf(m.get("category")) : null);
            row.setPartName(m.get("part_name") != null ? String.valueOf(m.get("part_name")) : null);
            row.setPrice(m.get("price") != null ? new BigDecimal(String.valueOf(m.get("price"))) : null);
            BigDecimal total = ZERO;
            for (String k : keys) {
                BigDecimal v = agg.getOrDefault(row.getKey() + "|" + k, ZERO);
                row.getMonths().put(k, v);
                total = total.add(v);
            }
            row.setTotal(total);
            row.setAmount(row.getPrice() != null ? row.getPrice().multiply(total) : null);
            out.add(row);
        }
        return out;
    }

    /**
     * 返修率看板：返修频次 ÷ 物料频次（IFERROR 语义：分母 0 或空 → 空）。
     * 平均列 = 合计返修 ÷ 合计物料（12 个月口径）。
     */
    public List<BoardRow> repairRate(Long companyId, int year) {
        List<BoardRow> freq = materialBoard(companyId, year, "material");
        List<BoardRow> repair = materialBoard(companyId, year, "repair");
        Map<String, BoardRow> repairByKey = new HashMap<>();
        for (BoardRow r : repair) {
            repairByKey.put(r.getKey(), r);
        }
        for (BoardRow row : freq) {
            BoardRow rr = repairByKey.get(row.getKey());
            for (String k : row.getMonths().keySet()) {
                BigDecimal denom = row.getMonths().get(k);
                BigDecimal num = rr != null ? rr.getMonths().get(k) : ZERO;
                if (denom != null && denom.compareTo(ZERO) > 0) {
                    row.getMonths().put(k, num.divide(denom, 4, RoundingMode.HALF_UP));
                } else {
                    row.getMonths().put(k, null);
                }
            }
            if (row.getTotal() != null && row.getTotal().compareTo(ZERO) > 0) {
                BigDecimal repTotal = rr != null && rr.getTotal() != null ? rr.getTotal() : ZERO;
                row.setAverage(repTotal.divide(row.getTotal(), 4, RoundingMode.HALF_UP));
            } else {
                row.setAverage(null);
            }
            row.setTotal(null);
            row.setAmount(null);
        }
        return freq;
    }

    /** Excel 导出：动态表头（行维度 + 12 月 + 小计/合计/金额/平均），参照 DeliveryRecordService.exportExcel 模式 */
    public void exportExcel(HttpServletResponse response, String board, Long companyId, int year) {
        boolean isMachine = "repair-amount".equals(board) || "fault-frequency".equals(board);
        boolean isRate = "repair-rate".equals(board);
        List<BoardRow> rows;
        List<String> extraHead;
        switch (board) {
            case "repair-amount" -> { rows = machineBoard(companyId, year, "amount"); extraHead = List.of("小计"); }
            case "fault-frequency" -> { rows = machineBoard(companyId, year, "count"); extraHead = List.of("小计"); }
            case "material-frequency" -> { rows = materialBoard(companyId, year, "material"); extraHead = List.of("合计", "金额"); }
            case "repair-frequency" -> { rows = materialBoard(companyId, year, "repair"); extraHead = List.of("合计", "金额"); }
            case "repair-rate" -> { rows = repairRate(companyId, year); extraHead = List.of("平均"); }
            default -> throw new BizException("未知看板: " + board);
        }
        List<String> keys = monthKeys(year);
        List<List<String>> head = new ArrayList<>();
        if (isMachine) {
            head.add(List.of("厂房+机台号"));
            head.add(List.of("厂房"));
        } else {
            head.add(List.of("类别"));
            head.add(List.of("料号"));
            head.add(List.of("配件名称"));
            head.add(List.of("合约单价"));
        }
        for (String k : keys) head.add(List.of(k));
        for (String h : extraHead) head.add(List.of(h));

        List<List<Object>> data = new ArrayList<>();
        for (BoardRow r : rows) {
            List<Object> line = new ArrayList<>();
            if (isMachine) {
                line.add(r.getKey());
                line.add(r.getFactory());
            } else {
                line.add(r.getCategory());
                line.add(r.getKey());
                line.add(r.getPartName());
                line.add(r.getPrice());
            }
            for (String k : keys) line.add(r.getMonths().get(k));
            if (isRate) {
                line.add(r.getAverage());
            } else {
                line.add(r.getTotal());
                if (!isMachine) line.add(r.getAmount());
            }
            data.add(line);
        }
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode(board + "-" + year + ".xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os).head(head)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("看板")
                    .doWrite(data);
            os.flush();
        } catch (IOException e) {
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    /** 默认年份：当年 */
    public int defaultYear() {
        return LocalDate.now().getYear();
    }
}
