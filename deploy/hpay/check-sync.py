#!/usr/bin/env python3
"""Read-only extension survival check after merging upstream. Optional argument: upstream ref."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
backend = 'aid-business/aid-business-main/src/main/java/com/sakura/extensions/hpay/'
tests = 'aid-business/aid-business-main/src/test/java/com/sakura/extensions/hpay/'
files = [backend + name + '.java' for name in ['HPayClient', 'HPayController', 'HPayOrderStore', 'HPayProperties', 'HPayProtocol', 'HPayScheduling', 'HPayService']]
files += [tests + name + '.java' for name in ['HPayClientTest', 'HPayProtocolTest', 'HPayServiceTest']]
files += ['frontend/creator/extensions/hpay/' + name for name in ['api.ts', 'HPayPanel.tsx', 'HPayPanel.test.tsx', 'hpay.css']]
files += ['deploy/hpay/schema.sql', 'deploy/hpay/application-hpay.example.yml', 'deploy/hpay/check-sync.py']
errors = [f'Missing extension file: {name}' for name in files if not (root / name).is_file()]
page = root / 'frontend/creator/app/(home)/billing/page.tsx'
if not page.exists() or "~/extensions/hpay/HPayPanel" not in page.read_text() or '<HPayPanel ' not in page.read_text():
    errors.append('Billing page no longer mounts HPayPanel')
account = root / 'aid-interface/aid-interface-main/src/main/java/com/sakura/billing/service/IAccountUpdateService.java'
if not account.exists() or 'void recharge(' not in account.read_text():
    errors.append('Account recharge contract changed; review adapter')
if len(sys.argv) > 1:
    result = subprocess.run(['git', '-C', str(root), 'ls-tree', '-r', '--name-only', sys.argv[1], '--', *files], capture_output=True, text=True)
    if result.returncode:
        errors.append('Unable to resolve upstream ref')
    elif result.stdout.strip():
        errors.append('Upstream now owns extension paths; inspect collisions:\n' + result.stdout.strip())
if errors:
    print('\n'.join(errors)); sys.exit(1)
print('HPay files and billing mount present. Run HPay backend/UI tests before release; this is not a merge or deployment tool.')
