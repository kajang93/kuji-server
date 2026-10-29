#!/usr/bin/env python3
# 결제 데이터와 비공개 등록증을 같은 스냅샷 기준으로 백업하고 무결성을 검사합니다.
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import subprocess
import tarfile
import tempfile
from datetime import datetime, timezone

BASE = Path('/var/backups/kuji')
DOCUMENTS = Path('/home/ubuntu/private/business-licenses')
PSQL = ['docker', 'exec', '-i', 'kuji-postgres', 'sh', '-c', 'exec psql -X -q -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "${POSTGRES_DB:-kuji_db}"']


def main():
    os.umask(0o077)
    BASE.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(BASE, 0o700)
    with Path('/run/lock/kuji-backup.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        run_backup()


def run_backup():
    destination = BASE / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    if destination.exists():
        raise RuntimeError('같은 시각의 백업이 이미 존재합니다.')
    staging = Path(tempfile.mkdtemp(prefix='.pending-', dir=BASE))
    session = subprocess.Popen(PSQL, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        # 등록증 DB 참조가 백업 중 바뀌어 원본 파일이 삭제되는 것을 막습니다.
        session.stdin.write("BEGIN ISOLATION LEVEL REPEATABLE READ;\nSET LOCAL lock_timeout = '5s';\nLOCK TABLE business_info IN SHARE MODE;\nSELECT pg_export_snapshot();\n")
        session.stdin.flush()
        ready = selectors.DefaultSelector()
        ready.register(session.stdout, selectors.EVENT_READ)
        if not ready.select(timeout=15):
            raise RuntimeError('백업 스냅샷 잠금 대기 시간이 지났습니다.')
        snapshot = session.stdout.readline().strip()
        ready.close()
        if not re.fullmatch(r'[0-9A-Fa-f-]+', snapshot):
            raise RuntimeError('백업 스냅샷을 생성하지 못했습니다.')
        session.stdin.write("SELECT json_build_object('payment', (SELECT count(*) FROM payment), 'pointhistory', (SELECT count(*) FROM pointhistory), 'drawhistory', (SELECT count(*) FROM drawhistory), 'business_info', (SELECT count(*) FROM business_info));\n")
        session.stdin.flush()
        counts = json.loads(session.stdout.readline())
        dump = staging / 'database.dump'
        with dump.open('wb') as output:
            subprocess.run(['docker', 'exec', 'kuji-postgres', 'sh', '-c',
                'exec pg_dump --format=custom --no-owner --no-acl --snapshot="$1" -U "$POSTGRES_USER" -d "${POSTGRES_DB:-kuji_db}"',
                'sh', snapshot], stdout=output, stderr=subprocess.PIPE, check=True, timeout=300)
        subprocess.run(['docker', 'exec', '-i', 'kuji-postgres', 'pg_restore', '--list'],
            input=dump.read_bytes(), stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True, timeout=30)
        with tarfile.open(staging / 'business-licenses.tar.gz', 'w:gz') as archive:
            if DOCUMENTS.is_dir():
                for file in sorted(DOCUMENTS.iterdir()):
                    if file.is_symlink() or not file.is_file():
                        raise RuntimeError('등록증 경로에 일반 파일이 아닌 항목이 있습니다.')
                    archive.add(file, arcname='business-licenses/' + file.name)
        session.communicate('COMMIT;\n', timeout=15)
        if session.returncode:
            raise RuntimeError('백업 스냅샷을 종료하지 못했습니다.')
        manifest = {'createdAt': datetime.now(timezone.utc).isoformat(), 'scope': 'database-and-private-business-licenses',
            'storage': 'same-server-local-only', 'retainedSuccessfulBackups': 30, 'rowCounts': counts,
            'files': {file.name: {'bytes': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
                      for file in staging.iterdir()}}
        (staging / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        staging.rename(destination)
        successful = sorted(path for path in BASE.iterdir() if path.is_dir() and re.fullmatch(r'\d{8}T\d{6}Z', path.name) and (path / 'manifest.json').is_file())
        for obsolete in successful[:-30]:
            shutil.rmtree(obsolete)
        print(json.dumps({'backup': destination.name, 'databaseBytes': dump.stat().st_size if dump.exists() else (destination/'database.dump').stat().st_size,
                          'documents': len(list(DOCUMENTS.iterdir())) if DOCUMENTS.exists() else 0, 'integrity': 'passed', 'offsite': False}))
    finally:
        if session.poll() is None:
            try:
                session.communicate('ROLLBACK;\n', timeout=5)
            except Exception:
                session.kill(); session.communicate()
        if staging.exists():
            shutil.rmtree(staging)


if __name__ == '__main__':
    main()
