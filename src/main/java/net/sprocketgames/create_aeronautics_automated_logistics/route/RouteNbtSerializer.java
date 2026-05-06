package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public final class RouteNbtSerializer {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String DIMENSION = "dimension";
    private static final String POINTS = "points";
    private static final String STOPS = "stops";
    private static final String CONTROLLER = "controller";
    private static final String PLAYBACK_MODE = "playbackMode";
    private static final String STATUS = "status";
    private static final String OWNER_ID = "ownerId";

    private RouteNbtSerializer() {
    }

    public static CompoundTag write(Route route) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, route.id().value());
        tag.putString(NAME, route.name());
        tag.putString(DIMENSION, route.dimension().location().toString());
        tag.put(POINTS, writePoints(route.points()));
        tag.put(STOPS, writeStops(route.stops()));
        tag.put(CONTROLLER, writeController(route.linkedController()));
        tag.putString(PLAYBACK_MODE, route.playbackMode().name());
        tag.putString(STATUS, route.status().name());
        route.ownerId().ifPresent(ownerId -> tag.putUUID(OWNER_ID, ownerId));
        return tag;
    }

    public static Optional<Route> read(CompoundTag tag) {
        try {
            RouteId id = new RouteId(tag.getUUID(ID));
            String name = tag.getString(NAME);
            ResourceKey<Level> dimension = readDimension(tag.getString(DIMENSION));
            List<RoutePoint> points = readPoints(tag.getList(POINTS, Tag.TAG_COMPOUND), dimension);
            List<RouteStop> stops = tag.contains(STOPS, Tag.TAG_LIST)
                    ? readStops(tag.getList(STOPS, Tag.TAG_COMPOUND), points.size())
                    : List.of();
            VehicleControllerRef controller = readController(tag.getCompound(CONTROLLER));
            PlaybackMode playbackMode = PlaybackMode.valueOf(tag.getString(PLAYBACK_MODE));
            RouteStatus status = RouteStatus.valueOf(tag.getString(STATUS));
            Optional<UUID> ownerId = tag.hasUUID(OWNER_ID) ? Optional.of(tag.getUUID(OWNER_ID)) : Optional.empty();

            if (points.size() < 2 || !controller.dimension().equals(dimension)) {
                return Optional.empty();
            }

            return Optional.of(new Route(id, name, dimension, points, controller, playbackMode, status, stops, ownerId));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static ListTag writeStops(List<RouteStop> stops) {
        ListTag list = new ListTag();
        for (RouteStop stop : stops) {
            CompoundTag stopTag = new CompoundTag();
            stopTag.putUUID("id", stop.id());
            stopTag.putString("name", stop.name());
            stopTag.putInt("pointIndex", stop.pointIndex());
            stopTag.put("waitCondition", writeWaitCondition(stop.waitCondition()));
            stop.dockPos().ifPresent(pos -> {
                stopTag.putInt("dockX", pos.getX());
                stopTag.putInt("dockY", pos.getY());
                stopTag.putInt("dockZ", pos.getZ());
            });
            list.add(stopTag);
        }
        return list;
    }

    public static List<RouteStop> readStops(ListTag stopsTag, int pointCount) {
        List<RouteStop> stops = new ArrayList<>();
        for (int i = 0; i < stopsTag.size(); i++) {
            CompoundTag stopTag = stopsTag.getCompound(i);
            int pointIndex = stopTag.getInt("pointIndex");
            if (pointIndex < 0 || pointIndex >= pointCount) {
                throw new IllegalArgumentException("route stop point index outside route points");
            }
            Optional<BlockPos> dockPos = stopTag.contains("dockX", Tag.TAG_ANY_NUMERIC)
                    && stopTag.contains("dockY", Tag.TAG_ANY_NUMERIC)
                    && stopTag.contains("dockZ", Tag.TAG_ANY_NUMERIC)
                    ? Optional.of(new BlockPos(stopTag.getInt("dockX"), stopTag.getInt("dockY"), stopTag.getInt("dockZ")))
                    : Optional.empty();
            stops.add(new RouteStop(
                    stopTag.hasUUID("id") ? stopTag.getUUID("id") : UUID.randomUUID(),
                    stopTag.getString("name").isBlank() ? "Stop " + (i + 1) : stopTag.getString("name"),
                    pointIndex,
                    readWaitCondition(stopTag.getCompound("waitCondition")),
                    dockPos
            ));
        }
        return stops;
    }

    private static CompoundTag writeWaitCondition(WaitCondition waitCondition) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", waitCondition.type().name());
        tag.putInt("durationTicks", waitCondition.durationTicks());
        tag.putInt("idleTicks", waitCondition.idleTicks());
        tag.putInt("maxTicks", waitCondition.maxTicks());
        tag.putBoolean("failOnTimeout", waitCondition.failOnTimeout());
        return tag;
    }

    private static WaitCondition readWaitCondition(CompoundTag tag) {
        if (!tag.contains("type", Tag.TAG_STRING)) {
            return WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS);
        }
        return new WaitCondition(
                WaitConditionType.valueOf(tag.getString("type")),
                tag.getInt("durationTicks"),
                tag.getInt("idleTicks"),
                tag.getInt("maxTicks"),
                tag.getBoolean("failOnTimeout")
        );
    }

    private static ListTag writePoints(List<RoutePoint> points) {
        ListTag list = new ListTag();
        for (RoutePoint point : points) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble("x", point.position().x());
            pointTag.putDouble("y", point.position().y());
            pointTag.putDouble("z", point.position().z());
            point.yaw().ifPresent(yaw -> pointTag.putFloat("yaw", yaw));
            point.rotation().ifPresent(rotation -> {
                pointTag.putDouble("rotX", rotation.x());
                pointTag.putDouble("rotY", rotation.y());
                pointTag.putDouble("rotZ", rotation.z());
                pointTag.putDouble("rotW", rotation.w());
            });
            pointTag.putLong("tickOffset", point.tickOffset());
            pointTag.putString(DIMENSION, point.dimension().location().toString());
            list.add(pointTag);
        }
        return list;
    }

    private static List<RoutePoint> readPoints(ListTag pointsTag, ResourceKey<Level> routeDimension) {
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < pointsTag.size(); i++) {
            CompoundTag pointTag = pointsTag.getCompound(i);
            ResourceKey<Level> pointDimension = readDimension(pointTag.getString(DIMENSION));
            if (!pointDimension.equals(routeDimension)) {
                throw new IllegalArgumentException("route point dimension mismatch");
            }

            Optional<Float> yaw = pointTag.contains("yaw", Tag.TAG_FLOAT)
                    ? Optional.of(pointTag.getFloat("yaw"))
                    : Optional.empty();
            Optional<RouteRotation> rotation = pointTag.contains("rotX", Tag.TAG_DOUBLE)
                    && pointTag.contains("rotY", Tag.TAG_DOUBLE)
                    && pointTag.contains("rotZ", Tag.TAG_DOUBLE)
                    && pointTag.contains("rotW", Tag.TAG_DOUBLE)
                    ? Optional.of(new RouteRotation(
                            pointTag.getDouble("rotX"),
                            pointTag.getDouble("rotY"),
                            pointTag.getDouble("rotZ"),
                            pointTag.getDouble("rotW")
                    ))
                    : Optional.empty();
            points.add(new RoutePoint(
                    new Vec3(pointTag.getDouble("x"), pointTag.getDouble("y"), pointTag.getDouble("z")),
                    yaw,
                    rotation,
                    pointTag.getLong("tickOffset"),
                    pointDimension
            ));
        }
        return points;
    }

    private static CompoundTag writeController(VehicleControllerRef controller) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", controller.controllerType().toString());
        tag.putString(DIMENSION, controller.dimension().location().toString());
        controller.vehicleId().ifPresent(vehicleId -> tag.putUUID("vehicleId", vehicleId));
        controller.controllerPos().ifPresent(pos -> {
            tag.putInt("controllerX", pos.getX());
            tag.putInt("controllerY", pos.getY());
            tag.putInt("controllerZ", pos.getZ());
        });
        return tag;
    }

    private static VehicleControllerRef readController(CompoundTag tag) {
        ResourceLocation controllerType = ResourceLocation.parse(tag.getString("type"));
        ResourceKey<Level> dimension = readDimension(tag.getString(DIMENSION));
        Optional<UUID> vehicleId = tag.hasUUID("vehicleId") ? Optional.of(tag.getUUID("vehicleId")) : Optional.empty();
        Optional<BlockPos> controllerPos = tag.contains("controllerX", Tag.TAG_ANY_NUMERIC)
                && tag.contains("controllerY", Tag.TAG_ANY_NUMERIC)
                && tag.contains("controllerZ", Tag.TAG_ANY_NUMERIC)
                ? Optional.of(new BlockPos(tag.getInt("controllerX"), tag.getInt("controllerY"), tag.getInt("controllerZ")))
                : Optional.empty();
        return new VehicleControllerRef(controllerType, dimension, vehicleId, controllerPos);
    }

    private static ResourceKey<Level> readDimension(String value) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(value));
    }
}
