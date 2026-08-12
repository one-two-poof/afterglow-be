"""대표 엣지(남북/동서) 주변 200m의 건물·그림자를 시각적으로 검증한다.

edge_id=26003(남북), edge_id=17009(동서) 각각을 중심으로 반경 200m 안의 건물과
그림자 폴리곤을 05:00/06:00/12:00/17:00 네 시각으로 그려 batch/debug/edge_shadow_map.png
에 저장한다. 두 엣지는 실제로 5.2km 떨어져 있어(강남구 내 다른 동) 한 프레임에 같이
담으면 200m 반경이 점처럼 작아지므로, 2행(엣지)×4열(시각) 그리드로 나눈다.
"""
from __future__ import annotations

import os

import geopandas as gpd
import matplotlib
import pandas as pd

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from src.buildings import DEBUG_DIR, load_seoul_buildings
from src.shadow import DEFAULT_TZ, compute_shadows

plt.rcParams["font.family"] = "Malgun Gothic"
plt.rcParams["axes.unicode_minus"] = False

EDGES_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "data", "out", "edges.geojson")
REPRESENTATIVE_DATE = "2026-08-15"
RADIUS_M = 200.0
TIMES = ["05:00", "06:00", "12:00", "17:00"]
TARGET_EDGES = [("남북", 26003), ("동서", 17009)]


def main() -> None:
    buildings, _ = load_seoul_buildings()
    edges = gpd.read_file(EDGES_PATH).to_crs(epsg=5186)

    fig, axes = plt.subplots(2, 4, figsize=(20, 10))

    for row, (label, edge_id) in enumerate(TARGET_EDGES):
        edge_geom = edges.loc[edges["edge_id"] == edge_id, "geometry"].iloc[0]
        center = edge_geom.centroid
        view = center.buffer(RADIUS_M)

        nearby = buildings[buildings.geometry.intersects(view)]
        edge_gs = gpd.GeoSeries([edge_geom], crs=edges.crs)

        for col, hhmm in enumerate(TIMES):
            ax = axes[row, col]
            when = pd.Timestamp(f"{REPRESENTATIVE_DATE} {hhmm}:00", tz=DEFAULT_TZ)
            shadow = compute_shadows(nearby, when)

            nearby.plot(ax=ax, color="lightgray", edgecolor="black", linewidth=0.3, zorder=1)
            if len(shadow) > 0:
                shadow.plot(ax=ax, color="dimgray", alpha=0.4, edgecolor="none", zorder=2)
            edge_gs.plot(ax=ax, color="red", linewidth=3, zorder=3)

            ax.set_xlim(center.x - RADIUS_M, center.x + RADIUS_M)
            ax.set_ylim(center.y - RADIUS_M, center.y + RADIUS_M)
            ax.set_aspect("equal")
            ax.set_xticks([])
            ax.set_yticks([])
            ax.set_title(f"{label} edge_id={edge_id}  {hhmm}")

    fig.suptitle(f"엣지 주변 200m 건물·그림자 검증 ({REPRESENTATIVE_DATE})")
    fig.tight_layout()

    out_path = os.path.join(DEBUG_DIR, "edge_shadow_map.png")
    os.makedirs(DEBUG_DIR, exist_ok=True)
    fig.savefig(out_path, dpi=150)
    plt.close(fig)
    print(f"저장: {out_path}")


if __name__ == "__main__":
    main()
