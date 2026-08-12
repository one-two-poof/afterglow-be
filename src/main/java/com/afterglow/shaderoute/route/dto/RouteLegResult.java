package com.afterglow.shaderoute.route.dto;

public record RouteLegResult(
        double lambda,
        String label,
        double distanceM,
        double avgShadeRatio,
        LineStringGeometry geometry
) {
}
