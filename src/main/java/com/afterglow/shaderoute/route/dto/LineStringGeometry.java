package com.afterglow.shaderoute.route.dto;

import java.util.List;

public record LineStringGeometry(String type, List<double[]> coordinates) {

    public static LineStringGeometry of(List<double[]> coordinates) {
        return new LineStringGeometry("LineString", coordinates);
    }
}
