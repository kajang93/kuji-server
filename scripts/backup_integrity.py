#!/usr/bin/env python3
# 백업 해시·비공개 서류 구조를 검사하고 승인된 비공개 S3 사본을 검증한다.
import hashlib
import json
import os
from pathlib import Path
import re
import tarfile
import tempfile

PAYLOADS = {'database.dump', 'business-licenses.tar.gz'}
BACKUP_NAME = re.compile(r'\d{8}T\d{6}Z')
DOCUMENT_NAME = re.compile(r'business-licenses/[0-9a-f-]{36}\.(?:jpg|png|pdf)')


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def verify_backup(directory):
    directory = Path(directory)
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError('백업 경로가 올바르지 않습니다.')
    manifest = json.loads((directory / 'manifest.json').read_text())
    files = manifest.get('files', {})
    if set(files) != PAYLOADS:
        raise ValueError('백업 구성 파일이 일치하지 않습니다.')
    for name, expected in files.items():
        path = directory / name
        if path.is_symlink() or not path.is_file() or path.stat().st_size != expected['bytes'] or digest(path) != expected['sha256']:
            raise ValueError('백업 파일 크기 또는 해시가 일치하지 않습니다.')
    with tarfile.open(directory / 'business-licenses.tar.gz', 'r:gz') as archive:
        names = set()
        for member in archive.getmembers():
            if not member.isfile() or not DOCUMENT_NAME.fullmatch(member.name) or member.name in names or member.size > 5 * 1024 * 1024:
                raise ValueError('등록증 백업 구성에 허용되지 않는 항목이 있습니다.')
            names.add(member.name)
    if not isinstance(manifest.get('rowCounts'), dict):
        raise ValueError('백업 기준 행수가 없습니다.')
    return manifest


def s3_settings():
    bucket = os.environ.get('KUJI_BACKUP_S3_BUCKET', '').strip()
    if not bucket:
        if os.environ.get('KUJI_BACKUP_OFFSITE_REQUIRED', '0') == '1':
            raise ValueError('필수 외부 백업 수신처가 설정되지 않았습니다.')
        return None
    owner = os.environ.get('KUJI_BACKUP_S3_EXPECTED_OWNER', '').strip()
    region = os.environ.get('KUJI_BACKUP_S3_REGION', 'ap-northeast-2').strip()
    prefix = os.environ.get('KUJI_BACKUP_S3_PREFIX', 'kuji-production').strip('/')
    if not re.fullmatch(r'kujishop-private-backups-apne2-[a-z0-9-]+', bucket) or not re.fullmatch(r'\d{12}', owner):
        raise ValueError('승인된 전용 백업 버킷과 소유 계정이 필요합니다.')
    if region != 'ap-northeast-2' or not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9/_-]{0,100}', prefix) or '..' in prefix:
        raise ValueError('백업 리전 또는 접두사가 올바르지 않습니다.')
    return {'bucket': bucket, 'owner': owner, 'region': region, 'prefix': prefix}


def s3_client(settings):
    import boto3
    from botocore.config import Config
    return boto3.client('s3', region_name=settings['region'], config=Config(connect_timeout=10, read_timeout=60, retries={'max_attempts': 3, 'mode': 'standard'}))


def verify_destination(client, settings):
    args = {'Bucket': settings['bucket'], 'ExpectedBucketOwner': settings['owner']}
    controls = client.get_public_access_block(**args)['PublicAccessBlockConfiguration']
    if not all(controls.get(name) is True for name in ['BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets']):
        raise ValueError('공개 접근이 완전히 차단된 버킷만 사용할 수 있습니다.')
    location = client.get_bucket_location(**args).get('LocationConstraint') or 'us-east-1'
    if location != settings['region']:
        raise ValueError('승인된 서울 리전 버킷이 아닙니다.')


def read_object(client, settings, key, destination=None):
    response = client.get_object(Bucket=settings['bucket'], Key=key, ExpectedBucketOwner=settings['owner'])
    if response.get('ServerSideEncryption') != 'AES256':
        response['Body'].close()
        raise ValueError('외부 사본의 저장 암호화를 확인하지 못했습니다.')
    expected = response.get('Metadata', {}).get('sha256', '')
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        response['Body'].close()
        raise ValueError('외부 사본의 기준 해시가 없습니다.')
    actual = hashlib.sha256()
    output = Path(destination).open('wb') if destination else None
    try:
        while True:
            block = response['Body'].read(1024 * 1024)
            if not block:
                break
            actual.update(block)
            if output:
                output.write(block)
    finally:
        response['Body'].close()
        if output:
            output.close()
    if actual.hexdigest() != expected:
        raise ValueError('외부 사본을 다시 읽은 해시가 일치하지 않습니다.')
    return expected


def upload_offsite(directory):
    settings = s3_settings()
    if not settings:
        return {'configured': False, 'verified': False}
    directory = Path(directory)
    verify_backup(directory)
    if not BACKUP_NAME.fullmatch(directory.name):
        raise ValueError('백업 시각 식별자가 올바르지 않습니다.')
    client = s3_client(settings)
    verify_destination(client, settings)
    key = f"{settings['prefix']}/{directory.name}/backup.tar.gz"
    with tempfile.TemporaryDirectory(prefix='.offsite-', dir=directory.parent) as scratch:
        archive_path = Path(scratch) / 'backup.tar.gz'
        with tarfile.open(archive_path, 'w:gz') as archive:
            for name in sorted(PAYLOADS | {'manifest.json'}):
                archive.add(directory / name, arcname=name, recursive=False)
        expected = digest(archive_path)
        client.upload_file(str(archive_path), settings['bucket'], key, ExtraArgs={
            'ServerSideEncryption': 'AES256', 'ExpectedBucketOwner': settings['owner'], 'ChecksumAlgorithm': 'SHA256',
            'ContentType': 'application/gzip', 'Metadata': {'sha256': expected, 'scope': 'kuji-private-backup'},
        })
        if read_object(client, settings, key) != expected:
            raise ValueError('외부 사본의 원본 해시가 일치하지 않습니다.')
    receipt = {'bucket': settings['bucket'], 'key': key, 'region': settings['region'], 'sha256': expected, 'verified': True}
    (directory / 'offsite.json').write_text(json.dumps(receipt) + '\n')
    os.chmod(directory / 'offsite.json', 0o600)
    return receipt


def download_offsite(key, destination):
    settings = s3_settings()
    if not settings or not key.startswith(settings['prefix'] + '/') or '..' in key:
        raise ValueError('승인된 외부 백업 키가 필요합니다.')
    client = s3_client(settings)
    verify_destination(client, settings)
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=False, mode=0o700)
    archive_path = destination / 'offsite.tar.gz'
    read_object(client, settings, key, archive_path)
    with tarfile.open(archive_path, 'r:gz') as archive:
        members = archive.getmembers()
        if {member.name for member in members} != PAYLOADS | {'manifest.json'} or len(members) != 3 or any(not member.isfile() for member in members):
            raise ValueError('외부 백업 압축 구조가 올바르지 않습니다.')
        for member in members:
            with archive.extractfile(member) as source, (destination / member.name).open('wb') as target:
                for block in iter(lambda: source.read(1024 * 1024), b''):
                    target.write(block)
            os.chmod(destination / member.name, 0o600)
    archive_path.unlink()
    verify_backup(destination)
    return destination
