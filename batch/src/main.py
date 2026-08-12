"""서울 전체 그늘길 배치 파이프라인 진입점.

buildings.py(건물 footprint+높이) + graph.py(보행 네트워크, WSL에서 pyrosm으로 미리
추출된 결과를 로드) + district.py(자치구 단위 병렬 그림자 계산)를 엮어 대표일
(REPRESENTATIVE_DATES) 각각에 대해 05:00~20:00 30분 버킷별 엣지 그늘 비율(shade_table)을
계산하고, 서버가 쓸 산출물을 data/out/ 에 저장한다.

edges.geojson / buildings.geojson / nodes.bin은 날짜와 무관해 1회만 저장하고,
shade_table은 날짜별로 shade_table_{YYYYMMDD}.bin 로 분리해 저장한다.

사전 준비 (최초 1회):
  1. cd batch && python -m src.fetch_osm_pbf
  2. cd batch && python -m src.boundaries
  3. wsl -d Ubuntu bash -c "source ~/osmenv/bin/activate && \
       python3 /mnt/c/afterglow-be/batch/src/wsl_extract_network.py"

실행: cd batch && python -m src.main
"""
from __future__ import annotations

import json
import multiprocessing
import os
import time

import geopandas as gpd
import matplotlib
import numpy as np
import pandas as pd

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from src.boundaries import fetch_gu_boundaries, fetch_seoul_boundary
from src.buildings import DEBUG_DIR, load_seoul_buildings
from src.district import build_district_jobs, run_district_job
from src.graph import SAMPLE_SPACING_M, load_seoul_walk_edges, load_seoul_walk_nodes, sample_edges
from src.shadow import (
    BUCKET_END_HOUR,
    BUCKET_MINUTES,
    BUCKET_START_HOUR,
    DEFAULT_TZ,
    REPRESENTATIVE_DATES,
    build_bucket_times,
)

plt.rcParams["font.family"] = "Malgun Gothic"
plt.rcParams["axes.unicode_minus"] = False

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "data", "out")

# nodes.bin 레코드: int64 node_id | float64 lon | float64 lat, 빅엔디안.
# Java DataInputStream.readLong()/readDouble() 기본값(빅엔디안) 그대로 읽을 수 있게
# 맞춘 것 — ByteBuffer.order() 래핑이 필요 없다. 바꾸면 조용히 깨지는 버그가 되니
# Java 쪽 ShadeGraph.load()와 반드시 짝을 맞출 것.
NODES_BIN_DTYPE = np.dtype([("node_id", ">i8"), ("lon", ">f8"), ("lat", ">f8")])


def save_shared_outputs(
    edges: gpd.GeoDataFrame,
    buildings: gpd.GeoDataFrame,
    out_dir: str,
) -> None:
    """날짜와 무관한 산출물(edges.geojson, buildings.geojson)을 1회만 저장한다."""
    os.makedirs(out_dir, exist_ok=True)

    edge_cols = [c for c in ["edge_id", "u", "v", "length", "geometry"] if c in edges.columns]
    edges[edge_cols].to_crs(epsg=4326).to_file(
        os.path.join(out_dir, "edges.geojson"), driver="GeoJSON"
    )

    bld_cols = [c for c in ["BLD_ID", "ADDR", "USE_NAME", "HEIGHT", "geometry"] if c in buildings.columns]
    buildings[bld_cols].to_crs(epsg=4326).to_file(
        os.path.join(out_dir, "buildings.geojson"), driver="GeoJSON"
    )


def save_nodes_bin(nodes_4326: gpd.GeoDataFrame, out_path: str) -> int:
    """ShadeGraph.java가 KD-tree를 만들 때 쓰는 nodes.bin을 저장한다.

    nodes_4326은 graph.py의 load_seoul_walk_nodes()가 반환한, wsl_extract_network.py가
    이미 edges.geojson이 참조하는 노드로만 걸러둔 결과라고 가정한다.
    """
    arr = np.zeros(len(nodes_4326), dtype=NODES_BIN_DTYPE)
    arr["node_id"] = nodes_4326["osmid"].to_numpy()
    arr["lon"] = nodes_4326["x"].to_numpy()
    arr["lat"] = nodes_4326["y"].to_numpy()
    arr.tofile(out_path)
    return len(arr)


