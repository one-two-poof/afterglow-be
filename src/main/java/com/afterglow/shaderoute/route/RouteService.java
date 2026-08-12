package com.afterglow.shaderoute.route;

import com.afterglow.shaderoute.graph.GraphEdge;
import com.afterglow.shaderoute.graph.NodeSnap;
import com.afterglow.shaderoute.graph.ShadeGraph;
import com.afterglow.shaderoute.route.dto.LineStringGeometry;
import com.afterglow.shaderoute.route.dto.RouteLegResult;
import org.springframework.stereotype.Service;

import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** 그늘 가중 Dijkstra. cost(edge) = length + lambda * length * (1 - shadeRatio). */
@Service
public class RouteService {

    private final ShadeGraph graph;

    public RouteService(ShadeGraph graph) {
        this.graph = graph;
    }

    public NodeSnap snap(double lat, double lon) {
        return graph.nearestNode(lat, lon);
    }

    public int bucketIndexFor(java.time.LocalTime time) {
        return graph.bucketIndexFor(time);
    }

    /** 대표일 4개 중 requestDate와 월/일 기준으로 가장 가까운 날짜를 고른다(연말/연초 경계 순환 거리 고려). */
    public MonthDay nearestRepresentativeDate(MonthDay requestDate) {
        return graph.nearestRepresentativeDate(requestDate);
    }

    public RouteLegResult findRoute(
            long fromNode, long toNode, int bucket, MonthDay requestDate, double lambda, String label
    ) {
        if (fromNode == toNode) {
            double[] c = graph.nodeCoord(fromNode);
            return new RouteLegResult(lambda, label, 0.0, 0.0, LineStringGeometry.of(List.of(c)));
        }

        Map<Long, Double> best = new HashMap<>();
        Map<Long, GraphEdge> cameFrom = new HashMap<>();
        PriorityQueue<Frontier> pq = new PriorityQueue<>();

        best.put(fromNode, 0.0);
        pq.add(new Frontier(fromNode, 0.0));

        while (!pq.isEmpty()) {
            Frontier cur = pq.poll();
            if (cur.cost > best.getOrDefault(cur.nodeId, Double.MAX_VALUE)) {
                continue; // stale entry
            }
            if (cur.nodeId == toNode) {
                break;
            }
            for (GraphEdge edge : graph.adjacency().getOrDefault(cur.nodeId, List.of())) {
                double shade = graph.shadeRatio(requestDate, bucket, edge.edgeId());
                double edgeCost = edge.length() + lambda * edge.length() * (1 - shade);
                double newCost = cur.cost + edgeCost;
                if (newCost < best.getOrDefault(edge.v(), Double.MAX_VALUE)) {
                    best.put(edge.v(), newCost);
                    cameFrom.put(edge.v(), edge);
                    pq.add(new Frontier(edge.v(), newCost));
                }
            }
        }

        if (!best.containsKey(toNode)) {
            throw new RouteNotFoundException(
                    "노드 %d에서 %d로 가는 경로를 찾을 수 없습니다 (그래프 비연결)".formatted(fromNode, toNode));
        }

        return buildResult(fromNode, toNode, cameFrom, lambda, label, bucket, requestDate);
    }

    private RouteLegResult buildResult(
            long fromNode,
            long toNode,
            Map<Long, GraphEdge> cameFrom,
            double lambda,
            String label,
            int bucket,
            MonthDay requestDate
    ) {
        List<GraphEdge> pathEdges = new ArrayList<>();
        long node = toNode;
        while (node != fromNode) {
            GraphEdge edge = cameFrom.get(node);
            pathEdges.add(edge);
            node = edge.u();
        }
        java.util.Collections.reverse(pathEdges);

        List<double[]> coordinates = new ArrayList<>();
        double totalLength = 0.0;
        double shadeWeightedSum = 0.0;

        for (GraphEdge edge : pathEdges) {
            List<double[]> edgeCoords = edge.coords();
            int startIdx = coordinates.isEmpty() ? 0 : 1; // 인접 엣지 경계 좌표 중복 제거
            for (int i = startIdx; i < edgeCoords.size(); i++) {
                coordinates.add(edgeCoords.get(i));
            }
            double shade = graph.shadeRatio(requestDate, bucket, edge.edgeId());
            totalLength += edge.length();
            shadeWeightedSum += edge.length() * shade;
        }

        double avgShadeRatio = totalLength > 0 ? shadeWeightedSum / totalLength : 0.0;
        return new RouteLegResult(lambda, label, totalLength, avgShadeRatio, LineStringGeometry.of(coordinates));
    }

    private record Frontier(long nodeId, double cost) implements Comparable<Frontier> {
        @Override
        public int compareTo(Frontier o) {
            return Double.compare(cost, o.cost);
        }
    }
}
