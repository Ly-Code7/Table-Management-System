package com.metal.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.converters.WriteConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 维修记录导入用宽松时间解析器：
 * Excel 中"报修/开始/结束时间"列存在多种格式（历史数据），常见：
 *   - Excel 时间/日期时间（数值序列号，如 0.85、46204.35）——按 Excel 1900 序列号转 LocalDateTime
 *   - 文本 "8:30" / "08:30" / "8.30"（只有时分，无日期——日期部分用 1900-01-01 占位，
 *     由 OriginalRecordService.fixTime 按记录日期重建）
 *   - 文本 "2026-07-01 08:30" / "2026/07/01 8:30" 等分隔符变体
 *   - 全角冒号/分号/引号/逗号（输入法差异，如 8：30、15；00、6,40、11"30）——归一化为半角冒号
 * supportExcelTypeKey 返回 null 表示接管全部单元格类型（EasyExcel 对字段自定义 converter
 * 不回落内置转换器，数值单元格必须在此处理，否则时间会静默丢失为 null）。
 */
public class LocalDateTimeFlexibleConverter implements Converter<LocalDateTime> {

    /** Excel 1900 日期系统序列号基准：0 = 1899-12-31 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 31);

    private static final DateTimeFormatter[] PATTERNS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH.mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm")
    };

    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDateTime.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return null; // 接管全部单元格类型（STRING 文本 + NUMBER 序列号）
    }

    @Override
    public LocalDateTime convertToJavaData(ReadConverterContext<?> context) {
        ReadCellData<?> cell = context.getReadCellData();
        if (cell == null) return null;
        if (cell.getType() == CellDataTypeEnum.NUMBER) {
            // Excel 时间/日期时间序列号：整数部分为距 1899-12-31 的天数，小数部分为当日时间
            BigDecimal num = cell.getNumberValue();
            if (num == null) return null;
            double d = num.doubleValue();
            long days = (long) d;
            double frac = d - days;
            long nanos = Math.round(frac * 86400_000_000_000L);
            if (nanos >= 86400_000_000_000L) { nanos = 0; days++; }
            LocalDateTime dt = LocalDateTime.of(EXCEL_EPOCH, LocalTime.MIDNIGHT)
                    .plusDays(days).plusNanos(nanos);
            // 纯时间序列号（0~1）日期部分为 1899-12-31，由 fixTime 按记录日期重建
            return dt;
        }
        String text = cell.getStringValue();
        if (text == null) return null;
        // 全角冒号/分号/引号（含全角引号“”）、半角引号、逗号 → 半角冒号，连续冒号合并、去开头冒号
        // （历史数据输入法差异：8：30、15；00、6,40、11"30、11“30、18：:00、：1:20）
        String s = text.trim()
                .replace('：', ':').replace('；', ':')
                .replace('“', ':').replace('”', ':')
                .replace('"', ':').replace(',', ':')
                .replaceAll(":+", ":")
                .replaceFirst("^:", "");
        if (s.isEmpty()) return null;
        for (DateTimeFormatter fmt : PATTERNS) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        // 无日期只有时分的格式（如 "8:30" / "8.30"）：日期部分用 1900-01-01 占位，由 fixTime 按记录日期重建
        for (String pattern : new String[]{"H:mm", "H.mm"}) {
            try {
                LocalTime time = LocalTime.parse(s, DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
                return LocalDateTime.of(LocalDate.of(1900, 1, 1), time);
            } catch (Exception ignored) {
                // 尝试下一种
            }
        }
        // 解析不了返回 null：时间列留空，不阻塞整行导入
        return null;
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<LocalDateTime> context) {
        LocalDateTime value = context.getValue();
        if (value == null) return new WriteCellData<>("");
        return new WriteCellData<>(value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
