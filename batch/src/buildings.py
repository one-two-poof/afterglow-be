"""서울 전체 건물 footprint + 높이 로더.

data/raw 의 건물통합정보 shapefile(AL_D010_11_*.shp)을 읽어 서울 전체
(SIG_CD 앞 2자리 = '11')를 대상으로 HEIGHT 결측/이상치를 GRND_FLR*3.3 으로 보정한다.

원본 자체가 이미 서울 전체(약 70만 건물, 695,769건) 규모라 전체를 한 번에
메모리에 올리는 대신 OGR SQL WHERE 절로 서울(11로 시작하는 SIG_CD)만 골라
읽는 방식을 유지한다. 이 조건은 사실상 전체 행과 일치하지만, 향후 원본에
서울 외 데이터가 섞이더라도 안전하도록 명시적으로 필터링한다.

정상 메모리 상황에서는 pyogrio.read_dataframe 한 번으로 전체를 읽고,
MemoryError 발생 시에만 skip_features/max_features 기반 청크 단위 읽기로
자동 폴백한다(_read_seoul_buildings_chunked).

실제 컬럼명은 A0~A28 로 익명화되어 있어(2026-08-05 데이터 확인, dbf 인코딩: cp949),
RAW_COLUMNS 에서 검증된 의미로 매핑한다. 이전 표본 데이터(CH_D010_00)와 컬럼 개수/
위치가 다르므로 재확인 없이 재사용 금지 (예: SIG_CD가 이번엔 A23, 이전엔 A30).
"""
from __future__ import annotations

import glob
import os

import pandas as pd
import pyogrio
import geopandas as gpd
import matplotlib
import shapely

matplotlib.use("Agg")
import matplotlib.pyplot as plt

plt.rcParams["font.family"] = "Malgun Gothic"
plt.rcParams["axes.unicode_minus"] = False

RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "data", "raw")
DEBUG_DIR = os.path.join(os.path.dirname(__file__), "..", "debug")
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "data", "out")

TARGET_CRS_EPSG = 5186
SIG_CD_SEOUL_PREFIX = "11"
CHUNK_SIZE = 100_000
HEIGHT_MIN_M = 2.0
HEIGHT_MAX_M = 300.0
FLOOR_HEIGHT_M = 3.3

# 대한민국 대략 범위. 이 밖으로 centroid가 벗어나면 좌표 이상치로 간주해 제외한다.
KOREA_LON_RANGE = (124.0, 132.0)
KOREA_LAT_RANGE = (33.0, 43.0)
COORD_OUTLIER_CSV = os.path.join(DEBUG_DIR, "coord_outliers.csv")

# data/raw/AL_D010_11_*.shp 실측 확인 결과 매핑 (dbf 인코딩: cp949)
RAW_COLUMNS = {
    "A0": "BLD_ID",
    "A4": "ADDR",
    "A9": "USE_NAME",
    "A16": "HEIGHT_RAW",
    "A23": "SIG_CD",
    "A26": "GRND_FLR",
}


def _find_shapefile(raw_dir: str) -> str:
    matches = sorted(glob.glob(os.path.join(raw_dir, "*.shp")))
    if not matches:
        raise FileNotFoundError(f"no .shp file found in {raw_dir}")
    return matches[-1]


def _fix_invalid_geometries(gdf: gpd.GeoDataFrame) -> gpd.GeoDataFrame:
    """위상적으로 깨진(self-intersecting 등) 건물 폴리곤을 make_valid로 고친다.

    강남구(28,228건)만 쓸 때는 안 걸렸는데 서울 전체(695,769건)로 늘리니
    shadow.py의 union() 호출이 GEOSException(TopologyException: unable to assign
    free hole to a shell)으로 죽는 걸 실측으로 확인 — 원본 shapefile 자체에 깨진
    폴리곤이 소수 섞여 있다. 25구 병렬 배치가 도중에 죽는 원인이 될 수 있어 로딩
    단계에서 한 번에 고쳐둔다.
    """
    invalid = ~gdf.geometry.is_valid
    n_invalid = int(invalid.sum())
    if n_invalid > 0:
        gdf = gdf.copy()
        gdf.loc[invalid, "geometry"] = gdf.loc[invalid, "geometry"].apply(shapely.make_valid)
        print(f"위상 오류 건물 {n_invalid}건 make_valid로 복구", flush=True)
    return gdf


