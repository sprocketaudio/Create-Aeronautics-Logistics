package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import java.util.UUID;

public record RouteId(UUID value) {
    public RouteId {
        Objects.requireNonNull(value, "value");
    }

    public static RouteId create() {
        return new RouteId(UUID.randomUUID());
    }
}
