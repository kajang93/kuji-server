#!/usr/bin/env python3
# 공개 상품 이미지 캐시를 검사 후 설치하고 실패하면 기존 설정으로 복구합니다.
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone


def install():
    if os.geteuid() != 0:
        raise SystemExit("관리자 권한으로 실행해 주세요.")
    source = Path(__file__).resolve().parent / "nginx"
    site = Path(sys.argv[1] if len(sys.argv) > 1 else "/etc/nginx/sites-available/default")
    text = site.read_text()
    include = "    include /etc/nginx/snippets/kuji-public-images.conf;"
    if include.strip() not in text:
        candidates = []
        for match in re.finditer(r"\bserver\s*\{", text):
            depth, end = 1, match.end()
            while depth and end < len(text):
                depth += (text[end] == "{") - (text[end] == "}")
                end += 1
            block = text[match.start():end]
            if re.search(r"\blisten\s+(?:\[::\]:)?443\b", block) and re.search(r"\bserver_name\s+[^;]*\bkujishop\.shop\b", block):
                anchor = re.search(r"(?m)^.*\bserver_name\b[^\n]*", block)
                candidates.append(match.start() + anchor.end())
        if len(candidates) != 1:
            raise SystemExit("쿠지 HTTPS 서버 설정을 하나로 식별할 수 없습니다.")
        position = candidates[0]
        text = text[:position] + "\n" + include + text[position:]

    targets = {
        Path("/etc/nginx/conf.d/kuji-image-cache.conf"): (source / "kuji-image-cache.conf").read_bytes(),
        Path("/etc/nginx/snippets/kuji-public-images.conf"): (source / "kuji-public-images.conf").read_bytes(),
        site: text.encode(),
    }
    previous = {path: path.read_bytes() if path.exists() else None for path in targets}
    if all(previous[path] == content for path, content in targets.items()):
        subprocess.run(["nginx", "-t"], check=True)
        print("공개 이미지 캐시 설정이 이미 적용되어 있습니다.")
        return
    backup = Path("/var/backups/kuji-nginx") / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    backup.mkdir(parents=True, mode=0o700)
    os.chmod(backup.parent, 0o700)
    for number, (path, content) in enumerate(previous.items()):
        if content is not None:
            saved = backup / f"{number}-{path.name}"
            saved.write_bytes(content)
            os.chmod(saved, 0o600)
    Path("/var/cache/nginx/kuji-images").mkdir(parents=True, exist_ok=True)
    shutil.chown("/var/cache/nginx/kuji-images", user="www-data", group="www-data")
    try:
        for path, content in targets.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            os.chmod(path, 0o644)
        subprocess.run(["nginx", "-t"], check=True)
        subprocess.run(["systemctl", "reload", "nginx"], check=True)
    except Exception:
        for path, content in previous.items():
            if content is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(content)
        subprocess.run(["nginx", "-t"], check=False)
        subprocess.run(["systemctl", "reload", "nginx"], check=False)
        raise
    print("공개 이미지 캐시를 설치하고 설정 검사를 통과했습니다.")


if __name__ == "__main__":
    install()