def _correct_height(gdf: gpd.GeoDataFrame) -> tuple[gpd.GeoDataFrame, dict]:
    raw = gdf["HEIGHT_RAW"]
    is_missing = raw.isna()
    is_out_of_range = raw.notna() & ((raw < HEIGHT_MIN_M) | (raw > HEIGHT_MAX_M))
    needs_fallback = is_missing | is_out_of_range

    fallback = gdf["GRND_FLR"] * FLOOR_HEIGHT_M
    height = raw.where(~needs_fallback, fallback)

    after_missing = height.isna()
    after_out_of_range = height.notna() & ((height < HEIGHT_MIN_M) | (height > HEIGHT_MAX_M))

    stats = {
        "before_missing": int(is_missing.sum()),
        "before_out_of_range": int(is_out_of_range.sum()),
        "after_missing": int(after_missing.sum()),
        "after_out_of_range": int(after_out_of_range.sum()),
    }

    gdf = gdf.copy()
    gdf["HEIGHT"] = height
    return gdf, stats


def _split_coord_outliers(gdf: gpd.GeoDataFrame) -> tuple[gpd.GeoDataFrame, gpd.GeoDataFrame]:
    """건물 centroid(EPSG:4326)가 대한민국 대략 범위를 벗어나면 분리한다.

    원본 shapefile은 건드리지 않고, geojson으로 내보내는 이 단계에서만 걸러낸다.
    """
    centroid_4326 = gdf.geometry.centroid.to_crs(epsg=4326)
    lon = centroid_4326.x
    lat = centroid_4326.y
    is_valid = lon.between(*KOREA_LON_RANGE) & lat.between(*KOREA_LAT_RANGE)

    gdf = gdf.copy()
    gdf["LON"] = lon
    gdf["LAT"] = lat

    clean = gdf[is_valid].drop(columns=["LON", "LAT"]).reset_index(drop=True)
    outliers = gdf[~is_valid].reset_index(drop=True)
    return clean, outliers


def _save_coord_outliers_csv(outliers: gpd.GeoDataFrame, out_path: str) -> None:
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    outliers[["BLD_ID", "ADDR", "LON", "LAT"]].to_csv(out_path, index=False, encoding="utf-8-sig")


def _read_seoul_buildings_chunked(shp_path: str, where: str, columns: list[str]) -> gpd.GeoDataFrame:
    """skip_features/max_features로 CHUNK_SIZE씩 끊어 읽어 이어붙인다.

    전체를 한 번에 read_dataframe 하다 MemoryError가 나는 경우에만 호출된다.
    """
    chunks = []
    offset = 0
    while True:
        chunk = pyogrio.read_dataframe(
            shp_path,
            encoding="cp949",
            where=where,
            columns=columns,
            skip_features=offset,
            max_features=CHUNK_SIZE,
        )
        if len(chunk) == 0:
            break
        chunks.append(chunk)
        offset += CHUNK_SIZE

    return gpd.GeoDataFrame(pd.concat(chunks, ignore_index=True), crs=chunks[0].crs)


