"""Geofabrik 한국 OSM 추출본(.osm.pbf) 1회 다운로드.

서울 보행 네트워크를 osmnx.graph_from_place("Seoul...")로 직접 받으면 Overpass에 거대한
그래프 쿼리를 날리게 되어 타임아웃/메모리 위험이 크다. 대신 Geofabrik의 한국 전체 pbf를
로컬에 받아두고, pyrosm으로 서울 경계만 잘라 쓴다 (실제 추출은 WSL 쪽 스크립트가 수행 —
pyrosm의 의존 패키지 cykhash가 Windows용 사전 빌드 wheel이 없어 이 파일은 다운로드만
담당하고 pyrosm은 import하지 않는다).

실행: cd batch && python -m src.fetch_osm_pbf
"""
from __future__ import annotations

import os

import requests

PBF_URL = "https://download.geofabrik.de/asia/south-korea-latest.osm.pbf"
RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "data", "raw")
PBF_PATH = os.path.join(RAW_DIR, "south-korea-latest.osm.pbf")

CHUNK_SIZE = 1024 * 1024  # 1MB


def ensure_pbf(path: str = PBF_PATH, url: str = PBF_URL) -> str:
    """path에 이미 파일이 있으면 그대로 반환하고, 없으면 받는다.

    중간에 끊겨도 손상된 파일이 최종 경로에 남지 않도록 .tmp로 받은 뒤 성공 시에만
    rename한다.
    """
    if os.path.exists(path) and os.path.getsize(path) > 0:
        print(f"이미 존재함, 다운로드 생략: {path} ({os.path.getsize(path):,} bytes)")
        return path

    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp_path = path + ".tmp"

    with requests.get(url, stream=True, timeout=60) as resp:
        resp.raise_for_status()
        total = int(resp.headers.get("content-length", 0))
        downloaded = 0
        with open(tmp_path, "wb") as f:
            for chunk in resp.iter_content(chunk_size=CHUNK_SIZE):
                f.write(chunk)
                downloaded += len(chunk)
                if total:
                    pct = downloaded / total * 100
                    print(f"\r다운로드 중: {downloaded:,}/{total:,} bytes ({pct:.1f}%)", end="", flush=True)

    print()
    os.replace(tmp_path, path)
    print(f"다운로드 완료: {path} ({os.path.getsize(path):,} bytes)")
    return path


def main() -> None:
    ensure_pbf()


if __name__ == "__main__":
    main()
