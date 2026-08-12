package com.afterglow.shaderoute.route.dto;

import java.util.List;

public record RouteResponse(
        String at,
        SnappedPoint from,
        SnappedPoint to,
        List<RouteLegResult> routes
) {
}
