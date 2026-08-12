"""자치구 단위 병렬 그림자 계산 작업 분할.

25개 구를 multiprocessing으로 나눠 계산한다. 그림자는 이웃 구 건물이 이 구 도로에
드리울 수 있어(건물이 높을수록 그림자가 수백m까지 뻗음), 구 경계에 딱 맞춰 건물을
잘라내면 경계 근처 엣지의 그늘 비율이 체계적으로 과소평가된다. 그래서 "경계 + 버퍼"
방식을 쓴다:
  - 엣지 배정(출력 소유권)은 구 경계 기준으로 정확히 나눈다 — 한 엣지는 정확히 한
    구의 shade_table row에만 쓰인다.
  - 건물(그림자 계산 입력)은 자기 구 건물 + 경계에서 BUFFER_M 이내 이웃 구 건물까지
    포함한다. 버퍼 건물은 입력에만 쓰이고 출력에 중복 집계되지 않는다.

Windows에서 multiprocessing은 spawn 방식이라(fork 아님) 워커로 넘길 함수는 반드시
모듈 최상위에 있어야 pickle된다 — run_district_job이 그 함수다.
"""
from __future__ import annotations

import os
from dataclasses import dataclass

import geopandas as gpd
import numpy as np
import pandas as pd

from src.shadow import (
    BUCKET_END_HOUR,
    BUCKET_MINUTES,
    BUCKET_START_HOUR,
    REPRESENTATIVE_DATES,
    build_bucket_times,
    compute_shade_table,
)

BUFFER_M = 1000.0  # HEIGHT_MAX_M=300 건물이 태양고도 ~17도일 때 그림자 길이 근사치

# 25구 x 4일 전체 실행은 이 개발 환경에서 1시간 이상 걸리는데, 백그라운드 프로세스가
# 예측 불가능한 시점에 외부 요인으로 죽는 일이 잦아(OS 레벨 kill, Python 예외 아님)
# 구+날짜 단위로 중간 결과를 디스크에 저장해 재실행 시 이어서 하도록 한다.
RESULTS_CACHE_DIR = os.path.join(os.path.dirname(__file__), "..", "cache", "district_results")


def _result_path(gu_name: str, date: str) -> str:
    return os.path.join(RESULTS_CACHE_DIR, f"{gu_name}_{date.replace('-', '')}.bin")


def _load_cached_table(gu_name: str, date: str, n_buckets: int, n_edges: int) -> np.ndarray | None:
    path = _result_path(gu_name, date)
    if not os.path.exists(path):
        return None
    table = np.fromfile(path, dtype=np.uint8)
    if table.size != n_buckets * n_edges:
        return None  # 크기가 안 맞으면(예: 엣지 재배정 등) 캐시 무효화하고 재계산
    return table.reshape(n_buckets, n_edges)


def _save_table_cache(gu_name: str, date: str, table: np.ndarray) -> None:
    os.makedirs(RESULTS_CACHE_DIR, exist_ok=True)
    table.tofile(_result_path(gu_name, date))


@dataclass
class DistrictJob:
    gu_name: str
    own_edge_ids: np.ndarray  # 이 구가 "소유"하는 엣지의 전역 edge_id
    points: np.ndarray  # 이 구 엣지들의 샘플 포인트 (로컬 인덱스 공간)
    point_edge_id: np.ndarray  # 각 포인트가 속한 엣지의 "로컬" 인덱스 (0..len(own_edge_ids)-1)
    buffer_buildings: gpd.GeoDataFrame  # 자기 구 + 버퍼 반경 이웃 구 건물


@dataclass
class DistrictResult:
    gu_name: str
    own_edge_ids: np.ndarray
    tables: dict[str, np.ndarray]  # date(YYYY-MM-DD) -> table[n_buckets, len(own_edge_ids)]


