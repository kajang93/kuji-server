#!/usr/bin/env python3
# 백업을 네트워크가 없는 임시 PostgreSQL과 비공개 디렉터리에 복원해 대조한다.
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import tempfile
import time
import uuid
from backup_integrity import BACKUP_NAME, download_offsite, s3_settings, verify_backup

BASE = Path('/var/backups/kuji')
COUNT_TABLES = {'payment', 'pointhistory', 'drawhistory', 'business_info', 'paymentsession', 'seller_sale', 'seller_sale_draw', 'seller_payout', 'seller_payout_allocation', 'refund_request'}


def command(args, **kwargs):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)


def validate_restore(directory, image=None):
    directory = Path(directory)
    manifest = verify_backup(directory)
    expected = manifest['rowCounts']
    if not {'payment', 'pointhistory', 'drawhistory', 'business_info'}.issubset(expected) or not set(expected).issubset(COUNT_TABLES) or any(type(value) is not int or value < 0 for value in expected.values()):
        raise ValueError('기준 행수의 형식이 올바르지 않습니다.')
    if image is None:
        image = command(['docker', 'inspect', '--format={{.Config.Image}}', 'kuji-postgres'], text=True).stdout.strip()
    if not image or any(char.isspace() for char in image):
        raise ValueError('복원 검사에 사용할 기존 PostgreSQL 이미지가 필요합니다.')
    name = 'kuji-restore-check-' + uuid.uuid4().hex
    started = False
    try:
        command(['docker', 'run', '-d', '--pull=never', '--network=none', '--name', name,
                 '--label', 'kuji.restore-check=true', '--memory=512m', '--cpus=0.5',
                 '-e', 'POSTGRES_USER=kuji_restore', '-e', 'POSTGRES_DB=kuji_restore',
                 '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', image])
        started = True
        for attempt in range(60):
            result = subprocess.run(['docker', 'exec', name, 'pg_isready', '-U', 'kuji_restore', '-d', 'kuji_restore'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('격리된 복원 DB를 준비하지 못했습니다.')
        with (directory / 'database.dump').open('rb') as source:
            command(['docker', 'exec', '-i', name, 'pg_restore', '--exit-on-error', '--no-owner', '--no-acl', '-U', 'kuji_restore', '-d', 'kuji_restore'], stdin=source, timeout=600)
        psql = ['docker', 'exec', '-i', name, 'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', 'kuji_restore', '-d', 'kuji_restore']
        counts_sql = "SELECT json_build_object(" + ",".join("'" + name + "',(SELECT count(*) FROM " + name + ")" for name in sorted(expected)) + ");"
        counts = json.loads(command(psql, input=counts_sql, text=True).stdout)
        if counts != expected:
            raise ValueError('격리 복원 DB의 행수가 스냅샷 기준과 다릅니다.')
        invalid = command(psql, input="SELECT count(*) FROM pg_constraint WHERE contype='f' AND NOT convalidated;", text=True).stdout.strip()
        if invalid != '0':
            raise ValueError('복원 DB의 외래키 검증이 완료되지 않았습니다.')
        keys = command(psql, input="SELECT license_image_url FROM business_info WHERE license_image_url LIKE 'private-license:%';", text=True).stdout.splitlines()
        with tempfile.TemporaryDirectory(prefix='kuji-restored-documents-') as folder:
            restored = Path(folder)
            with tarfile.open(directory / 'business-licenses.tar.gz', 'r:gz') as archive:
                for member in archive.getmembers():
                    target = restored / Path(member.name).name
                    with archive.extractfile(member) as source, target.open('wb') as output:
                        for block in iter(lambda: source.read(1024 * 1024), b''):
                            output.write(block)
                    os.chmod(target, 0o600)
            for key in keys:
                if not re.fullmatch(r'private-license:[0-9a-f-]{36}\.(jpg|png|pdf)', key) or not (restored / key.split(':', 1)[1]).is_file():
                    raise ValueError('복원한 등록증과 DB 참조가 일치하지 않습니다.')
        return {'restore': 'passed', 'rowCountsMatched': True, 'foreignKeys': 'validated', 'privateDocumentReferences': len(keys)}
    finally:
        if started:
            cleanup = subprocess.run(['docker', 'rm', '-f', '-v', name], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            if cleanup.returncode:
                raise RuntimeError('격리 복원 컨테이너 정리에 실패했습니다.')


def main():
    parser = argparse.ArgumentParser(description='운영 DB를 바꾸지 않고 백업 복원을 검사합니다.')
    parser.add_argument('--backup', type=Path)
    parser.add_argument('--s3-key')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        with Path('/run/lock/kuji-restore-check.lock').open('w') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                print(json.dumps({'status': 'skipped', 'reason': 'restore-check-in-progress'}))
                return 0
            if args.s3_key:
                with tempfile.TemporaryDirectory(prefix='kuji-offsite-restore-') as scratch:
                    directory = download_offsite(args.s3_key, Path(scratch) / 'backup')
                    result = validate_restore(directory)
                    result['source'] = 'offsite'
            else:
                candidates = sorted(path for path in BASE.iterdir() if BACKUP_NAME.fullmatch(path.name) and (path / 'manifest.json').is_file()) if BASE.exists() else []
                directory = args.backup or (candidates[-1] if candidates else None)
                if directory is None:
                    raise ValueError('복원 검증할 성공 백업이 없습니다.')
                receipt = directory / 'offsite.json'
                if not args.backup and s3_settings():
                    if not receipt.is_file():
                        raise ValueError('최신 백업의 외부 사본 확인 기록이 없습니다.')
                    key = json.loads(receipt.read_text())['key']
                    with tempfile.TemporaryDirectory(prefix='kuji-offsite-restore-') as scratch:
                        downloaded = download_offsite(key, Path(scratch) / 'backup')
                        result = validate_restore(downloaded)
                        result['source'] = 'offsite'
                else:
                    result = validate_restore(directory)
                    result['source'] = 'local'
            print(json.dumps(result))
            return 0
    except Exception as error:
        message = str(error) if isinstance(error, (ValueError, RuntimeError)) else '격리 복원 검증 단계가 실패했습니다.'
        print(json.dumps({'restore': 'failed', 'errorType': type(error).__name__, 'message': message}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
