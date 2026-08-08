package com.metal.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.converters.WriteConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.WriteCellData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 维修记录导入用宽松时间解析器：
 * Excel 中"报修/开始/结束时间"列存在多种文本格式（历史数据），常见：
 *   - "8:30" / "08:30"（只有时分，无日期——日期部分用 1900-01-01 占位，
 *     由 OriginalRecordService.fixTime 按记录日期重建）
 *   - "2026-07-01 08:30" / "2026-07-01 08:30:00"
 *   - "2026/07/01 8:30" 等分隔符变体
 * 仅接管文本（STRING）单元格；Excel 时间格式（数字序列号）仍走 EasyExcel 默认
 * 转换 + fixTime 兜底，行为不变。
 */
public class LocalDateTimeFlexibleConverter implements Converter<LocalDateTime> {

    private static final DateTimeFormatter[] PATTERNS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm")
    };

    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDateTime.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public LocalDateTime convertToJavaData(ReadConverterContext<?> context) {
        String text = context.getReadCellData().getStringValue();
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter fmt : PATTERNS) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        // 无日期只有时分的格式（如 "8:30"）：日期部分用 1900-01-01 占位，由 fixTime 按记录日期重建
        try {
            LocalTime time = LocalTime.parse(s,
                    DateTimeFormatter.ofPattern("H:mm", Locale.ROOT));
            return LocalDateTime.of(LocalDate.of(1900, 1, 1), time);
        } catch (Exception e) {
            // 解析不了返回 null：时间列留空，不阻塞整行导入
            return null;
        }
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<LocalDateTime> context) {
        LocalDateTime value = context.getValue();
        if (value == null) return new WriteCellData<>("");
        return new WriteCellData<>(value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
