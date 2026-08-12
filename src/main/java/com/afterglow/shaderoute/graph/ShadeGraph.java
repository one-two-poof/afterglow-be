package com.afterglow.shaderoute.graph;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.kdtree.KdNode;
import org.locationtech.jts.index.kdtree.KdTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * data/out/ 산출물(batch/src/main.py 결과)을 부팅 시 힙에 로드한다.
 * 서버는 런타임에 기하 연산을 하지 않고 이 그래프와 shade_table만 조회한다.
 */
@Component
public class ShadeGraph {

    private static final Logger log = LoggerFactory.getLogger(ShadeGraph.class);
    private static final double EARTH_RADIUS_M = 6371000.0;
    // MonthDay.atYear()로 day-of-year를 뽑기 위한 기준 연도. 2/29를 포함해 모든
    // MonthDay가 유효해야 하므로 반드시 윤년이어야 한다(달력 순서 비교용일 뿐 실제
    // 연도와는 무관).
    private static final int LEAP_REFERENCE_YEAR = 2024;
    private static final int DAYS_IN_LEAP_YEAR = 366;

    // nodes.bin 레코드 크기: int64 node_id(8) + float64 lon(8) + float64 lat(8), 빅엔디안.
    // batch/src/main.py의 NODES_BIN_DTYPE과 반드시 짝이 맞아야 한다.
    private static final int NODE_RECORD_BYTES = 24;

    // lon/lat(도)는 미터 단위로 등방적이지 않다 (위도 37.5°에서 경도 1도 ≈ 88.8km,
    // 위도 1도 ≈ 111km). 정식 좌표계 변환 없이, 서울 중심 위도 기준 로컬 등장방형
    // 근사로 KdTree 후보를 좁히고 최종 거리는 haversineMeters()로 정확히 계산한다.
    private static final double SEOUL_REF_LAT_RAD = Math.toRadians(37.5665);
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private static final double INITIAL_SEARCH_RADIUS_M = 500.0;
    private static final int MAX_SEARCH_ATTEMPTS = 4; // 500 -> 1000 -> 2000 -> 4000m

    private final ObjectMapper objectMapper;

    @Value("${shade.data-dir}")
    private String dataDir;

    @Value("${shade.max-snap-distance-m:300}")
    private double maxSnapDistanceM;

    private Map<Long, double[]> nodeCoords;      // nodeId -> [lon, lat]
    private KdTree spatialIndex;                 // 근사 등장방형 좌표(m) 기준, data = nodeId(Long)
    private Map<Long, List<GraphEdge>> adjacency; // u -> outgoing edges
    private Map<MonthDay, byte[]> shadeTablesByDate; // 대표일(월/일, 연도 무시)별 row-major [bucket][edgeId]
    private int nBuckets;
    private int nEdges;
    private int bucketStartMinutes;
    private int bucketIntervalMinutes;

    public ShadeGraph(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        Path base = Path.of(dataDir);

        JsonNode meta = objectMapper.readTree(base.resolve("shade_meta.json").toFile());
        nBuckets = meta.get("n_buckets").asInt();
        nEdges = meta.get("n_edges").asInt();
        bucketStartMinutes = parseHHmmToMinutes(meta.get("bucket_start").asText());
        bucketIntervalMinutes = meta.get("bucket_minutes").asInt();

        shadeTablesByDate = new LinkedHashMap<>();
        for (JsonNode dateNode : meta.get("representative_dates")) {
            String dateStr = dateNode.asText(); // "YYYY-MM-DD"
            LocalDate date = LocalDate.parse(dateStr);
            MonthDay monthDay = MonthDay.from(date);
            String fileName = "shade_table_" + dateStr.replace("-", "") + ".bin";

            byte[] table = Files.readAllBytes(base.resolve(fileName));
            if (table.length != nBuckets * nEdges) {
                throw new IllegalStateException(
                        "%s 크기가 shade_meta.json과 다릅니다: 기대값 %d bytes (n_buckets=%d * n_edges=%d), 실제 %d bytes"
                                .formatted(fileName, nBuckets * nEdges, nBuckets, nEdges, table.length));
            }
            shadeTablesByDate.put(monthDay, table);
        }
        if (shadeTablesByDate.isEmpty()) {
            throw new IllegalStateException("shade_meta.json의 representative_dates가 비어 있습니다");
        }

        loadNodes(base.resolve("nodes.bin"));

        int edgeCount = loadEdges(base.resolve("edges.geojson"));

        log.info(
                "ShadeGraph 로딩 완료: 노드 {}개, 엣지 {}개, 버킷 {}개, 대표일 {}개({}), 최대 스냅 거리 {}m",
                nodeCoords.size(),
                edgeCount,
                nBuckets,
                shadeTablesByDate.size(),
                shadeTablesByDate.keySet(),
                maxSnapDistanceM);
    }

