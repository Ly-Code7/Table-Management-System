# -*- coding: utf-8 -*-
"""
重算 unwarranted_material 的派生字段（第几次/总次数/上次日期/上次维修人/日期+编号/超六个月/使用时长）。

背景：批量导入时同组（同唯一标识编号）记录互不可见，导致同组同日多条"第几次/总次数"相同。
修复依据：库内 id 序 = Excel 原表行序（已全量验证 23171 行 uid 0 差异），行序即业务顺序。
算法与后端 UnwarrantedMaterialService.applyCalculations 保持一致：
  - 第几次     = 组内按 (record_date, id) 排序后的序号（从 1 开始）
  - 总次数     = 组大小
  - 上次日期   = 组内上一条的 record_date（无则 NULL）
  - 上次维修人 = 组内上一条的 repair_person（无则 NULL）
  - 本次日期+编号 = serialNo(record_date) + unique_id
  - 上次日期+编号 = 上次日期非空 ? serialNo(last_date) + unique_id : NULL
  - 超六个月   = 上次日期空 ? '1st' : (月差 >= 6 ? 'Y' : 'N')
  - 使用时长/月 = 上次日期空 ? '1st' : 整月数（零头舍弃）

用法：
  python fix_unwarranted_derived.py                    # dry-run：统计差异 + 与 Excel 原表对比
  python fix_unwarranted_derived.py --apply            # 写库（单事务，失败回滚）
  python fix_unwarranted_derived.py --company 13       # 指定公司（默认 13）
  python fix_unwarranted_derived.py --no-excel         # 跳过与 Excel 原表对比
"""
import argparse
import re
import sys
from datetime import date, timedelta

import pymysql

sys.stdout.reconfigure(encoding='utf-8')

EXCEL_DEFAULT = r'C:/Users/Administrator/Desktop/金属厂--2607--截止2026年8月1日最新版.xlsx'
DERIVED_COLS = [
    'occurrence_no', 'total_count', 'last_date', 'last_repair_person',
    'last_date_no', 'current_date_no', 'over_six_months', 'usage_months',
]


def load_db_config():
    cfg = open('backend/src/main/resources/application.yml', encoding='utf-8').read()
    url = re.search(r'url:\s*(jdbc:mysql://[^?]+)', cfg).group(1)
    m = re.match(r'jdbc:mysql://([^:]+):(\d+)/([^?]+)', url)
    return {
        'host': m.group(1), 'port': int(m.group(2)),
        'user': re.search(r'username:\s*(\S+)', cfg).group(1),
        'password': re.search(r'password:\s*(\S+)', cfg).group(1),
        'database': m.group(3),
    }


def serial_no(d):
    """Excel 序列号：1899-12-31 对应数值 0，1900-01-01 对应数值 1（复现 Excel 1900 闰年处理）。"""
    return (d - date(1899, 12, 31)).days + 1


def whole_months(frm, to):
    """整月数：只统计满整月，零头舍弃（例 2025-01-20 ~ 2025-03-10 → 1）。"""
    months = (to.year - frm.year) * 12 + (to.month - frm.month)
    if to.day < frm.day:
        months -= 1
    return max(months, 0)


def month_diff(frm, to):
    """与 ChronoUnit.MONTHS.between 对齐（满月按日判断，不足月不计）。"""
    return (to.year - frm.year) * 12 + (to.month - frm.month) - (1 if to.day < frm.day else 0)


def compute_group(rows):
    """组内按 id 序（= 导入序 = 原表行序，已全量验证）逐条计算派生值，返回 [(id, values_dict), ...]。
    注意不按日期重排：原表少量组内日期乱序，但"第几次"始终按行序编号，行序即业务顺序。"""
    rows.sort(key=lambda r: r['id'])
    out = []
    for k, r in enumerate(rows):
        d = r['record_date']
        uid = r['unique_id']
        prev = rows[k - 1] if k > 0 else None
        last_date = prev['record_date'] if prev else None
        last_person = prev['repair_person'] if prev else None
        last_no = (str(serial_no(last_date)) + uid) if last_date else None
        if last_date is None:
            over6, usage = '1st', '1st'
        else:
            over6 = 'Y' if month_diff(last_date, d) >= 6 else 'N'
            usage = str(whole_months(last_date, d))
        out.append((r['id'], {
            'occurrence_no': k + 1,
            'total_count': len(rows),
            'last_date': last_date,
            'last_repair_person': last_person,
            'last_date_no': last_no,
            'current_date_no': str(serial_no(d)) + uid,
            'over_six_months': over6,
            'usage_months': usage,
        }))
    return out


