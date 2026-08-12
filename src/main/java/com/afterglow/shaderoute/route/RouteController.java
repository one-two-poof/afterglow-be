package com.afterglow.shaderoute.route;

import com.afterglow.shaderoute.graph.NodeSnap;
import com.afterglow.shaderoute.graph.NodeSnapTooFarException;
import com.afterglow.shaderoute.route.dto.LatLon;
import com.afterglow.shaderoute.route.dto.RouteLegResult;
import com.afterglow.shaderoute.route.dto.RouteRequest;
import com.afterglow.shaderoute.route.dto.RouteResponse;
import com.afterglow.shaderoute.route.dto.SnappedPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final double LAMBDA_SHORTEST = 0.0;
    private static final double LAMBDA_SHADY = 0.8;

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/route")
    public RouteResponse route(@RequestBody RouteRequest request) {
        LatLon from = request.from();
        LatLon to = request.to();
        String at = request.at();
        ZonedDateTime seoulDateTime = toSeoulZonedDateTime(at);
        LocalTime seoulTime = seoulDateTime.toLocalTime();
        MonthDay requestDate = MonthDay.from(seoulDateTime);
        int bucket = routeService.bucketIndexFor(seoulTime);

        NodeSnap fromSnap;
        NodeSnap toSnap;
        try {
            fromSnap = routeService.snap(from.lat(), from.lon());
            toSnap = routeService.snap(to.lat(), to.lon());
        } catch (NodeSnapTooFarException e) {
            log.warn("스냅 거리 초과: from={} to={} at={} — {}", from, to, at, e.getMessage());
            throw e;
        }
        log.info(
                "route 요청: from={} (스냅 {}m) to={} (스냅 {}m) at={}",
                from, "%.1f".formatted(fromSnap.distanceM()), to, "%.1f".formatted(toSnap.distanceM()), at);

        RouteLegResult shortest;
        RouteLegResult shady;
        try {
            shortest = routeService.findRoute(
                    fromSnap.nodeId(), toSnap.nodeId(), bucket, requestDate, LAMBDA_SHORTEST, "shortest");
            shady = routeService.findRoute(
                    fromSnap.nodeId(), toSnap.nodeId(), bucket, requestDate, LAMBDA_SHADY, "shady");
        } catch (RouteNotFoundException e) {
            log.warn("경로 없음: from={} to={} at={} — {}", from, to, at, e.getMessage());
            throw e;
        }

        return new RouteResponse(
                at,
                new SnappedPoint(from.lat(), from.lon(), fromSnap.nodeId(), fromSnap.distanceM()),
                new SnappedPoint(to.lat(), to.lon(), toSnap.nodeId(), toSnap.distanceM()),
                List.of(shortest, shady)
        );
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRouteNotFound(RouteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    /** RouteNotFoundException(경로 없음, 404)과 구분: 클릭 좌표가 그래프가 커버하는
     * 범위 밖이라 애초에 노드로 스냅할 수 없는 경우. */
    @ExceptionHandler(NodeSnapTooFarException.class)
    public ResponseEntity<Map<String, String>> handleNodeSnapTooFar(NodeSnapTooFarException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** ISO8601(오프셋 있음/없음 둘 다 허용). 오프셋이 없으면 이미 Asia/Seoul 시각이라고 본다. */
    private static ZonedDateTime toSeoulZonedDateTime(String at) {
        try {
            return OffsetDateTime.parse(at).atZoneSameInstant(SEOUL);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(at).atZone(SEOUL);
        }
    }
}
