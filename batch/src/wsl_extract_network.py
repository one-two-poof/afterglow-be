"""서울 보행 네트워크를 Geofabrik pbf에서 pyrosm으로 추출한다 (WSL 전용).

pyrosm의 의존 패키지 cykhash가 Windows용 사전 빌드 wheel이 없어 Windows에서 pyrosm을
설치할 수 없다. 이 스크립트는 WSL Ubuntu venv(~/osmenv, pyrosm/osmnx/geopandas 설치됨)
안에서만 실행하고, 결과를 Windows 쪽 graph.py가 읽을 수 있는 GeoPackage로 남긴다.

서울 전체를 한 번에(bounding_box=서울 전체) 추출하면 이 개발 환경에서 두 가지 문제가
실측으로 확인됐다:
  1. 메모리: 노드 75만+/엣지 85만+ 규모 NetworkX 그래프를 만들다 WSL이 OOM(SIGKILL,
     exit code 9)으로 죽는다 (이 머신 물리 RAM 7.9GB, WSL 할당량은 절반인 3.8GB뿐).
  2. 실행 시간: get_network()가 bbox 크기와 무관하게 pbf 전체를 스캔해 강남구 하나만
     해도 250~985초로 편차가 크고, 이 환경의 백그라운드 작업 실행 시간 제약을 넘으면
     get_network() 도중 강제 종료된다(메모리와 무관하게 3회 연속 재현).

그래서 자치구 25개를 하나씩 독립적으로 추출한다 — 각 구는 강남구 테스트와 비슷한
규모라 개별 실행은 안정적이다. 구 하나가 끝날 때마다 결과를 디스크에 저장해두고
(batch/cache/gu_extract/{gu_name}_*.gpkg), 이미 저장된 구는 다시 추출하지 않는다
(idempotent) — 전체 스크립트가 도중에 죽어도 이어서 실행할 수 있다. 25개 구 추출이
모두 끝나면 merge_gu_extracts()가 하나로 합친다.

경계는 gpd.clip()으로 자르지 않는다 — LineString이 경계에서 잘리면
GraphEdge.java/ShadeGraph.java가 의존하는 불변조건(엣지 LineString의 첫/끝 좌표 =
노드 u/v 좌표)이 깨진다. 대신 엣지 중점이 그 구의 (버퍼 없는) 실제 경계 안에 있는지로
소유권을 정한다 — district.py의 엣지 배정 방식과 동일한 기준이라 일관적이다.

pyrosm.to_graph()는 simplified=False로 나온다(osmnx.graph_from_place 기본값인
simplify=True와 다름) — 교차로가 아닌 중간 노드까지 전부 별도 엣지로 남아 osmnx
기준보다 엣지가 약 2배 많다. ox.simplify_graph()로 맞출 수 있다는 것도 확인했지만
(강남구 검증 시 26,010개 기준에 26,362개로 근접), 서울 전체 규모에서 simplify_graph
자체가 위 메모리 문제의 원인 중 하나라 생략한다(사용자 승인) — 결과가 정확하지 않은
건 아니고, 엣지/포인트/shade_table이 그만큼 더 잘게 쪼개져서 커질 뿐이다.

실행 (Windows에서 wsl 통해 호출):
  wsl -d Ubuntu bash -c "source ~/osmenv/bin/activate && \
    python3 /mnt/c/afterglow-be/batch/src/wsl_extract_network.py"
"""
from __future__ import annotations

import os

import geopandas as gpd
import numpy as np
import osmnx as ox
import pandas as pd
import pyrosm

TARGET_CRS_EPSG = 5186
BUFFER_M_FOR_EXTRACTION = 2000.0  # pyrosm bbox에 여유를 둬 구 경계 근처 연결성을 확보

REPO_ROOT = "/mnt/c/afterglow-be"
PBF_PATH = os.path.join(REPO_ROOT, "data", "raw", "south-korea-latest.osm.pbf")
GU_BOUNDARIES_PATH = os.path.join(REPO_ROOT, "batch", "cache", "seoul_gu_boundaries.geojson")

GU_EXTRACT_DIR = os.path.join(REPO_ROOT, "batch", "cache", "gu_extract")
OUT_EDGES_PATH = os.path.join(REPO_ROOT, "batch", "cache", "seoul_walk_edges_5186.gpkg")
OUT_NODES_PATH = os.path.join(REPO_ROOT, "batch", "cache", "seoul_walk_nodes_4326.gpkg")


def _gu_extract_paths(gu_name: str) -> tuple[str, str]:
    edges_path = os.path.join(GU_EXTRACT_DIR, f"{gu_name}_edges.gpkg")
    nodes_path = os.path.join(GU_EXTRACT_DIR, f"{gu_name}_nodes.gpkg")
    return edges_path, nodes_path


