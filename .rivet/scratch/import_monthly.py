# -*- coding: utf-8 -*-
"""按月导入维修记录到公司13（临时探针脚本，收尾清理）。"""
import base64
import hashlib
import hmac
import io
import json
import re
import sys
import time

import requests

sys.stdout.reconfigure(encoding='utf-8')

BASE = 'http://localhost:8080/api/original-record'
COMPANY_ID = 13


def make_token():
    cfg = open('backend/src/main/resources/application.yml', encoding='utf-8').read()
    secret = re.search(r'jwt:\s*\n\s+secret:\s*(\S+)', cfg).group(1)

    def b64url(d):
        return base64.urlsafe_b64encode(d).rstrip(b'=').decode()

    now = int(time.time())
    h = b64url(json.dumps({'alg': 'HS256'}).encode())
    p = b64url(json.dumps({'userId': 2, 'username': '18720647482', 'realName': 'admin',
                           'role': 'admin', 'sub': '18720647482', 'iat': now, 'exp': now + 3600}).encode())
    s = b64url(hmac.new(secret.encode(), f'{h}.{p}'.encode(), hashlib.sha256).digest())
    return f'{h}.{p}.{s}'


def import_file(path):
    with open(path, 'rb') as f:
        data = f.read()
    files = {'file': (path.split('/')[-1], io.BytesIO(data),
                      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')}
    r = requests.post(f'{BASE}/import', params={'companyId': COMPANY_ID},
                      headers={'Authorization': f'Bearer {make_token()}'},
                      files=files, timeout=1800)
    d = r.json()
    if d.get('code') != 200:
        return {'error': r.text[:500]}
    return d['data']


if __name__ == '__main__':
    months = sys.argv[1:] or ['2026-01', '2026-02', '2026-03', '2026-04',
                              '2026-05', '2026-06', '2026-07']
    for m in months:
        path = f'.rivet/scratch/monthly_import/维修记录_{m}.xlsx'
        t0 = time.time()
        res = import_file(path)
        dt = time.time() - t0
        if 'error' in res:
            print(f'{m}: ERROR {res["error"]}', flush=True)
            continue
        fails = res.get('failDetails') or []
        print(f'{m}: total={res.get("total")} success={res.get("success")} '
              f'fail={res.get("fail")} failDetail0={fails[0] if fails else None} '
              f'耗时={dt:.1f}s', flush=True)