def load_seoul_buildings(raw_dir: str = RAW_DIR) -> tuple[gpd.GeoDataFrame, dict]:
    shp_path = _find_shapefile(raw_dir)
    where = f"A23 LIKE '{SIG_CD_SEOUL_PREFIX}%'"
    columns = list(RAW_COLUMNS.keys())

    # OGR WHERE 절로 서울(11로 시작하는 SIG_CD)만 골라 읽는 최적화는 유지.
    # 정상 상황에서는 한 번에 읽고, 메모리가 부족할 때만 청크 단위로 폴백한다.
    try:
        gdf = pyogrio.read_dataframe(shp_path, encoding="cp949", where=where, columns=columns)
    except MemoryError:
        gdf = _read_seoul_buildings_chunked(shp_path, where, columns)

    if gdf.crs is None or gdf.crs.to_epsg() != TARGET_CRS_EPSG:
        raise ValueError(f"expected EPSG:{TARGET_CRS_EPSG}, got {gdf.crs}")

    gdf = gdf.rename(columns=RAW_COLUMNS).reset_index(drop=True)
    gdf = _fix_invalid_geometries(gdf)
    gdf, stats = _correct_height(gdf)

    gdf, coord_outliers = _split_coord_outliers(gdf)
    _save_coord_outliers_csv(coord_outliers, COORD_OUTLIER_CSV)
    stats["coord_outliers"] = int(len(coord_outliers))
    print(f"좌표 범위 이상치 {len(coord_outliers)}건 제외됨: {COORD_OUTLIER_CSV}")

    return gdf, stats


def _district_counts(gdf: gpd.GeoDataFrame, top_n: int = 5) -> pd.Series:
    """ADDR("서울특별시 XX구 ...")에서 자치구명을 뽑아 건물 수를 센다."""
    district = gdf["ADDR"].str.split(" ").str[1]
    return district.value_counts().head(top_n)


def _save_height_histogram(gdf: gpd.GeoDataFrame, out_path: str) -> None:
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    fig, axes = plt.subplots(1, 2, figsize=(10, 4), sharey=True)
    axes[0].hist(gdf["HEIGHT_RAW"].dropna(), bins=30, color="lightgray", edgecolor="black")
    axes[0].set_title("HEIGHT_RAW (보정 전)")
    axes[0].set_xlabel("m")
    axes[0].set_ylabel("건물 수")

    axes[1].hist(gdf["HEIGHT"].dropna(), bins=30, color="steelblue", edgecolor="black")
    axes[1].set_title("HEIGHT (보정 후)")
    axes[1].set_xlabel("m")

    fig.suptitle(f"서울 전체 건물 높이 분포 (n={len(gdf)})")
    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def main() -> None:
    gdf, stats = load_seoul_buildings()

    print(f"서울 전체 건물 수: {len(gdf)}")
    before_outliers = stats["before_missing"] + stats["before_out_of_range"]
    after_outliers = stats["after_missing"] + stats["after_out_of_range"]
    print(
        f"높이 보정 전 - 결측: {stats['before_missing']}, "
        f"범위 밖({HEIGHT_MIN_M}~{HEIGHT_MAX_M}m): {stats['before_out_of_range']}, "
        f"이상치 합계: {before_outliers}"
    )
    print(
        f"높이 보정 후 - 결측: {stats['after_missing']}, "
        f"범위 밖: {stats['after_out_of_range']}, "
        f"이상치 합계: {after_outliers}"
    )

    print("자치구별 건물 수 상위 5개:")
    for district, count in _district_counts(gdf).items():
        print(f"  {district}: {count}")

    out_path = os.path.join(DEBUG_DIR, "height_dist.png")
    _save_height_histogram(gdf, out_path)
    print(f"높이 히스토그램 저장: {out_path}")

    os.makedirs(OUT_DIR, exist_ok=True)
    geojson_path = os.path.join(OUT_DIR, "buildings.geojson")
    bld_cols = [c for c in ["BLD_ID", "ADDR", "USE_NAME", "HEIGHT", "geometry"] if c in gdf.columns]
    gdf[bld_cols].to_crs(epsg=4326).to_file(geojson_path, driver="GeoJSON")
    geojson_bytes = os.path.getsize(geojson_path)
    print(f"buildings.geojson 저장: {geojson_path} ({geojson_bytes:,} bytes, {geojson_bytes / 1024 / 1024:.1f} MB)")


if __name__ == "__main__":
    main()
