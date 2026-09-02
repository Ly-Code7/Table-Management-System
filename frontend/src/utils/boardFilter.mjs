/**
 * 数据看板多条件组合筛选（2026-09）。
 * 纯函数模块（无 Vue 依赖，node --test 可直接测试）：
 *  - buildColumns(tab, monthKeys)          → 按当前看板维度生成可选条件列
 *  - getRowValue(row, column)              → 取行内某列的值（月份列走 months 映射）
 *  - matchCondition(row, cond, tab, monthKeys) → 单条件匹配（空值/非法输入视为不生效）
 *  - filterRowsByConditions(rows, conds, tab, monthKeys) → 多条件 AND 过滤
 *
 * 条件模型：{ column: string, op: string, value: string|number|null }
 * 列类型：text（包含/等于，大小写不敏感）| number（> ≥ < ≤ = ≠）| percent（average 存小数，输入按百分比数字）
 */

export const TEXT_OPS = ['包含', '等于']
export const NUM_OPS = ['>', '≥', '<', '≤', '=', '≠']

const MACHINE_TABS = ['repair-amount', 'fault-frequency']
const MONTH_RE = /^FY\d{4}$/

/** 是否为月份列（FY2601 形如） */
function isMonthColumn(column) {
  return MONTH_RE.test(column)
}

/**
 * 生成可选条件列（{ value, label, type }）。
 * value 与行字段对应：月份列值为 'FY2601'…，其余为 BoardRow 顶层字段名。
 */
export function buildColumns(tab, monthKeys) {
  const months = (monthKeys || []).map(m => ({ value: m, label: m, type: 'number' }))
  if (MACHINE_TABS.includes(tab)) {
    return [
      { value: 'key', label: '厂房+机台号', type: 'text' },
      { value: 'factory', label: '厂房', type: 'text' },
      ...months,
      { value: 'total', label: '小计', type: 'number' }
    ]
  }
  const cols = [
    { value: 'key', label: '料号', type: 'text' },
    { value: 'partName', label: '156项名称', type: 'text' },
    { value: 'category', label: '类别', type: 'text' },
    { value: 'price', label: '合约单价', type: 'number' },
    ...months,
    { value: 'total', label: '合计', type: 'number' },
    { value: 'amount', label: '金额', type: 'number' }
  ]
  if (tab === 'repair-rate') {
    cols.push({ value: 'average', label: '平均', type: 'percent' })
  }
  return cols
}

/** 取行内某列的值（月份列 → months 映射；其余 → 顶层字段） */
export function getRowValue(row, column) {
  if (row == null) return undefined
  if (isMonthColumn(column)) {
    return row.months != null ? row.months[column] : undefined
  }
  return row[column]
}

/** 数值比较：≥6 类运算符统一入口（percent 输入按百分比换算） */
function compareNumber(actual, expected, op) {
  switch (op) {
    case '>': return actual > expected
    case '≥': return actual >= expected
    case '<': return actual < expected
    case '≤': return actual <= expected
    case '=': return actual === expected
    case '≠': return actual !== expected
    default: return true
  }
}

/**
 * 单条件匹配。约定：
 *  - value 为空（'' / null / undefined）→ 条件不生效，返回 true
 *  - text：包含/等于（trim + 大小写不敏感）
 *  - number/percent：值解析失败（NaN）→ 不生效返回 true；行内该列缺失/null → 不匹配返回 false
 *  - percent：expected = Number(value) / 100（输入 60 表示 60%）
 */
export function matchCondition(row, cond, tab, monthKeys) {
  const column = cond.column
  const raw = cond.value
  if (raw === '' || raw === null || raw === undefined) return true
  const type = (buildColumns(tab, monthKeys).find(c => c.value === column) || {}).type || 'text'

  if (type === 'text') {
    const actual = String(getRowValue(row, column) ?? '')
    const target = String(raw).trim().toLowerCase()
    if (!target) return true
    if (cond.op === '等于') return actual.toLowerCase() === target
    return actual.toLowerCase().includes(target)
  }

  // number / percent
  let expected = Number(raw)
  if (Number.isNaN(expected)) return true // 非法输入不生效
  if (type === 'percent') expected = expected / 100
  const actual = getRowValue(row, column)
  if (actual === null || actual === undefined || actual === '') return false // 缺值不匹配
  return compareNumber(Number(actual), expected, cond.op)
}

/** 多条件 AND 过滤（conditions 为空的 value 自动跳过） */
export function filterRowsByConditions(rows, conditions, tab, monthKeys) {
  const active = (conditions || []).filter(c =>
    c && c.column && c.value !== '' && c.value !== null && c.value !== undefined
  )
  if (active.length === 0) return rows
  return rows.filter(r => active.every(c => matchCondition(r, c, tab, monthKeys)))
}
