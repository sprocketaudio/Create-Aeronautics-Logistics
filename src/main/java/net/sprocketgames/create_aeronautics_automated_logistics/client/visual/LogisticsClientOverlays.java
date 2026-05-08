package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllSpecialTextures;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LogisticsClientOverlays {
    private static final int LANDING_COLOR = 0x95E06C;
    private static final int ROUTE_COLOR_A = 0x7AD7FF;
    private static final int ROUTE_COLOR_B = 0x2F9BFF;
    private static final int ROUTE_PULSE_COLOR = 0xE6FCFF;
    private static final int START_COLOR = 0x95E06C;
    private static final int END_COLOR = 0xFFD36C;
    private static final int LANDING_SEGMENTS = 48;
    private static final int LATITUDE_RINGS = 5;
    private static final int ROUTE_ARROW_SPACING = 12;

    private static Optional<LandingAreaOverlay> landingAreaOverlay = Optional.empty();
    private static List<Vec3> flightPath = List.of();
    private static Optional<UUID> previewedRouteId = Optional.empty();

    private LogisticsClientOverlays() {
    }

    public static void toggleLandingArea(BlockPos stationPos, double radius) {
        if (landingAreaOverlay.isPresent() && landingAreaOverlay.get().stationPos().equals(stationPos)) {
            clearLandingArea();
            return;
        }
        clearLandingArea();
        landingAreaOverlay = Optional.of(new LandingAreaOverlay(stationPos.immutable(), radius));
    }

    public static void clearLandingArea() {
        landingAreaOverlay.ifPresent(LogisticsClientOverlays::removeLandingArea);
        landingAreaOverlay = Optional.empty();
    }

    public static boolean isLandingAreaVisible(BlockPos stationPos) {
        return landingAreaOverlay.isPresent() && landingAreaOverlay.get().stationPos().equals(stationPos);
    }

    public static void setFlightPath(List<Vec3> points) {
        removeFlightPath(flightPath);
        flightPath = List.copyOf(points);
    }

    public static void setPreviewedRouteId(UUID routeId) {
        previewedRouteId = Optional.ofNullable(routeId);
    }

    public static Optional<UUID> previewedRouteId() {
        return previewedRouteId;
    }

    public static void clearFlightPath() {
        removeFlightPath(flightPath);
        flightPath = List.of();
        previewedRouteId = Optional.empty();
    }

    public static boolean hasFlightPath() {
        return flightPath.size() >= 2;
    }

    public static void refresh() {
        landingAreaOverlay.ifPresent(LogisticsClientOverlays::showLandingArea);
        if (flightPath.size() >= 2) {
            showFlightPath(flightPath);
        }
    }

    private static void showLandingArea(LandingAreaOverlay overlay) {
        Vec3 center = Vec3.atCenterOf(overlay.stationPos());
        double radius = overlay.radius();

        for (int axis = 0; axis < 3; axis++) {
            Vec3 previous = pointOnCircle(center, radius, 0, axis);
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                double theta = Math.PI * 2.0D * i / LANDING_SEGMENTS;
                Vec3 current = pointOnCircle(center, radius, theta, axis);
                Outliner.getInstance()
                        .showLine(Pair.of(Pair.of("landing_area_circle", overlay.stationPos()), axis + ":" + i), previous, current)
                        .colored(LANDING_COLOR)
                        .lineWidth(1 / 32f)
                        .disableLineNormals();
                previous = current;
            }
        }

        for (int ring = 1; ring <= LATITUDE_RINGS; ring++) {
            double normalized = -1.0D + (2.0D * ring) / (LATITUDE_RINGS + 1.0D);
            double yOffset = normalized * radius;
            double ringRadius = Math.sqrt(Math.max(0.0D, radius * radius - yOffset * yOffset));
            Vec3 ringCenter = center.add(0, yOffset, 0);
            Vec3 previous = pointOnCircle(ringCenter, ringRadius, 0, 0);
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                double theta = Math.PI * 2.0D * i / LANDING_SEGMENTS;
                Vec3 current = pointOnCircle(ringCenter, ringRadius, theta, 0);
                Outliner.getInstance()
                        .showLine(Pair.of(Pair.of("landing_area_latitude", overlay.stationPos()), ring + ":" + i), previous, current)
                        .colored(LANDING_COLOR)
                        .lineWidth(1 / 32f)
                        .disableLineNormals();
                previous = current;
            }
        }
    }

    private static void removeLandingArea(LandingAreaOverlay overlay) {
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                Outliner.getInstance().remove(Pair.of(Pair.of("landing_area_circle", overlay.stationPos()), axis + ":" + i));
            }
        }
        for (int ring = 1; ring <= LATITUDE_RINGS; ring++) {
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                Outliner.getInstance().remove(Pair.of(Pair.of("landing_area_latitude", overlay.stationPos()), ring + ":" + i));
            }
        }
    }

    private static void showFlightPath(List<Vec3> points) {
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        int pulsingSegment = points.size() <= 1 ? -1 : (int) (gameTime % (points.size() - 1));

        for (int i = 1; i < points.size(); i++) {
            int color = i == pulsingSegment
                    ? ROUTE_PULSE_COLOR
                    : (i % 2 == 0 ? ROUTE_COLOR_A : ROUTE_COLOR_B);
            Outliner.getInstance()
                    .showLine(Pair.of("flight_path_segment", Integer.valueOf(i)), points.get(i - 1), points.get(i))
                    .colored(color)
                    .lineWidth(i == pulsingSegment ? 1 / 8f : 1 / 16f)
                    .disableLineNormals();

            if (i % ROUTE_ARROW_SPACING == 0 || i == points.size() - 1) {
                showDirectionArrow(i, points.get(i - 1), points.get(i), color);
            }
        }

        Vec3 start = points.getFirst();
        Vec3 end = points.getLast();
        Outliner.getInstance()
                .showAABB("flight_path_start", AABB.ofSize(start, .35, .35, .35))
                .colored(START_COLOR)
                .lineWidth(1 / 16f)
                .disableLineNormals()
                .withFaceTexture(AllSpecialTextures.SELECTION);
        Outliner.getInstance()
                .showAABB("flight_path_end", AABB.ofSize(end, .35, .35, .35))
                .colored(END_COLOR)
                .lineWidth(1 / 16f)
                .disableLineNormals()
                .withFaceTexture(AllSpecialTextures.SELECTION);
    }

    private static void showDirectionArrow(int index, Vec3 from, Vec3 to, int color) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        direction = direction.normalize();
        Vec3 midpoint = from.add(to).scale(0.5D);
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6D) {
            side = direction.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize();

        double stemHalf = 0.35D;
        double wingHalf = 0.18D;
        Vec3 tip = midpoint.add(direction.scale(stemHalf));
        Vec3 tail = midpoint.subtract(direction.scale(stemHalf));
        Vec3 left = tail.add(side.scale(wingHalf));
        Vec3 right = tail.subtract(side.scale(wingHalf));

        Outliner.getInstance()
                .showLine(Pair.of("flight_path_arrow_left", Integer.valueOf(index)), left, tip)
                .colored(color)
                .lineWidth(1 / 16f)
                .disableLineNormals();
        Outliner.getInstance()
                .showLine(Pair.of("flight_path_arrow_right", Integer.valueOf(index)), right, tip)
                .colored(color)
                .lineWidth(1 / 16f)
                .disableLineNormals();
    }

    private static void removeFlightPath(List<Vec3> points) {
        for (int i = 1; i < points.size(); i++) {
            Outliner.getInstance().remove(Pair.of("flight_path_segment", Integer.valueOf(i)));
            if (i % ROUTE_ARROW_SPACING == 0 || i == points.size() - 1) {
                Outliner.getInstance().remove(Pair.of("flight_path_arrow_left", Integer.valueOf(i)));
                Outliner.getInstance().remove(Pair.of("flight_path_arrow_right", Integer.valueOf(i)));
            }
        }
        Outliner.getInstance().remove("flight_path_start");
        Outliner.getInstance().remove("flight_path_end");
    }

    private static Vec3 pointOnCircle(Vec3 center, double radius, double theta, int axis) {
        double c = Math.cos(theta) * radius;
        double s = Math.sin(theta) * radius;
        return switch (axis) {
            case 0 -> new Vec3(center.x + c, center.y, center.z + s); // XZ
            case 1 -> new Vec3(center.x + c, center.y + s, center.z); // XY
            case 2 -> new Vec3(center.x, center.y + c, center.z + s); // YZ
            default -> new Vec3(center.x + c, center.y, center.z + s);
        };
    }

    private record LandingAreaOverlay(BlockPos stationPos, double radius) {
    }
}
