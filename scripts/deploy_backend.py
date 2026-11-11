#!/usr/bin/env python3
# 승인된 커밋의 서버를 원자 교체하고 API 준비 실패 시 이전 JAR로 복구한다.
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile


def check_approval(revision, policy_path):
    if not re.fullmatch(r'[a-f0-9]{40}', revision):
        raise ValueError('배포 커밋 식별자가 올바르지 않습니다.')
    policy_path = Path(policy_path)
    info = policy_path.stat()
    if policy_path.is_symlink() or info.st_uid != 0 or stat.S_IMODE(info.st_mode) & 0o022:
        raise ValueError('호환성 승인 파일은 관리자 소유이며 다른 사용자가 쓸 수 없어야 합니다.')
    policy = json.loads(policy_path.read_text())
    if policy.get('approvedRevision') != revision or policy.get('rollbackCompatible') is not True:
        raise ValueError('해당 커밋의 DB 호환성 승인 후 배포할 수 있습니다. 실행 중인 서버는 변경하지 않습니다.')


def ready(service, url, attempts=60, interval=2):
    for attempt in range(attempts):
        active = subprocess.run(['systemctl', 'is-active', '--quiet', service], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
        if active:
            try:
                request = urllib.request.Request(url, headers={'Accept': 'application/json', 'Cache-Control': 'no-cache'})
                with urllib.request.urlopen(request, timeout=4) as response:
                    if response.status == 200 and 'application/json' in response.headers.get('Content-Type', ''):
                        body = response.read(4 * 1024 * 1024 + 1)
                        if len(body) <= 4 * 1024 * 1024 and isinstance(json.loads(body), list):
                            return True
            except Exception:
                pass
        if attempt + 1 < attempts:
            time.sleep(interval)
    return False


def replace_file(source, target, owner):
    descriptor, staging_name = tempfile.mkstemp(prefix='.kuji-jar-', dir=target.parent)
    staging = Path(staging_name)
    try:
        with os.fdopen(descriptor, 'wb') as output, Path(source).open('rb') as input_file:
            shutil.copyfileobj(input_file, output)
            output.flush(); os.fsync(output.fileno())
        os.chmod(staging, 0o600)
        os.chown(staging, owner[0], owner[1])
        os.replace(staging, target)
    finally:
        staging.unlink(missing_ok=True)


def deploy(artifact, target, checksum, revision, policy_path, service='kuji-server', url='http://127.0.0.1:8080/api/kuji', backup_root=Path('/var/backups/kuji-releases/backend'), attempts=60, interval=2):
    check_approval(revision, policy_path)
    artifact, target = Path(artifact), Path(target)
    if artifact.is_symlink() or not artifact.is_file() or target.is_symlink() or not target.is_file():
        raise ValueError('새 JAR와 기존 서버 파일을 확인해 주세요.')
    expected = Path(checksum).read_text().split()[0]
    hasher = hashlib.sha256()
    with artifact.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            hasher.update(block)
    actual = hasher.hexdigest()
    if not re.fullmatch(r'[a-f0-9]{64}', expected) or actual != expected:
        raise ValueError('서버 JAR 해시가 일치하지 않습니다.')
    with zipfile.ZipFile(artifact) as archive:
        if not any(name.startswith('BOOT-INF/classes/') for name in archive.namelist()) or archive.testzip() is not None:
            raise ValueError('실행할 Spring Boot JAR 구조가 올바르지 않습니다.')
    previous = target.stat()
    owner = (previous.st_uid, previous.st_gid)
    backup_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(backup_root, 0o700)
    backup = backup_root / (revision + '-' + str(time.time_ns()) + '.jar')
    shutil.copyfile(target, backup)
    os.chmod(backup, 0o600)
    replaced = False
    try:
        replace_file(artifact, target, owner); replaced = True
        subprocess.run(['systemctl', 'restart', service], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if not ready(service, url, attempts, interval):
            raise RuntimeError('새 서버의 API 준비 확인이 실패했습니다.')
        return {'deployment': 'passed', 'revision': revision, 'readiness': 'passed', 'rollback': 'not-needed'}
    except Exception:
        if replaced:
            replace_file(backup, target, owner)
            subprocess.run(['systemctl', 'restart', service], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            recovered = ready(service, url, attempts, interval)
            print(json.dumps({'deployment': 'failed', 'revision': revision, 'rollback': 'passed' if recovered else 'failed'}), file=sys.stderr)
        raise RuntimeError('서버 배포가 실패했습니다. 이전 버전 복구 결과를 확인해 주세요.') from None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact', type=Path)
    parser.add_argument('--target', type=Path, default=Path('/home/ubuntu/kuji-server.jar'))
    parser.add_argument('--checksum', type=Path)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--policy', type=Path, default=Path('/etc/kuji-deploy/rollback-policy.json'))
    parser.add_argument('--check-approval', action='store_true')
    args = parser.parse_args()
    try:
        if args.check_approval:
            check_approval(args.revision, args.policy)
            print('해당 커밋의 DB 호환성 승인을 확인했습니다.')
            return 0
        if not args.artifact or not args.checksum:
            raise ValueError('JAR와 해시 파일이 필요합니다.')
        with Path('/run/lock/kuji-backend-deploy.lock').open('w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            print(json.dumps(deploy(args.artifact, args.target, args.checksum, args.revision, args.policy)))
        return 0
    except Exception as error:
        message = str(error) if isinstance(error, (ValueError, RuntimeError)) else '서버 배포 사전 검사가 실패했습니다.'
        print(json.dumps({'deployment': 'failed', 'errorType': type(error).__name__, 'message': message}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
