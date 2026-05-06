package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

public record RouteRotation(double x, double y, double z, double w) {
    public RouteRotation {
        double lengthSquared = x * x + y * y + z * z + w * w;
        if (lengthSquared <= 1.0E-12D) {
            throw new IllegalArgumentException("rotation quaternion must be non-zero");
        }
    }

    public static RouteRotation of(Quaterniondc quaternion) {
        Objects.requireNonNull(quaternion, "quaternion");
        Quaterniond normalized = new Quaterniond(quaternion).normalize();
        return new RouteRotation(normalized.x, normalized.y, normalized.z, normalized.w);
    }

    public Quaterniond toQuaterniond() {
        return new Quaterniond(x, y, z, w).normalize();
    }

    public RouteRotation slerp(RouteRotation target, double delta) {
        Objects.requireNonNull(target, "target");
        Quaterniond interpolated = toQuaterniond().slerp(target.toQuaterniond(), delta);
        return RouteRotation.of(interpolated);
    }
}