def load_excel_original(path, sheet='3.未过保物料'):
    """读 Excel 原表：返回 [dict]，含唯一标识编号/第几次/总次数/上次日期/上次维修人/超六个月/使用时长/本次日期+编号"""
    import openpyxl
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb[sheet]
    rows = ws.iter_rows(values_only=True)
    header = [str(h).strip() if h else '' for h in next(rows)]
    idx = {name: i for i, name in enumerate(header)}
    out = []
    for row in rows:
        uid = str(row[idx['唯一标识编号']]).strip() if row[idx['唯一标识编号']] else None
        if not uid:
            continue
        d = row[idx['日期']]
        rec = {
            'unique_id': uid,
            'record_date': d.date() if hasattr(d, 'date') else None,
            'occurrence_no': row[idx['第几次']] if isinstance(row[idx['第几次']], (int, float)) else None,
            'total_count': row[idx['总次数']] if isinstance(row[idx['总次数']], (int, float)) else None,
            'last_date': row[idx['上次日期']].date() if hasattr(row[idx['上次日期']], 'date') else None,
            # 辅助列-T1 = 上次维修人（原表无"上次维修人"列，按用户确认映射）
            'last_repair_person': row[idx['辅助列-T1']] if row[idx['辅助列-T1']] else None,
            'current_date_no': str(row[idx['本次日期+编号']]).strip() if row[idx['本次日期+编号']] else None,
            'over_six_months': row[idx['超六个月']] if row[idx['超六个月']] else None,
            'usage_months': row[idx['使用时长/月']] if row[idx['使用时长/月']] else None,
        }
        out.append(rec)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--company', type=int, default=13, help='公司 id（默认 13=6月数据）')
    ap.add_argument('--apply', action='store_true', help='写库（默认仅 dry-run）')
    ap.add_argument('--no-excel', action='store_true', help='跳过与 Excel 原表对比')
    args = ap.parse_args()

    db = load_db_config()
    conn = pymysql.connect(host=db['host'], port=db['port'], user=db['user'],
                           password=db['password'], database=db['database'], charset='utf8mb4')
    cur = conn.cursor(pymysql.cursors.DictCursor)

    cur.execute("""SELECT id, record_date, factory, machine_no, material_code, repair_person,
                          unique_id FROM unwarranted_material
                   WHERE company_id = %s ORDER BY id""", (args.company,))
    rows = cur.fetchall()
    print(f'公司 {args.company}: 共 {len(rows)} 行')

    # 分组
    groups = {}
    for r in rows:
        if not r['unique_id']:
            continue  # 无唯一编号的行不参与重算
        groups.setdefault(r['unique_id'], []).append(r)
    print(f'有唯一编号的分组数: {len(groups)}')

    updates = []
    for uid, g in groups.items():
        updates.extend(compute_group(g))
    print(f'待更新行数: {len(updates)}')

    # 与原表对比（dry-run 证据）
    if not args.no_excel:
        xl = load_excel_original(EXCEL_DEFAULT)
        xl_by_uid = {}
        for r in xl:
            xl_by_uid.setdefault(r['unique_id'], []).append(r)
        mismatch = {'occurrence_no': 0, 'total_count': 0, 'last_date': 0,
                    'last_repair_person': 0, 'current_date_no': 0, 'over_six_months': 0, 'usage_months': 0}
        checked = {'occurrence_no': 0, 'total_count': 0, 'last_date': 0,
                   'last_repair_person': 0, 'current_date_no': 0, 'over_six_months': 0, 'usage_months': 0}
        for uid, g in groups.items():
            xrows = xl_by_uid.get(uid, [])
            if not xrows:
                print(f'  [警告] 原表无此编号: {uid} ({len(g)} 行)')
                continue
            # 库内 id 序 = 原表行序（已验证），按序对齐
            g_sorted = sorted(g, key=lambda r: r['id'])
            vals_by_id = {rid: v for rid, v in compute_group(g)}
            for i, (dbr, xr) in enumerate(zip(g_sorted, xrows)):
                if i >= len(g) or i >= len(xrows):
                    break
                vals = vals_by_id[dbr['id']]
                for key, xval in [('occurrence_no', xr['occurrence_no']), ('total_count', xr['total_count']),
                                  ('last_date', xr['last_date']), ('last_repair_person', xr['last_repair_person']),
                                  ('current_date_no', xr['current_date_no']), ('over_six_months', xr['over_six_months']),
                                  ('usage_months', xr['usage_months'])]:
                    if xval is not None:
                        checked[key] += 1
                        if key in ('occurrence_no', 'total_count'):
                            ok = int(vals[key]) == int(xval)
                        elif key == 'last_date':
                            ok = (vals[key] or None) == (xval or None)
                        else:
                            ok = str(vals[key] or '') == str(xval or '')
                        if not ok:
                            mismatch[key] += 1
        print('=== 与原表对比（重算值 vs 原表值）===')
        for k in mismatch:
            print(f'  {k}: 对比 {checked[k]} 行, 不一致 {mismatch[k]} 行')
        if sum(mismatch.values()) == 0:
            print('  ✓ 全部一致')
        else:
            print('  ✗ 存在差异（见上）')

    # 抽样展示
    print('=== 抽样（B5-I0815297020-00）===')
    for uid, g in groups.items():
        if 'B5-I0815297020-00' in uid:
            for rid, vals in compute_group(g):
                print(f'  id={rid} date={vals["last_date"]} occ={vals["occurrence_no"]} total={vals["total_count"]} last_person={vals["last_repair_person"]}')

    if not args.apply:
        print('\n[dry-run] 未写库。确认无误后加 --apply 执行。')
        conn.close()
        return

    # 写库（单事务）
    try:
        sql = ('UPDATE unwarranted_material SET occurrence_no=%s, total_count=%s, last_date=%s, '
               'last_repair_person=%s, last_date_no=%s, current_date_no=%s, over_six_months=%s, usage_months=%s '
               'WHERE id=%s')
        cur.executemany(sql, [(v['occurrence_no'], v['total_count'], v['last_date'], v['last_repair_person'],
                               v['last_date_no'], v['current_date_no'], v['over_six_months'], v['usage_months'], rid)
                              for rid, v in updates])
        conn.commit()
        print(f'\n✓ 已更新 {len(updates)} 行（公司 {args.company}）')
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == '__main__':
    main()
