package com.afterglow.shaderoute.graph;

import java.util.List;

/**
 * edges.geojson의 한 feature. coords는 LineString 좌표 [lon, lat] 목록으로,
 * coords.get(0)이 노드 u, coords.get(coords.size()-1)이 노드 v의 좌표와 같다
 * (osmnx fill_edge_geometry 기본값이 보장하는 성질 — 서울 확장 후에도 경계 클리핑에
 * gpd.clip() 대신 intersects 필터를 써서 이 불변조건을 유지한다, wsl_extract_network.py
 * 참고). 노드 좌표 자체는 nodes.bin에서 오며, 이 불변조건은 여기서 참조하지 않는다.
 */
public record GraphEdge(long u, long v, int edgeId, double length, List<double[]> coords) {
}