    /** nodes.bin: 고정 24바이트 레코드(int64 node_id, float64 lon, float64 lat), 빅엔디안. */
    private void loadNodes(Path nodesBinPath) throws IOException {
        long fileSize = Files.size(nodesBinPath);
        if (fileSize % NODE_RECORD_BYTES != 0) {
            throw new IllegalStateException(
                    "nodes.bin 크기(%d bytes)가 레코드 크기(%d)의 배수가 아닙니다".formatted(fileSize, NODE_RECORD_BYTES));
        }
        long recordCount = fileSize / NODE_RECORD_BYTES;

        nodeCoords = new HashMap<>();
        spatialIndex = new KdTree();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(nodesBinPath)))) {
            for (long i = 0; i < recordCount; i++) {
                long nodeId = in.readLong();
                double lon = in.readDouble();
                double lat = in.readDouble();
                nodeCoords.put(nodeId, new double[]{lon, lat});
                spatialIndex.insert(toApproxMeters(lat, lon), nodeId);
            }
        }
    }

    /**
     * edges.geojson을 스트리밍으로 읽는다. 서울 전체 규모(114만 엣지, 280MB)에서
     * {@code objectMapper.readTree(file)}로 전체를 트리 모델에 한 번에 올리면 JsonNode당
     * 객체 오버헤드가 누적돼 JVM 기본 힙(~2GB)에서 OutOfMemoryError가 난다(실측 확인).
     * JsonParser로 최상위를 토큰 단위로 훑다가 "features" 배열 원소 하나씩만
     * {@code objectMapper.readTree(parser)}로 파싱해 피처 1개 분량만 메모리에 머물게 한다.
     */
    private int loadEdges(Path edgesGeoJsonPath) throws IOException {
        adjacency = new HashMap<>();
        Set<Long> missingNodes = new HashSet<>();
        int edgeCount = 0;

        JsonFactory jsonFactory = objectMapper.getFactory();
        try (JsonParser parser = jsonFactory.createParser(edgesGeoJsonPath.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("edges.geojson 최상위가 객체가 아닙니다");
            }

            boolean foundFeatures = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if (!"features".equals(fieldName)) {
                    parser.skipChildren();
                    continue;
                }
                foundFeatures = true;
                if (parser.currentToken() != JsonToken.START_ARRAY) {
                    throw new IllegalStateException("edges.geojson의 features가 배열이 아닙니다");
                }

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode feature = objectMapper.readTree(parser); // 이 피처 1개만 트리로
                    JsonNode props = feature.get("properties");
                    int edgeId = props.get("edge_id").asInt();
                    long u = props.get("u").asLong();
                    long v = props.get("v").asLong();
                    double length = props.get("length").asDouble();

                    List<double[]> coords = new ArrayList<>();
                    for (JsonNode c : feature.get("geometry").get("coordinates")) {
                        coords.add(new double[]{c.get(0).asDouble(), c.get(1).asDouble()});
                    }

                    if (!nodeCoords.containsKey(u)) missingNodes.add(u);
                    if (!nodeCoords.containsKey(v)) missingNodes.add(v);

                    adjacency.computeIfAbsent(u, k -> new ArrayList<>())
                            .add(new GraphEdge(u, v, edgeId, length, coords));
                    edgeCount++;
                }
            }
            if (!foundFeatures) {
                throw new IllegalStateException("edges.geojson에 features 필드가 없습니다");
            }
        }

        if (!missingNodes.isEmpty()) {
            throw new IllegalStateException(
                    "edges.geojson이 참조하는 노드 %d개가 nodes.bin에 없습니다 (예: %s)"
                            .formatted(missingNodes.size(), missingNodes.iterator().next()));
        }
        return edgeCount;
    }

    private static Coordinate toApproxMeters(double lat, double lon) {
        double x = lon * Math.cos(SEOUL_REF_LAT_RAD) * METERS_PER_DEGREE_LAT;
        double y = lat * METERS_PER_DEGREE_LAT;
        return new Coordinate(x, y);
    }

    private static int parseHHmmToMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    public Map<Long, List<GraphEdge>> adjacency() {
        return adjacency;
    }

    public double[] nodeCoord(long nodeId) {
        return nodeCoords.get(nodeId);
    }

    /**
     * 그늘 비율(0~1). requestDate와 가장 가까운(월/일 기준, 순환 거리) 대표일의
     * shade_table을 골라 조회한다. shade_table은 uint8이라 Java byte(signed)를
     * & 0xFF로 읽어야 한다.
     */
    public double shadeRatio(MonthDay requestDate, int bucketIndex, int edgeId) {
        byte[] table = shadeTablesByDate.get(nearestRepresentativeDate(requestDate));
        int idx = bucketIndex * nEdges + edgeId;
        return (table[idx] & 0xFF) / 255.0;
    }

    /** 대표일 중 requestDate와 월/일 기준 순환 거리가 가장 가까운 날짜를 고른다. */
    public MonthDay nearestRepresentativeDate(MonthDay requestDate) {
        MonthDay best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MonthDay candidate : shadeTablesByDate.keySet()) {
            int distance = circularDistanceDays(requestDate, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 두 MonthDay 사이의 날짜 수 차이. 연말(12월)-연초(1월) 경계를 넘어가는 쪽이 더 가까우면 그쪽을 택한다. */
    private static int circularDistanceDays(MonthDay a, MonthDay b) {
        int dayOfYearA = a.atYear(LEAP_REFERENCE_YEAR).getDayOfYear();
        int dayOfYearB = b.atYear(LEAP_REFERENCE_YEAR).getDayOfYear();
        int diff = Math.abs(dayOfYearA - dayOfYearB);
        return Math.min(diff, DAYS_IN_LEAP_YEAR - diff);
    }

    public int bucketIndexFor(LocalTime time) {
        int minutesOfDay = time.getHour() * 60 + time.getMinute();
        int idx = (int) Math.round((minutesOfDay - bucketStartMinutes) / (double) bucketIntervalMinutes);
        return Math.max(0, Math.min(nBuckets - 1, idx));
    }

    /**
     * KdTree(근사 좌표)로 후보를 좁힌 뒤 haversineMeters()로 정확한 최근접 노드를 고른다.
     * 서울 전체로 노드가 약 21만 개까지 늘어나며 선형 스캔은 느려져 공간 인덱스로 교체했다.
     * 최근접 노드까지 거리가 maxSnapDistanceM을 넘으면(그래프가 커버하지 않는 지역을
     * 클릭한 경우) 조용히 엉뚱한 먼 노드로 스냅하는 대신 예외를 던진다.
     */
    public NodeSnap nearestNode(double lat, double lon) {
        Coordinate query = toApproxMeters(lat, lon);

        List<KdNode> candidates = List.of();
        double searchRadius = INITIAL_SEARCH_RADIUS_M;
        for (int attempt = 0; attempt < MAX_SEARCH_ATTEMPTS; attempt++) {
            Envelope env = new Envelope(
                    query.x - searchRadius, query.x + searchRadius,
                    query.y - searchRadius, query.y + searchRadius);
            @SuppressWarnings("unchecked")
            List<KdNode> found = spatialIndex.query(env);
            candidates = found;
            if (!candidates.isEmpty()) {
                break;
            }
            searchRadius *= 2;
        }

        if (candidates.isEmpty()) {
            throw new NodeSnapTooFarException(lat, lon, Double.POSITIVE_INFINITY, maxSnapDistanceM);
        }

        long bestId = -1;
        double bestDist = Double.MAX_VALUE;
        for (KdNode node : candidates) {
            long nodeId = (Long) node.getData();
            double[] c = nodeCoords.get(nodeId); // [lon, lat]
            double d = haversineMeters(lat, lon, c[1], c[0]);
            if (d < bestDist) {
                bestDist = d;
                bestId = nodeId;
            }
        }

        if (bestDist > maxSnapDistanceM) {
            throw new NodeSnapTooFarException(lat, lon, bestDist, maxSnapDistanceM);
        }
        return new NodeSnap(bestId, bestDist);
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }
}