def save_shade_table(table: np.ndarray, date: str, out_dir: str) -> str:
    """날짜별 shade_table_{YYYYMMDD}.bin 저장 후 경로를 반환한다."""
    table_path = os.path.join(out_dir, f"shade_table_{date.replace('-', '')}.bin")
    table.tofile(table_path)
    return table_path


def save_meta(n_edges: int, n_buckets: int, dates: list[str], out_dir: str) -> None:
    meta = {
        "n_edges": int(n_edges),
        "n_buckets": int(n_buckets),
        "bucket_start": f"{BUCKET_START_HOUR:02d}:00",
        "bucket_minutes": BUCKET_MINUTES,
        "representative_dates": dates,
        "timezone": DEFAULT_TZ,
    }
    with open(os.path.join(out_dir, "shade_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)


MIN_TYPICAL_EDGE_LEN_M = 75.0  # sample_edges 15m 간격 기준 최소 5개 포인트 확보
MAX_TYPICAL_EDGE_LEN_M = 300.0


def _pick_representative_edges(
    edges: gpd.GeoDataFrame,
    table: np.ndarray,
    min_length: float = MIN_TYPICAL_EDGE_LEN_M,
    max_length: float = MAX_TYPICAL_EDGE_LEN_M,
) -> tuple[int, int]:
    """방향별로 하루 동안 그늘 비율 분산이 가장 큰 엣지를 고른다.

    가장 긴 엣지를 고르면 공원/산책로처럼 건물이 거의 없는 구간이 뽑혀 하루 종일
    0에 가깝고, 총 그늘량이 가장 큰 엣지를 고르면 반대로 고층 건물 사이 좁은 통로처럼
    하루 종일 100% 그늘인 평평한 곡선이 뽑힌다. 분산만 보면 이번엔 샘플 포인트가
    1개뿐인 짧은 엣지(그늘 여부가 0/255로 이진 스위칭)가 뽑혀 곡선이 아니라 계단식
    잡음이 된다. 세 문제를 다 피하려면 샘플 포인트가 여러 개 확보되는 길이
    (일반적인 가로 구간) 안에서 분산이 큰 엣지를 고른다.
    """
    bounds = edges.geometry.bounds
    dx = (bounds["maxx"] - bounds["minx"]).abs().to_numpy()
    dy = (bounds["maxy"] - bounds["miny"]).abs().to_numpy()
    lengths = edges.geometry.length.to_numpy()
    edge_ids = edges["edge_id"].to_numpy()

    typical = (lengths >= min_length) & (lengths <= max_length)
    variance = table.astype(np.float64).var(axis=0)  # edge_id로 바로 인덱싱 가능

    ns_mask = typical & (dy > dx)
    ew_mask = typical & (dx >= dy)

    ns_edge_id = int(ns_ids[np.argmax(variance[ns_ids])]) if (ns_ids := edge_ids[ns_mask]).size else -1
    ew_edge_id = int(ew_ids[np.argmax(variance[ew_ids])]) if (ew_ids := edge_ids[ew_mask]).size else -1
    return ns_edge_id, ew_edge_id


def _plot_shade_curve(
    table: np.ndarray,
    bucket_times: list[pd.Timestamp],
    ns_edge_id: int,
    ew_edge_id: int,
    out_path: str,
    date: str,
) -> None:
    hours = [t.strftime("%H:%M") for t in bucket_times]
    ns_ratio = table[:, ns_edge_id] / 255.0 if ns_edge_id >= 0 else np.zeros(len(hours))
    ew_ratio = table[:, ew_edge_id] / 255.0 if ew_edge_id >= 0 else np.zeros(len(hours))

    fig, ax = plt.subplots(figsize=(12, 5))
    ax.plot(hours, ns_ratio, label=f"남북 엣지 (edge_id={ns_edge_id})", color="steelblue")
    ax.plot(hours, ew_ratio, label=f"동서 엣지 (edge_id={ew_edge_id})", color="indianred")
    ax.set_xlabel("시각")
    ax.set_ylabel("그늘 비율")
    ax.set_ylim(0, 1)
    ax.set_xticks(range(0, len(hours), 4))
    ax.set_xticklabels(hours[::4], rotation=45)
    ax.legend()
    ax.set_title(f"남북/동서 엣지 시간별 그늘 비율 ({date})")
    fig.tight_layout()

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def main() -> None:
    run_start = time.time()

    print("건물 로딩 중...", flush=True)
    buildings, _ = load_seoul_buildings()
    print(f"건물 수: {len(buildings)}", flush=True)

    print("서울/자치구 경계 로딩 중...", flush=True)
    gu_boundaries = fetch_gu_boundaries()
    fetch_seoul_boundary()  # 캐시 확인용 (여기서는 직접 쓰지 않음)

    print("서울 보행 네트워크 로딩 중 (WSL 추출 결과)...", flush=True)
    edges = load_seoul_walk_edges()
    nodes_4326 = load_seoul_walk_nodes()
    print(f"엣지 수: {len(edges)}, 노드 수: {len(nodes_4326)}", flush=True)

    print(f"엣지 포인트 샘플링 중 ({SAMPLE_SPACING_M}m 간격)...", flush=True)
    points, point_edge_id = sample_edges(edges)
    print(f"샘플 포인트 수: {len(points)}", flush=True)

    save_shared_outputs(edges, buildings, OUT_DIR)
    n_nodes = save_nodes_bin(nodes_4326, os.path.join(OUT_DIR, "nodes.bin"))
    print(
        f"공통 산출물 저장 완료 (edges.geojson, buildings.geojson, nodes.bin {n_nodes}개): {OUT_DIR}",
        flush=True,
    )

    print("자치구별 작업 분할 중 (경계+버퍼)...", flush=True)
    jobs = build_district_jobs(buildings, edges, points, point_edge_id, gu_boundaries)
    print(f"자치구 작업 {len(jobs)}개 생성", flush=True)

    n_buckets = len(build_bucket_times(REPRESENTATIVE_DATES[0]))
    full_tables = {
        date: np.zeros((n_buckets, len(edges)), dtype=np.uint8) for date in REPRESENTATIVE_DATES
    }

    # os.cpu_count() 그대로(8개) 썼다가 numpy._ArrayMemoryError로 죽는 걸 확인했다 —
    # 워커마다 버퍼 건물 GeoDataFrame(수만 행, geometry 포함)을 독립적으로 들고 있는데
    # Windows는 spawn 방식이라 fork의 메모리 공유 이점이 없어, 이 머신 물리 RAM(7.9GB)
    # 한도에서 8개 동시 실행이 버겁다. 워커 수를 줄여 동시 메모리 사용량을 낮춘다.
    n_workers = min(os.cpu_count() or 1, 4)
    print(f"{n_workers}개 프로세스로 {len(jobs)}개 구 병렬 처리 시작...", flush=True)
    with multiprocessing.Pool(processes=n_workers) as pool:
        for i, result in enumerate(pool.imap_unordered(run_district_job, jobs, chunksize=1), start=1):
            for date, sub_table in result.tables.items():
                full_tables[date][:, result.own_edge_ids] = sub_table
            print(f"[{i}/{len(jobs)}] {result.gu_name} 완료", flush=True)

    for date, table in full_tables.items():
        table_path = save_shade_table(table, date, OUT_DIR)
        print(f"{date} 저장 완료: {table_path} ({os.path.getsize(table_path):,} bytes)", flush=True)

        bucket_times = build_bucket_times(date)
        ns_edge_id, ew_edge_id = _pick_representative_edges(edges, table)
        curve_path = os.path.join(DEBUG_DIR, f"shade_curve_{date.replace('-', '')}.png")
        _plot_shade_curve(table, bucket_times, ns_edge_id, ew_edge_id, curve_path, date)
        print(f"{date} 그늘 비율 곡선 저장: {curve_path}", flush=True)

    save_meta(len(edges), n_buckets, REPRESENTATIVE_DATES, OUT_DIR)
    print("shade_meta.json 저장 완료", flush=True)

    elapsed = time.time() - run_start
    print(
        f"전체 실행 시간: {elapsed:.1f}s ({elapsed / 60:.1f}분), "
        f"날짜 {len(REPRESENTATIVE_DATES)}개 x 구 {len(jobs)}개 처리 완료",
        flush=True,
    )


if __name__ == "__main__":
    main()
