package com.metal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 语音/文字输入解析服务 — 基于关键词锚点匹配，将口语化文字拆解为表单字段。
 *
 * 算法：在输入文本中找到所有关键词的位置 → 排序 → 关键词之间的文字块即为字段值。
 * 例如: "日期2026年7月21日 班次白班 维修人张三"
 *   → 日期=2026年7月21日, 班次=白班, 维修人=张三
 */
@Service
public class VoiceParseService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 各表关键词定义 ====================
    // 格式: { fieldName → [关键词1, 关键词2, ...] }
    // 关键词按长度降序排列（长关键词优先匹配，避免"时间"误匹配"报修时间"）

    private static final Map<String, Map<String, String[]>> TABLE_KEYWORDS = new LinkedHashMap<>();

    static {
        // --- 送货记录 ---
        TABLE_KEYWORDS.put("delivery-record", orderedMap(new LinkedHashMap<>() {{
            put("recordDate",    new String[]{"日期", "送货日期", "记录日期"});
            put("category",      new String[]{"类别"});
            put("materialName",  new String[]{"物料名称", "名称"});
            put("specModel",     new String[]{"规格型号", "规格", "型号"});
            put("materialCode",  new String[]{"物料编码", "编码", "料号"});
            put("materialSerial",new String[]{"物料序列号", "序列号", "出厂编号"});
            put("quantity",      new String[]{"数量"});
            put("unit",          new String[]{"单位"});
            put("brand",         new String[]{"品牌"});
            put("productAttr",   new String[]{"产品属性"});
            put("factory",       new String[]{"厂房"});
            put("shipmentNo",    new String[]{"送货单号", "单号"});
            put("remark",        new String[]{"备注"});
        }}));

        // --- 维修记录 ---
        TABLE_KEYWORDS.put("original-record", orderedMap(new LinkedHashMap<>() {{
            put("recordDate",          new String[]{"日期", "记录日期"});
            put("shift",               new String[]{"班次"});
            put("factory",             new String[]{"厂房", "车间"});
            put("serialNumber",        new String[]{"序列号"});
            put("machineNo",           new String[]{"机台号", "机号"});
            put("machineModel",        new String[]{"机型"});
            put("diagnostician",       new String[]{"诊断人"});
            put("repairPerson",        new String[]{"维修人"});
            put("confirmer",           new String[]{"确认人"});
            put("repairRequestTime",   new String[]{"报修时间", "报障时间"});
            put("startTime",           new String[]{"开始时间", "接单时间"});
            put("endTime",             new String[]{"结束时间", "维修结束时间", "完成时间"});
            put("faultPhenomenon",     new String[]{"故障现象", "故障描述"});
            put("faultDescription",    new String[]{"维修描述", "维修方案", "故障原因"});
            put("materialCode",        new String[]{"物料编码", "料号", "编码"});
            put("partName",            new String[]{"配件名称", "配件", "部件名称"});
            put("quantity",            new String[]{"数量"});
            put("machineOnMaterial",   new String[]{"上机物料号", "上机物料", "上机"});
            put("machineOffMaterial",  new String[]{"下机物料号", "下机物料", "下机"});
            put("remark",              new String[]{"备注"});
            put("deliveryRecordRef",   new String[]{"送货记录引用", "送货记录"});
            put("documentNo",         new String[]{"单据号"});
        }}));

        // --- 上机物料 ---
        TABLE_KEYWORDS.put("machine-material", orderedMap(new LinkedHashMap<>() {{
            put("recordDate",          new String[]{"日期", "记录日期"});
            put("shift",               new String[]{"班次"});
            put("factory",             new String[]{"厂房", "车间"});
            put("serialNumber",        new String[]{"序列号"});
            put("machineNo",           new String[]{"机台号", "机号"});
            put("machineModel",        new String[]{"机型"});
            put("repairPerson",        new String[]{"维修人"});
            put("confirmer",           new String[]{"确认人"});
            put("repairRequestTime",   new String[]{"报修时间", "报障时间"});
            put("startTime",           new String[]{"开始时间", "接单时间"});
            put("endTime",             new String[]{"结束时间", "维修结束时间"});
            put("faultPhenomenon",     new String[]{"故障现象", "故障描述"});
            put("faultDescription",    new String[]{"维修描述", "维修方案"});
            put("materialCode",        new String[]{"物料编码", "料号"});
            put("partName",            new String[]{"配件名称", "配件"});
            put("quantity",            new String[]{"数量"});
            put("machineOnMaterial",   new String[]{"上机物料号", "上机物料", "上机"});
            put("machineOffMaterial",  new String[]{"下机物料号", "下机物料", "下机"});
            put("remark",              new String[]{"备注"});
            put("deliveryRecordRef",   new String[]{"送货记录引用", "送货记录"});
        }}));

        // --- 送货超比统计 ---
        TABLE_KEYWORDS.put("delivery-stats", orderedMap(new LinkedHashMap<>() {{
            put("category",            new String[]{"类别"});
            put("materialCode",        new String[]{"物料编码", "料号"});
            put("systemName",          new String[]{"系统名称", "系统"});
            put("partName",            new String[]{"配件名称", "配件"});
            put("unitUsage",           new String[]{"单台机用量", "台机用量", "用量"});
            put("ratio",               new String[]{"比例"});
            put("unitPriceWithTax",    new String[]{"含税单价", "单价", "价格"});
            put("machineCount",        new String[]{"机台数", "台数"});
            put("deliveryQuantity",    new String[]{"送货数量"});
            put("machineOnQuantity",   new String[]{"上机数量"});
            put("monthRepair",         new String[]{"当月返修", "返修"});
            put("statDate",            new String[]{"统计日期", "日期"});
        }}));

        // --- 结算机台数 ---
        TABLE_KEYWORDS.put("settlement-machine", orderedMap(new LinkedHashMap<>() {{
            put("materialCode",           new String[]{"物料编码", "料号"});
            put("category",               new String[]{"类别"});
            put("partName",               new String[]{"配件名称", "配件"});
            put("unitUsage",              new String[]{"单台机用量", "台机用量"});
            put("ratio",                  new String[]{"比例"});
            put("unitPriceWithTax",       new String[]{"含税单价", "单价"});
            put("warrantyPeriod",         new String[]{"保修期", "质保期"});
            put("priceType",              new String[]{"价格类型"});
            put("remark",                 new String[]{"备注"});
            put("machineModel",           new String[]{"机型"});
            put("settlementMachineCount", new String[]{"结算机台数量", "结算数量", "机台数"});
        }}));

        // --- 机型明细 ---
        TABLE_KEYWORDS.put("machine-detail", orderedMap(new LinkedHashMap<>() {{
            put("factory",      new String[]{"厂房", "车间"});
            put("machineNo",    new String[]{"机台号", "机号"});
            put("machineBrand", new String[]{"机台品牌", "机品牌", "品牌"});
        }}));

        // --- 开机数量 ---
        TABLE_KEYWORDS.put("machine-count", orderedMap(new LinkedHashMap<>() {{
            put("machineModel", new String[]{"机型", "机台型号"});
            put("count",        new String[]{"开机数量", "数量", "机台数量"});
            put("ratioPct",     new String[]{"比例", "占比"});
            put("statMonth",    new String[]{"统计月份", "月份"});
            put("remark",       new String[]{"备注"});
        }}));

        // --- 物料表 ---
        TABLE_KEYWORDS.put("material", orderedMap(new LinkedHashMap<>() {{
            put("category",     new String[]{"类别"});
            put("materialName", new String[]{"物料名称", "名称"});
            put("specModel",    new String[]{"规格型号", "规格"});
            put("materialCode", new String[]{"物料编码", "编码"});
            put("remark",       new String[]{"备注"});
        }}));
    }

    // ==================== 解析入口 ====================

    /**
     * 根据表类型解析文本，返回字段名→值的映射
     */
    public Map<String, Object> parse(String text, String tableType) {
        Map<String, String[]> fieldKeywords = TABLE_KEYWORDS.get(tableType);
        if (fieldKeywords == null) {
            throw new IllegalArgumentException("不支持的表类型: " + tableType);
        }

        if (text == null || text.isBlank()) {
            return Map.of("fields", Collections.emptyMap(), "message", "输入文本为空");
        }

        text = text.replaceAll("\\s+", " ").trim(); // 归一化空白

        // 构建全局关键词→字段名映射，关键词按长度降序
        List<KwEntry> allKeywords = new ArrayList<>();
        for (var entry : fieldKeywords.entrySet()) {
            String fieldName = entry.getKey();
            for (String kw : entry.getValue()) {
                allKeywords.add(new KwEntry(kw, fieldName));
            }
        }
        // 长关键词优先
        allKeywords.sort((a, b) -> Integer.compare(b.keyword.length(), a.keyword.length()));

        // 在文本中查找所有关键词出现位置
        List<Match> matches = new ArrayList<>();
        for (KwEntry kw : allKeywords) {
            int pos = 0;
            while ((pos = text.indexOf(kw.keyword, pos)) >= 0) {
                matches.add(new Match(kw.keyword, kw.fieldName, pos));
                pos += kw.keyword.length();
            }
        }
        // 按位置排序
        matches.sort(Comparator.comparingInt(m -> m.pos));

        // 去重：同一位置取最长关键词，同一字段取最早出现
        List<Match> filtered = new ArrayList<>();
        Set<String> usedFields = new HashSet<>();
        for (Match m : matches) {
            if (usedFields.contains(m.fieldName)) continue;
            // 检查是否被已选中的匹配覆盖（位置重叠）
            boolean overlap = false;
            for (Match f : filtered) {
                if (m.pos >= f.pos && m.pos < f.pos + f.keyword.length()) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                filtered.add(m);
                usedFields.add(m.fieldName);
            }
        }
        // 按位置重新排序
        filtered.sort(Comparator.comparingInt(m -> m.pos));

        // 提取值：关键词之间的文字
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < filtered.size(); i++) {
            Match m = filtered.get(i);
            int valueStart = m.pos + m.keyword.length();
            int valueEnd = (i + 1 < filtered.size()) ? filtered.get(i + 1).pos : text.length();
            String value = text.substring(valueStart, valueEnd).trim();
            if (!value.isEmpty()) {
                fields.put(m.fieldName, value);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields);
        result.put("filledCount", fields.size());
        return result;
    }

    // ==================== 辅助类和方法 ====================

    /** 按插入顺序保持 key，同时关键词数组内部按长度降序 */
    private static Map<String, String[]> orderedMap(Map<String, String[]> map) {
        Map<String, String[]> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            String[] kws = entry.getValue();
            Arrays.sort(kws, (a, b) -> Integer.compare(b.length(), a.length()));
            result.put(entry.getKey(), kws);
        }
        return result;
    }

    private record KwEntry(String keyword, String fieldName) {}
    private record Match(String keyword, String fieldName, int pos) {}
}