def _assign_edges_to_districts(
    edges: gpd.GeoDataFrame, gu_boundaries: gpd.GeoDataFrame
) -> pd.Series:
    """엣지 중점을 구 경계와 공간조인해 gu_name을 배정한다.

    반환: edges.index와 같은 길이의 gu_name Series. 어느 구에도 속하지 않는(경계선상)
    엣지는 sjoin_nearest로 가장 가까운 구에 폴백 배정한다.
    """
    gu_5186 = gu_boundaries.to_crs(edges.crs)[["gu_name", "geometry"]]
    midpoints = gpd.GeoDataFrame(
        {"edge_id": edges["edge_id"].to_numpy()},
        geometry=edges.geometry.interpolate(0.5, normalized=True),
        crs=edges.crs,
    )

    joined = gpd.sjoin(midpoints, gu_5186, predicate="within", how="left")
    joined = joined[~joined.index.duplicated(keep="first")]  # 경계 겹침으로 중복 매치 방지

    unmatched = joined["gu_name"].isna()
    if unmatched.any():
        nearest = gpd.sjoin_nearest(midpoints[unmatched.to_numpy()], gu_5186)
        nearest = nearest[~nearest.index.duplicated(keep="first")]
        joined.loc[unmatched, "gu_name"] = nearest["gu_name"]
        print(f"경계선상 엣지 {int(unmatched.sum())}건 최근접 구로 폴백 배정", flush=True)

    return joined["gu_name"]


def build_district_jobs(
    buildings: gpd.GeoDataFrame,
    edges: gpd.GeoDataFrame,
    points: np.ndarray,
    point_edge_id: np.ndarray,
    gu_boundaries: gpd.GeoDataFrame,
    buffer_m: float = BUFFER_M,
) -> list[DistrictJob]:
    edge_gu = _assign_edges_to_districts(edges, gu_boundaries)
    building_gu = buildings["ADDR"].str.split(" ").str[1]
    gu_5186 = gu_boundaries.to_crs(edges.crs)

    jobs = []
    for _, gu_row in gu_5186.iterrows():
        gu_name = gu_row["gu_name"]

        own_edge_ids = edges.loc[edge_gu == gu_name, "edge_id"].to_numpy()
        if len(own_edge_ids) == 0:
            print(f"[{gu_name}] 소유 엣지 0개 — 스킵", flush=True)
            continue

        buffer_geom = gu_row.geometry.buffer(buffer_m)
        buffer_buildings = buildings[buildings.geometry.intersects(buffer_geom)].reset_index(drop=True)

        own_edge_set = set(own_edge_ids.tolist())
        point_mask = np.isin(point_edge_id, own_edge_ids)
        local_points = points[point_mask]
        local_global_edge_ids = point_edge_id[point_mask]
        edge_id_to_local = {eid: i for i, eid in enumerate(own_edge_ids)}
        local_point_edge_id = np.array(
            [edge_id_to_local[e] for e in local_global_edge_ids], dtype=np.int64
        )

        jobs.append(
            DistrictJob(
                gu_name=gu_name,
                own_edge_ids=own_edge_ids,
                points=local_points,
                point_edge_id=local_point_edge_id,
                buffer_buildings=buffer_buildings,
            )
        )
        print(
            f"[{gu_name}] 소유 엣지 {len(own_edge_ids)}개, 샘플 포인트 {len(local_points)}개, "
            f"버퍼 건물(자기 구+{buffer_m:.0f}m 이내) {len(buffer_buildings)}개",
            flush=True,
        )

    return jobs


def run_district_job(job: DistrictJob) -> DistrictResult:
    """워커 프로세스에서 실행된다. 구 하나당 대표일 전부를 내부에서 순회해,
    버퍼 건물 GeoDataFrame을 프로세스당 1번만 pickle로 받는다.

    날짜별로 RESULTS_CACHE_DIR에 중간 결과를 저장하고, 이미 있으면 재계산하지 않는다
    — 전체 25구x4일 실행이 이 환경에서 예측 불가능하게 죽는 일이 잦아, 재실행 시
    이미 끝난 (구, 날짜)는 건너뛰고 이어서 하기 위함이다."""
    n_edges = len(job.own_edge_ids)
    tables: dict[str, np.ndarray] = {}

    for date in REPRESENTATIVE_DATES:
        bucket_times = build_bucket_times(date, BUCKET_START_HOUR, BUCKET_END_HOUR, BUCKET_MINUTES)
        n_buckets = len(bucket_times)

        cached = _load_cached_table(job.gu_name, date, n_buckets, n_edges)
        if cached is not None:
            print(f"[{job.gu_name}] [{date}] 캐시 사용 (이미 계산됨)", flush=True)
            tables[date] = cached
            continue

        table = compute_shade_table(
            job.buffer_buildings,
            job.points,
            job.point_edge_id,
            n_edges,
            bucket_times,
            log_prefix=f"[{job.gu_name}] [{date}] ",
        )
        _save_table_cache(job.gu_name, date, table)
        tables[date] = table

    return DistrictResult(gu_name=job.gu_name, own_edge_ids=job.own_edge_ids, tables=tables)
