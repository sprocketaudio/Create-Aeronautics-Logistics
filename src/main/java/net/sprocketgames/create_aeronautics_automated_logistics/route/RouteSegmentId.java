package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import java.util.UUID;

public record RouteSegmentId(UUID value) {
    public RouteSegmentId {
        Objects.requireNonNull(value, "value");
    }

    public static RouteSegmentId create() {
        return new RouteSegmentId(UUID.randomUUID());
    }
}
