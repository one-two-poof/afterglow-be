"""건물 footprint + 태양 위치로 그림자 폴리곤을 계산한다.

L = h / tan(고도각), 방향은 태양 방위각 + 180도.
footprint를 (dx, dy)만큼 translate 한 뒤 원본과 union, convex_hull로 감싼 폴리곤을
그림자로 근사한다.

모든 좌표 연산은 건물 GeoDataFrame의 CRS(EPSG:5186, 미터)에서 이뤄진다.
WGS84 상태에서 이 함수를 호출하면 버그다.
"""
from __future__ import annotations

import math
import os
import time

import geopandas as gpd
import matplotlib
import numpy as np
import pandas as pd
from pvlib import solarposition
from shapely.affinity import translate as shp_translate
from shapely.strtree import STRtree

matplotlib.use("Agg")
import matplotlib.pyplot as plt

plt.rcParams["font.family"] = "Malgun Gothic"
plt.rcParams["axes.unicode_minus"] = False

REFERENCE_LAT = 37.5172
REFERENCE_LON = 127.0473
MIN_ELEVATION_DEG = 3.0
DEFAULT_TZ = "Asia/Seoul"

BUCKET_START_HOUR = 5
BUCKET_END_HOUR = 20
BUCKET_MINUTES = 30  # was 15 — 서울 규모 계산량 완화 (61 -> 31 버킷/일)

REPRESENTATIVE_DATES = ["2026-03-20", "2026-06-21", "2026-09-22", "2026-12-21"]


def compute_shadows(
    buildings: gpd.GeoDataFrame,
    when: pd.Timestamp,
    height_col: str = "HEIGHT",
) -> gpd.GeoSeries:
    when = pd.Timestamp(when)
    if when.tzinfo is None:
        when = when.tz_localize(DEFAULT_TZ)

    solpos = solarposition.get_solarposition(
        pd.DatetimeIndex([when]), REFERENCE_LAT, REFERENCE_LON
    )
    # apparent_elevation: 대기굴절 보정 포함. 지평선 부근(3도 컷오프)에서 실제로
    # 보이는 태양 고도와 일치시키기 위해 기하학적 elevation 대신 이걸 쓴다.
    elevation = float(solpos["apparent_elevation"].iloc[0])
    azimuth = float(solpos["azimuth"].iloc[0])

    if elevation <= MIN_ELEVATION_DEG:
        return gpd.GeoSeries([], crs=buildings.crs, name="shadow")

    valid = buildings[height_col].notna()
    bld = buildings.loc[valid]

    length = bld[height_col] / math.tan(math.radians(elevation))
    shadow_azimuth = math.radians((azimuth + 180.0) % 360.0)
    dx = length * math.sin(shadow_azimuth)
    dy = length * math.cos(shadow_azimuth)

    translated = gpd.GeoSeries(
        [shp_translate(geom, xo, yo) for geom, xo, yo in zip(bld.geometry, dx, dy)],
        index=bld.index,
        crs=bld.crs,
    )
    swept = bld.geometry.union(translated)
    shadow = swept.convex_hull
    shadow.name = "shadow"
    return shadow


def build_bucket_times(
    date: str,
    start_hour: int = BUCKET_START_HOUR,
    end_hour: int = BUCKET_END_HOUR,
    minutes: int = BUCKET_MINUTES,
) -> list[pd.Timestamp]:
    start = pd.Timestamp(f"{date} {start_hour:02d}:00:00", tz=DEFAULT_TZ)
    end = pd.Timestamp(f"{date} {end_hour:02d}:00:00", tz=DEFAULT_TZ)
    step = pd.Timedelta(minutes=minutes)
    n = int((end - start) / step) + 1
    return [start + i * step for i in range(n)]


def compute_shade_table(
    buildings: gpd.GeoDataFrame,
    points: np.ndarray,
    point_edge_id: np.ndarray,
    n_edges: int,
    bucket_times: list[pd.Timestamp],
    log_prefix: str = "",
) -> np.ndarray:
    """버킷별 엣지 그늘 비율(0~255) 테이블을 계산한다.

    자치구 단위 병렬 워커(district.py)와 main.py 양쪽에서 재사용하는 공용 함수라 특정
    지역/날짜 문맥을 모른다 — 로그 접두사만 호출부에서 넘겨받는다.
    """
    n_buckets = len(bucket_times)
    table = np.zeros((n_buckets, n_edges), dtype=np.uint8)

    total_per_edge = np.bincount(point_edge_id, minlength=n_edges)
    total_per_edge_safe = np.where(total_per_edge == 0, 1, total_per_edge)

    for i, when in enumerate(bucket_times):
        t0 = time.time()
        shadow = compute_shadows(buildings, when)

        if len(shadow) > 0:
            # STRtree로 그림자 후보를 좁혀서 포인트 전체를 한 번에 질의한다.
            tree = STRtree(shadow.values)
            input_idx, _ = tree.query(points, predicate="intersects")
            shaded_point_idx = np.unique(input_idx)
            shaded_edge_ids = point_edge_id[shaded_point_idx]
            shaded_counts = np.bincount(shaded_edge_ids, minlength=n_edges)
            ratio = shaded_counts / total_per_edge_safe
            table[i] = np.clip(np.round(ratio * 255), 0, 255).astype(np.uint8)

        print(
            f"{log_prefix}[버킷 {i + 1}/{n_buckets}] {when.strftime('%H:%M')} 완료 ({time.time() - t0:.1f}s)",
            flush=True,
        )

    return table


def _plot_check(buildings: gpd.GeoDataFrame, date: str, hours: list[int], out_path: str) -> None:
    fig, axes = plt.subplots(1, len(hours), figsize=(5 * len(hours), 5), sharex=True, sharey=True)
    for ax, hour in zip(axes, hours):
        when = pd.Timestamp(f"{date} {hour:02d}:00:00", tz=DEFAULT_TZ)
        shadow = compute_shadows(buildings, when)
        if len(shadow) > 0:
            shadow.plot(ax=ax, color="steelblue", alpha=0.4, edgecolor="none")
        buildings.plot(ax=ax, color="gray", edgecolor="black", linewidth=0.3)
        ax.set_title(f"{hour:02d}:00")
        ax.set_aspect("equal")

    fig.suptitle(f"삼성동 그림자 검증 ({date})")
    fig.tight_layout()
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def main() -> None:
    from src.buildings import DEBUG_DIR, load_seoul_buildings

    gdf, _ = load_seoul_buildings()
    samseong = gdf[gdf["ADDR"].str.contains("삼성동", na=False)].reset_index(drop=True)
    print(f"삼성동 건물 수: {len(samseong)}")

    out_path = os.path.join(DEBUG_DIR, "shadow_check.png")
    _plot_check(samseong, "2026-08-15", [9, 12, 17], out_path)
    print(f"저장: {out_path}")


if __name__ == "__main__":
    main()
