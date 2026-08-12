"""서울 전체 보행 네트워크 로더 + 엣지 포인트 샘플링.

실제 OSM 추출(pyrosm + Geofabrik pbf)은 WSL 전용 스크립트(wsl_extract_network.py)가
수행한다 — pyrosm의 의존 패키지 cykhash가 Windows용 사전 빌드 wheel이 없어 이 파일은
pyrosm을 import하지 않는다. 여기서는 그 결과물(GeoPackage)을 읽기만 한다.

WSL 스크립트 실행 (최초 1회 또는 pbf/경계가 바뀌었을 때):
  wsl -d Ubuntu bash -c "source ~/osmenv/bin/activate && \
    python3 /mnt/c/afterglow-be/batch/src/wsl_extract_network.py"
"""
from __future__ import annotations

import os

import numpy as np
import geopandas as gpd

TARGET_CRS_EPSG = 5186
SAMPLE_SPACING_M = 15.0  # was 8.0 — 서울 규모 계산량 완화

CACHE_DIR = os.path.join(os.path.dirname(__file__), "..", "cache")
SEOUL_EDGES_GPKG = os.path.join(CACHE_DIR, "seoul_walk_edges_5186.gpkg")
SEOUL_NODES_GPKG = os.path.join(CACHE_DIR, "seoul_walk_nodes_4326.gpkg")


def load_seoul_walk_edges(path: str = SEOUL_EDGES_GPKG) -> gpd.GeoDataFrame:
    """wsl_extract_network.py가 만든 GeoPackage를 읽는다.

    u, v, edge_id, length, geometry(EPSG:5186) 컬럼을 가진 GeoDataFrame을 반환한다.
    """
    if not os.path.exists(path):
        raise FileNotFoundError(
            f"{path} 가 없습니다. 먼저 WSL에서 wsl_extract_network.py를 실행하세요:\n"
            '  wsl -d Ubuntu bash -c "source ~/osmenv/bin/activate && '
            'python3 /mnt/c/afterglow-be/batch/src/wsl_extract_network.py"'
        )
    edges = gpd.read_file(path)
    if edges.crs is None or edges.crs.to_epsg() != TARGET_CRS_EPSG:
        raise ValueError(f"expected EPSG:{TARGET_CRS_EPSG}, got {edges.crs}")
    return edges


def load_seoul_walk_nodes(path: str = SEOUL_NODES_GPKG) -> gpd.GeoDataFrame:
    """osmid, x(lon), y(lat), geometry(EPSG:4326) 컬럼을 가진 GeoDataFrame을 반환한다."""
    if not os.path.exists(path):
        raise FileNotFoundError(f"{path} 가 없습니다. graph.py의 load_seoul_walk_edges 참고.")
    return gpd.read_file(path)


def sample_edges(
    edges: gpd.GeoDataFrame, spacing: float = SAMPLE_SPACING_M
) -> tuple[np.ndarray, np.ndarray]:
    """엣지 geometry를 spacing(m) 간격으로 샘플링한다.

    반환: (포인트 배열, 각 포인트가 속한 edge_id 배열). 두 배열은 길이가 같다.
    """
    points: list = []
    point_edge_id: list[int] = []

    for edge_id, geom in zip(edges["edge_id"], edges.geometry):
        length = geom.length
        distances = [length / 2.0] if length < spacing else np.arange(0.0, length, spacing)
        for d in distances:
            points.append(geom.interpolate(d))
            point_edge_id.append(edge_id)

    return np.array(points, dtype=object), np.array(point_edge_id, dtype=np.int64)
