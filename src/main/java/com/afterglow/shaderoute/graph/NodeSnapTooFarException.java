package com.afterglow.shaderoute.graph;

/**
 * 쿼리 좌표에서 가장 가까운 노드까지의 거리가 허용 임계값을 넘을 때 던진다.
 * 서울 전체로 확장되기 전에는 임계값 체크가 없어, 그래프 범위 밖 좌표(예: 강남구
 * 그래프뿐이던 시절의 종로구 클릭)가 조용히 엉뚱한 먼 노드로 스냅되는 문제가 있었다.
 */
public class NodeSnapTooFarException extends RuntimeException {

    public NodeSnapTooFarException(double lat, double lon, double distanceM, double maxDistanceM) {
        super(
                "좌표(%.6f, %.6f)에서 가장 가까운 노드까지 거리가 %.1fm로 허용 임계값(%.1fm)을 초과합니다"
                        .formatted(lat, lon, distanceM, maxDistanceM));
    }
}
