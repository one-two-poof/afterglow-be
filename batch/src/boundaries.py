"""서울 전체 + 25개 자치구 행정 경계 폴리곤 로더.

osmnx.geocode_to_gdf는 Nominatim "search" 엔드포인트로 경계 폴리곤 하나만 가져오는
가벼운 조회다. osmnx.graph_from_place처럼 전체 도로 그래프를 Overpass에 요청하는 게
아니라서, 서울 전체를 대상으로 해도 타임아웃/메모리 위험이 크지 않다.

결과는 batch/cache/에 캐싱해 재실행 시 Nominatim을 다시 두드리지 않는다.
"""
from __future__ import annotations

import os
import time

import geopandas as gpd
import osmnx as ox
import pandas as pd

CACHE_DIR = os.path.join(os.path.dirname(__file__), "..", "cache")
SEOUL_BOUNDARY_PATH = os.path.join(CACHE_DIR, "seoul_boundary.geojson")
GU_BOUNDARIES_PATH = os.path.join(CACHE_DIR, "seoul_gu_boundaries.geojson")

SEOUL_QUERY = "Seoul, South Korea"

# 서울 25개 자치구 (buildings.py의 ADDR 파싱 결과와 표기를 맞춘다: "OO구")
SEOUL_GU_NAMES = [
    "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
    "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
    "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구",
]

# Nominatim 사용 정책(초당 1회 권장) 고려한 요청 간 대기시간
NOMINATIM_SLEEP_SEC = 1.1


def fetch_seoul_boundary(path: str = SEOUL_BOUNDARY_PATH, force: bool = False) -> gpd.GeoDataFrame:
    if not force and os.path.exists(path):
        return gpd.read_file(path)

    gdf = ox.geocode_to_gdf(SEOUL_QUERY)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    gdf.to_file(path, driver="GeoJSON")
    return gdf


def fetch_gu_boundaries(path: str = GU_BOUNDARIES_PATH, force: bool = False) -> gpd.GeoDataFrame:
    """gu_name 컬럼(예: "강남구")을 가진 25행 GeoDataFrame을 반환한다."""
    if not force and os.path.exists(path):
        return gpd.read_file(path)

    rows = []
    for i, gu in enumerate(SEOUL_GU_NAMES):
        query = f"{gu}, Seoul, South Korea"
        row = ox.geocode_to_gdf(query)
        row["gu_name"] = gu
        rows.append(row)
        print(f"[{i + 1}/{len(SEOUL_GU_NAMES)}] {gu} 경계 조회 완료", flush=True)
        if i < len(SEOUL_GU_NAMES) - 1:
            time.sleep(NOMINATIM_SLEEP_SEC)

    gdf = gpd.GeoDataFrame(pd.concat(rows, ignore_index=True), crs=rows[0].crs)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    gdf.to_file(path, driver="GeoJSON")
    return gdf


def main() -> None:
    seoul = fetch_seoul_boundary()
    print(f"서울 경계 저장: {SEOUL_BOUNDARY_PATH} (bounds={seoul.total_bounds})")

    gu = fetch_gu_boundaries()
    print(f"자치구 경계 {len(gu)}개 저장: {GU_BOUNDARIES_PATH}")
    print(gu["gu_name"].tolist())


if __name__ == "__main__":
    main()
