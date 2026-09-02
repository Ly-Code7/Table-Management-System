import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildColumns,
  getRowValue,
  matchCondition,
  filterRowsByConditions
} from './boardFilter.mjs'

const monthKeys = ['FY2601', 'FY2602', 'FY2603', 'FY2604', 'FY2605', 'FY2606', 'FY2607', 'FY2608', 'FY2609', 'FY2610', 'FY2611', 'FY2612']

/** 构造一行（料号类看板） */
function row(overrides = {}) {
  return {
    key: 'MC-001',
    factory: 'B5',
    category: '风扇类',
    partName: '驱动风扇',
    price: 100,
    months: { FY2605: 10, FY2606: 5000, FY2607: null },
    total: 5010,
    amount: 501000,
    ...overrides
  }
}

const col = (list, value) => list.find(c => c.value === value)

// ============ buildColumns ============

test('buildColumns 机台类：含厂房+机台号/厂房/12 个月/小计', () => {
  const cols = buildColumns('repair-amount', monthKeys)
  const values = cols.map(c => c.value)
  assert.ok(values.includes('key'), '应含厂房+机台号列')
  assert.ok(values.includes('factory'), '应含厂房列')
  assert.ok(values.includes('FY2606'), '应含月份列')
  assert.ok(values.includes('total'), '应含小计')
  assert.equal(col(cols, 'FY2606').type, 'number')
  assert.equal(col(cols, 'key').type, 'text')
})

test('buildColumns 料号类：含料号/156项名称/金额，不含厂房', () => {
  const cols = buildColumns('material-frequency', monthKeys)
  const values = cols.map(c => c.value)
  assert.ok(values.includes('partName'))
  assert.ok(values.includes('key'))
  assert.ok(values.includes('amount'))
  assert.ok(values.includes('total'))
  assert.ok(values.includes('price'))
  assert.ok(!values.includes('factory'), '料号类不应有厂房列')
  assert.equal(col(cols, 'partName').label, '156项名称')
})

test('buildColumns 返修率看板：额外含平均(percent)', () => {
  const cols = buildColumns('repair-rate', monthKeys)
  assert.ok(cols.some(c => c.value === 'average' && c.type === 'percent'))
})

// ============ getRowValue ============

test('getRowValue：月份列取 months 映射，顶层字段直接取', () => {
  const r = row()
  assert.equal(getRowValue(r, 'FY2606'), 5000)
  assert.equal(getRowValue(r, 'amount'), 501000)
  assert.equal(getRowValue(r, 'partName'), '驱动风扇')
  assert.equal(getRowValue(r, 'FY2607'), null)
  assert.equal(getRowValue(r, 'FY2699'), undefined, '不存在的月份返回 undefined')
})

// ============ matchCondition：文本 ============

test('文本包含/等于（大小写不敏感）', () => {
  const r = row({ key: 'ABC-123' })
  assert.equal(matchCondition(r, { column: 'key', op: '包含', value: 'abc' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'key', op: '包含', value: 'XYZ' }, 'material-frequency', monthKeys), false)
  assert.equal(matchCondition(r, { column: 'key', op: '等于', value: 'ABC-123' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'key', op: '等于', value: 'abc-123' }, 'material-frequency', monthKeys), true, '等于也应大小写不敏感')
  assert.equal(matchCondition(r, { column: 'key', op: '等于', value: 'ABC' }, 'material-frequency', monthKeys), false)
})

// ============ matchCondition：数值 ============

test('数值 > ≥ < ≤ = ≠ 边界（例子：FY2606 > 4000）', () => {
  const r = row({ months: { FY2606: 5000 } })
  assert.equal(matchCondition(r, { column: 'FY2606', op: '>', value: '4000' }, 'material-frequency', monthKeys), true, 'FY2606=5000 > 4000')
  assert.equal(matchCondition(r, { column: 'FY2606', op: '>', value: '5000' }, 'material-frequency', monthKeys), false)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '≥', value: '5000' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '<', value: '5000' }, 'material-frequency', monthKeys), false)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '≤', value: '5000' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '=', value: '5000' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '≠', value: '5000' }, 'material-frequency', monthKeys), false)
})

test('数值条件：月份缺值/空值 → 不匹配', () => {
  const r = row({ months: { FY2605: 10 } }) // 无 FY2606
  assert.equal(matchCondition(r, { column: 'FY2606', op: '>', value: '0' }, 'material-frequency', monthKeys), false, '缺月不匹配')
  const r2 = row({ months: { FY2606: null } })
  assert.equal(matchCondition(r2, { column: 'FY2606', op: '>', value: '0' }, 'material-frequency', monthKeys), false, 'null 值不匹配')
})

test('percent 列：值按百分比输入（60 = 60%），与存储小数(0.6)比较', () => {
  const r = row({ average: 0.6 })
  assert.equal(matchCondition(r, { column: 'average', op: '>', value: '50' }, 'repair-rate', monthKeys), true, '60% > 50%')
  assert.equal(matchCondition(r, { column: 'average', op: '>', value: '60' }, 'repair-rate', monthKeys), false)
  assert.equal(matchCondition(r, { column: 'average', op: '=', value: '60' }, 'repair-rate', monthKeys), true)
})

// ============ 无效/空条件 ============

test('空值与非法数值输入 → 条件不生效（跳过）', () => {
  const r = row()
  assert.equal(matchCondition(r, { column: 'partName', op: '包含', value: '' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '>', value: '' }, 'material-frequency', monthKeys), true)
  assert.equal(matchCondition(r, { column: 'FY2606', op: '>', value: 'abc' }, 'material-frequency', monthKeys), true, '非数字输入不生效')
  assert.equal(matchCondition(r, { column: 'partName', op: '等于', value: null }, 'material-frequency', monthKeys), true)
})

// ============ filterRowsByConditions：AND 组合 ============

test('多条件 AND：FY2606 > 4000 且 类别包含 风扇', () => {
  const rows = [
    row({ key: 'A', category: '风扇类', months: { FY2606: 5000 } }),
    row({ key: 'B', category: '风扇类', months: { FY2606: 1000 } }),
    row({ key: 'C', category: '轴承类', months: { FY2606: 9000 } }),
    { key: '合计', months: { FY2606: 15000 }, total: 15000 }
  ]
  const conds = [
    { column: 'FY2606', op: '>', value: '4000' },
    { column: 'category', op: '包含', value: '风扇' }
  ]
  const hit = filterRowsByConditions(rows, conds, 'material-frequency', monthKeys)
  assert.deepEqual(hit.map(r => r.key), ['A'], 'AND 组合只留同时满足两条的行（合计行也被滤除）')
})

test('无条件/全部空条件 → 原样返回（含合计行）', () => {
  const rows = [row(), { key: '合计' }]
  assert.equal(filterRowsByConditions(rows, [], 'material-frequency', monthKeys).length, 2)
  assert.equal(filterRowsByConditions(rows, [{ column: 'partName', op: '包含', value: '' }], 'material-frequency', monthKeys).length, 2)
})

test('金额列数值筛选（amount > 1000）', () => {
  const rows = [row({ key: 'A', amount: 2000 }), row({ key: 'B', amount: 500 })]
  const hit = filterRowsByConditions(rows, [{ column: 'amount', op: '>', value: '1000' }], 'material-frequency', monthKeys)
  assert.deepEqual(hit.map(r => r.key), ['A'])
})
