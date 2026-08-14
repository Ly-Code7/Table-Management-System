package com.metal.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.converters.WriteConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;

import java.math.BigDecimal;

/**
 * 维修记录导入用宽松 BigDecimal 解析器：
 * Excel 工时列存在业务文本（历史数据手工填写），如「未停机」（无停机故无工时），
 * 映射为 null 正常导入，不再因 BigDecimal 转换失败整行跳过。
 *
 * 容错边界：
 *   - NUMBER 单元格 → 原样转换
 *   - STRING 单元格：空白 / 「未停机」→ null；其他文本（如 #N/A、乱码）→ 抛异常，
 *     由 AnalysisEventListener.onException 计入失败明细跳过该行（保持既有容错语义）
 *   - EMPTY → null；ERROR/BOOLEAN 等其他类型 → 抛异常（保持既有跳过行为）
 *
 * supportExcelTypeKey 返回 null 表示接管全部单元格类型（EasyExcel 对字段自定义
 * converter 不回落内置转换器，NUMBER 单元格必须在此处理，否则数值工时静默丢失）。
 */
public class FlexibleBigDecimalConverter implements Converter<BigDecimal> {

    /** 业务文本：未停机，无工时 */
    private static final String NO_DOWNTIME_TEXT = "未停机";

    @Override
    public Class<?> supportJavaTypeKey() {
        return BigDecimal.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return null; // 接管全部单元格类型（STRING 业务文本 + NUMBER 数值）
    }

    @Override
    public BigDecimal convertToJavaData(ReadConverterContext<?> context) {
        ReadCellData<?> cell = context.getReadCellData();
        if (cell == null) return null;
        if (cell.getType() == CellDataTypeEnum.NUMBER) {
            return cell.getNumberValue();
        }
        if (cell.getType() == CellDataTypeEnum.STRING) {
            String s = cell.getStringValue();
            if (s == null) return null;
            String t = s.trim();
            if (t.isEmpty()) return null;
            if (NO_DOWNTIME_TEXT.equals(t)) return null;
            // 其他无法解析的文本：抛异常，由 onException 计入失败明细跳过该行
            throw new NumberFormatException("无法解析的工时文本: " + s);
        }
        if (cell.getType() == CellDataTypeEnum.EMPTY) return null;
        throw new NumberFormatException("不支持的单元格类型: " + cell.getType());
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<BigDecimal> context) {
        BigDecimal value = context.getValue();
        if (value == null) return new WriteCellData<>("");
        return new WriteCellData<>(value);
    }
}