def extract_gu_walk_network(
    pbf_path: str, gu_name: str, gu_boundary_4326: gpd.GeoDataFrame
) -> tuple[gpd.GeoDataFrame, gpd.GeoDataFrame]:
    """반환: (nodes_4326, edges_5186). edges_5186는 이 구 소유(중점이 구 경계 안)만 남긴다."""
    gu_5186 = gu_boundary_4326.to_crs(epsg=TARGET_CRS_EPSG)
    buffered_5186 = gu_5186.geometry.buffer(BUFFER_M_FOR_EXTRACTION)
    buffered_4326 = gpd.GeoSeries(buffered_5186, crs=TARGET_CRS_EPSG).to_crs(epsg=4326)
    minx, miny, maxx, maxy = buffered_4326.total_bounds

    osm = pyrosm.OSM(pbf_path, bounding_box=[minx, miny, maxx, maxy])
    nodes, edges = osm.get_network(network_type="walking", nodes=True)
    print(f"  pyrosm 원본: 노드 {len(nodes)}개, 엣지 {len(edges)}개", flush=True)

    G = osm.to_graph(nodes, edges, graph_type="networkx")
    nodes_4326 = ox.graph_to_gdfs(G, nodes=True, edges=False)

    # ox.project_graph()는 쓰지 않는다 — pyrosm 그래프에서는 G.graph['crs'] 메타데이터만
    # 바뀌고 실제 엣지 좌표는 lon/lat 그대로 남는 상호운용성 버그가 있다(실측 확인).
    edges_4326 = ox.graph_to_gdfs(G, nodes=False, edges=True).reset_index()
    edges_5186 = edges_4326.set_crs(epsg=4326, allow_override=True).to_crs(epsg=TARGET_CRS_EPSG)
    edges_5186["length"] = edges_5186.geometry.length

    own_boundary_union = gu_5186.geometry.union_all()
    midpoints = edges_5186.geometry.interpolate(0.5, normalized=True)
    keep = midpoints.within(own_boundary_union)
    edges_5186 = edges_5186[keep].reset_index(drop=True)

    used_ids = set(edges_5186["u"]) | set(edges_5186["v"])
    nodes_4326 = nodes_4326[nodes_4326.index.isin(used_ids)].reset_index()

    return nodes_4326, edges_5186


def extract_all_gu(pbf_path: str = PBF_PATH, gu_boundaries_path: str = GU_BOUNDARIES_PATH) -> None:
    """25개 구를 하나씩 추출해 batch/cache/gu_extract/에 저장한다.

    이미 저장된 구는 건너뛴다 — 도중에 죽어도 다시 실행하면 이어서 진행된다.
    """
    os.makedirs(GU_EXTRACT_DIR, exist_ok=True)
    gu_boundaries = gpd.read_file(gu_boundaries_path)

    for i, row in gu_boundaries.iterrows():
        gu_name = row["gu_name"]
        edges_path, nodes_path = _gu_extract_paths(gu_name)
        if os.path.exists(edges_path) and os.path.exists(nodes_path):
            print(f"[{i + 1}/{len(gu_boundaries)}] {gu_name} 이미 있음, 스킵", flush=True)
            continue

        print(f"[{i + 1}/{len(gu_boundaries)}] {gu_name} 추출 중...", flush=True)
        gu_boundary_4326 = gu_boundaries.iloc[[i]]
        nodes_4326, edges_5186 = extract_gu_walk_network(pbf_path, gu_name, gu_boundary_4326)

        edge_cols = [c for c in ["u", "v", "length", "geometry"] if c in edges_5186.columns]
        node_cols = [c for c in ["osmid", "x", "y", "geometry"] if c in nodes_4326.columns]
        edges_5186[edge_cols].to_file(edges_path, driver="GPKG")
        nodes_4326[node_cols].to_file(nodes_path, driver="GPKG")
        print(
            f"[{i + 1}/{len(gu_boundaries)}] {gu_name} 완료: 엣지 {len(edges_5186)}개, "
            f"노드 {len(nodes_4326)}개",
            flush=True,
        )


def merge_gu_extracts(
    gu_boundaries_path: str = GU_BOUNDARIES_PATH,
    out_edges_path: str = OUT_EDGES_PATH,
    out_nodes_path: str = OUT_NODES_PATH,
) -> None:
    """25개 구 추출 결과를 하나로 합친다. 노드는 osmid 기준 중복 제거(구 버퍼가 겹쳐서
    이웃 구 추출에도 같은 노드가 들어있을 수 있음)."""
    gu_boundaries = gpd.read_file(gu_boundaries_path)

    all_edges = []
    all_nodes = []
    for _, row in gu_boundaries.iterrows():
        gu_name = row["gu_name"]
        edges_path, nodes_path = _gu_extract_paths(gu_name)
        if not (os.path.exists(edges_path) and os.path.exists(nodes_path)):
            raise FileNotFoundError(f"{gu_name} 추출 결과가 없습니다. extract_all_gu()를 먼저 실행하세요.")
        all_edges.append(gpd.read_file(edges_path))
        all_nodes.append(gpd.read_file(nodes_path))

    edges_5186 = gpd.GeoDataFrame(pd.concat(all_edges, ignore_index=True), crs=all_edges[0].crs)
    edges_5186["edge_id"] = np.arange(len(edges_5186))

    nodes_4326 = gpd.GeoDataFrame(pd.concat(all_nodes, ignore_index=True), crs=all_nodes[0].crs)
    nodes_4326 = nodes_4326.drop_duplicates(subset="osmid").reset_index(drop=True)
    used_ids = set(edges_5186["u"]) | set(edges_5186["v"])
    nodes_4326 = nodes_4326[nodes_4326["osmid"].isin(used_ids)].reset_index(drop=True)

    edge_cols = [c for c in ["u", "v", "edge_id", "length", "geometry"] if c in edges_5186.columns]
    edges_5186[edge_cols].to_file(out_edges_path, driver="GPKG")
    print(f"저장: {out_edges_path} ({len(edges_5186)}행)")

    node_cols = [c for c in ["osmid", "x", "y", "geometry"] if c in nodes_4326.columns]
    nodes_4326[node_cols].to_file(out_nodes_path, driver="GPKG")
    print(f"저장: {out_nodes_path} ({len(nodes_4326)}행)")


def main() -> None:
    extract_all_gu()
    merge_gu_extracts()


if __name__ == "__main__":
    main()
