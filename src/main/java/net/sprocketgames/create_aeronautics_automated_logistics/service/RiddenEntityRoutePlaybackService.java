package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleMotionResult;

public class RiddenEntityRoutePlaybackService implements RoutePlaybackService {
    private static final double ARRIVAL_DISTANCE = 0.5D;
    private static final double ENDPOINT_ARRIVAL_DISTANCE = 0.2D;
    private static final double ENDPOINT_SETTLE_DISTANCE = 0.65D;
    private static final double ARRIVAL_ROTATION_TOLERANCE_DEGREES = 4.0D;
    private static final double MIN_PROGRESS_PER_TICK = 0.001D;
    private static final double MIN_REPLAY_SPEED = 0.08D;
    private static final double MAX_REPLAY_SPEED = 3.0D;
    private static final int JOIN_BLEND_SEGMENTS = 5;
    private static final int ENDPOINT_SETTLE_TICKS = 40;
    private static final int MAX_STALLED_TICKS = 200;
    private static final int INITIAL_STALL_GRACE_TICKS = 60;
    private static final double STATIONARY_SEGMENT_DISTANCE = 0.1D;

    private final Map<RouteId, ActivePlayback> activePlaybacks = new HashMap<>();

    @Override
    public PlaybackOperationResult<RouteId> startPlayback(ServerLevel level, BlockPos stationPos, Route route) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(route, "route");

        if (activePlaybacks.containsKey(route.id())) {
            return PlaybackOperationResult.failure(PlaybackFailure.ALREADY_RUNNING);
        }
        if (!route.dimension().equals(level.dimension())) {
            return PlaybackOperationResult.failure(PlaybackFailure.DIMENSION_MISMATCH);
        }
        if (route.points().size() < 2) {
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        if (route.points().stream().anyMatch(point -> !point.dimension().equals(route.dimension()))) {
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        Optional<AirshipStationBlockEntity> station = stationAt(level, stationPos);
        if (station.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.STATION_MISSING);
        }

        Optional<VehicleController> controller = VehicleControllerResolver.resolve(level, route.linkedController());
        if (controller.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.VEHICLE_MISSING);
        }
        if (!controller.get().isAutomationCapable()) {
            return PlaybackOperationResult.failure(PlaybackFailure.MISSING_CONTROLLER);
        }
        if (ActivePlayback.nearestEndpointDistance(route, controller.get()) > AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get()) {
            return PlaybackOperationResult.failure(PlaybackFailure.START_TOO_FAR_FROM_ROUTE);
        }

        ActivePlayback activePlayback = ActivePlayback.create(route, stationPos, controller.get());
        activePlaybacks.put(route.id(), activePlayback);
        station.get().startPlayback(route);
        return PlaybackOperationResult.success(route.id());
    }

    @Override
    public void stopPlayback(ServerLevel level, RouteId routeId, FailureReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(reason, "reason");

        ActivePlayback activePlayback = activePlaybacks.remove(routeId);
        if (activePlayback == null) {
            return;
        }

        activePlayback.controller().stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            if (reason == FailureReason.NONE) {
                station.stopPlayback();
            } else {
                station.failPlayback(reason);
            }
        });
    }

    @Override
    public void tickPlayback(ServerLevel level) {
        Iterator<ActivePlayback> iterator = activePlaybacks.values().iterator();
        while (iterator.hasNext()) {
            ActivePlayback activePlayback = iterator.next();
            if (!activePlayback.route().dimension().equals(level.dimension())) {
                continue;
            }

            PlaybackFailure failure = tickOne(level, activePlayback);
            if (failure != null) {
                fail(level, activePlayback, failure);
                iterator.remove();
            } else if (activePlayback.completed()) {
                iterator.remove();
            }
        }
    }

    public void tickAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            tickPlayback(level);
        }
    }

    public boolean isRunning(RouteId routeId) {
        return activePlaybacks.containsKey(routeId);
    }

    private PlaybackFailure tickOne(ServerLevel level, ActivePlayback activePlayback) {
        Optional<AirshipStationBlockEntity> station = stationAt(level, activePlayback.stationPos());
        if (station.isEmpty()) {
            return PlaybackFailure.STATION_MISSING;
        }

        VehicleController controller = activePlayback.controller();
        if (!controller.isLoaded(level)) {
            return PlaybackFailure.VEHICLE_UNLOADED;
        }
        if (!controller.isAssembled()) {
            return PlaybackFailure.VEHICLE_MISSING;
        }
        if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get()
                && controller.collisionEntity()
                .map(entity -> entity.horizontalCollision || (entity.verticalCollision && !entity.verticalCollisionBelow))
                .orElse(false)) {
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }

        if (activePlayback.isWaiting()) {
            return tickWaiting(level, station.get(), activePlayback, controller);
        }

        RoutePoint target = activePlayback.targetPoint();
        Vec3 targetPosition = activePlayback.targetPosition();
        Optional<RouteRotation> targetRotation = activePlayback.targetRotation(controller.position());
        double distanceToTarget = controller.position().distanceTo(targetPosition);
        double arrivalDistance = activePlayback.arrivalDistance();
        boolean rotationAligned = activePlayback.isRotationAligned(targetRotation, controller);
        if (activePlayback.shouldHoldAtTarget(distanceToTarget)) {
            controller.moveToward(
                    level,
                    targetPosition,
                    targetRotation,
                    AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                    0.0D
            );
            activePlayback.tickSegment();
            activePlayback.resetProgress(distanceToTarget);
            return null;
        }
        if (distanceToTarget <= arrivalDistance && (!activePlayback.requiresRotationAlignmentForArrival() || rotationAligned)) {
            CreateAeronauticsAutomatedLogistics.LOGGER.info(
                    "Playback {} reached point {} at distance {} position={} target={}",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex(),
                    distanceToTarget,
                    controller.position(),
                    targetPosition
            );
            if (activePlayback.beginWaitAtCurrentTarget()) {
                station.get().waitPlayback(activePlayback.route());
                CreateAeronauticsAutomatedLogistics.LOGGER.info(
                        "Playback {} waiting at stop {} for {} ticks",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                        activePlayback.waitTicksRemaining()
                );
                activePlayback.resetProgress(distanceToTarget);
                return holdAtTarget(level, activePlayback, controller, targetPosition, activePlayback.pointRotation(activePlayback.targetIndex()));
            }
            if (activePlayback.isComplete()) {
                complete(level, activePlayback);
                return null;
            }
            activePlayback.advanceTarget();
            target = activePlayback.targetPoint();
            targetPosition = activePlayback.targetPosition();
            targetRotation = activePlayback.targetRotation(controller.position());
            distanceToTarget = controller.position().distanceTo(targetPosition);
            arrivalDistance = activePlayback.arrivalDistance();
            activePlayback.resetProgress(distanceToTarget);
        } else if (activePlayback.shouldSettleEndpoint(distanceToTarget, rotationAligned)) {
            if (activePlayback.tickEndpointSettle()) {
                CreateAeronauticsAutomatedLogistics.LOGGER.info(
                        "Playback {} accepted endpoint {} after {} settle ticks at distance {} position={} target={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        activePlayback.endpointSettleTicks(),
                        distanceToTarget,
                        controller.position(),
                        targetPosition
                );
                if (activePlayback.beginWaitAtCurrentTarget()) {
                    station.get().waitPlayback(activePlayback.route());
                    CreateAeronauticsAutomatedLogistics.LOGGER.info(
                            "Playback {} waiting at stop {} for {} ticks",
                            activePlayback.route().id().value(),
                            activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                            activePlayback.waitTicksRemaining()
                    );
                    activePlayback.resetProgress(distanceToTarget);
                    return holdAtTarget(level, activePlayback, controller, targetPosition, activePlayback.pointRotation(activePlayback.targetIndex()));
                }
                if (activePlayback.isComplete()) {
                    complete(level, activePlayback);
                    return null;
                }
                activePlayback.advanceTarget();
                target = activePlayback.targetPoint();
                targetPosition = activePlayback.targetPosition();
                targetRotation = activePlayback.targetRotation(controller.position());
                distanceToTarget = controller.position().distanceTo(targetPosition);
                arrivalDistance = activePlayback.arrivalDistance();
                activePlayback.resetProgress(distanceToTarget);
            }
        } else {
            activePlayback.resetEndpointSettle();
            if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get() && activePlayback.isStalled(distanceToTarget)) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Playback {} stalled toward point {}. distance={}, previousDistance={}, stalledTicks={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        distanceToTarget,
                        activePlayback.previousDistance(),
                        activePlayback.stalledTicks()
                );
                return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
            }
        }

        Vec3 collisionTargetPosition = targetPosition;
        if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get()
                && controller.collisionEntity().map(entity -> willCollide(level, entity, collisionTargetPosition)).orElse(false)) {
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }

        double targetSpeed = activePlayback.targetSpeedBlocksPerTick();
        if (activePlayback.shouldLogProgress()) {
            CreateAeronauticsAutomatedLogistics.LOGGER.info(
                    "Playback {} tick {} point {} distance={} targetSpeed={} position={} target={}",
                    activePlayback.route().id().value(),
                    activePlayback.playbackTicks(),
                    activePlayback.targetIndex(),
                    distanceToTarget,
                    targetSpeed,
                    controller.position(),
                    targetPosition
            );
        }

        VehicleMotionResult motionResult = controller.moveToward(
                level,
                targetPosition,
                targetRotation,
                AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                targetSpeed
        );
        activePlayback.tickSegment();
        return motionResult.failureReason()
                .map(this::toPlaybackFailure)
                .orElse(null);
    }

    private PlaybackFailure tickWaiting(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        Vec3 targetPosition = activePlayback.targetPosition();
        Optional<RouteRotation> targetRotation = activePlayback.pointRotation(activePlayback.targetIndex());
        PlaybackFailure failure = holdAtTarget(level, activePlayback, controller, targetPosition, targetRotation);
        if (failure != null) {
            return failure;
        }

        if (activePlayback.tickWait()) {
            CreateAeronauticsAutomatedLogistics.LOGGER.info(
                    "Playback {} finished waiting at stop {}",
                    activePlayback.route().id().value(),
                    activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
            );
            activePlayback.clearWait();
            if (activePlayback.isComplete()) {
                complete(level, activePlayback);
                return null;
            }
            station.resumePlayback();
            activePlayback.advanceTarget();
            activePlayback.resetProgress(controller.position().distanceTo(activePlayback.targetPosition()));
        }
        return null;
    }

    private PlaybackFailure holdAtTarget(
            ServerLevel level,
            ActivePlayback activePlayback,
            VehicleController controller,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation
    ) {
        VehicleMotionResult motionResult = controller.moveToward(
                level,
                targetPosition,
                targetRotation,
                AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                0.0D
        );
        activePlayback.resetProgress(controller.position().distanceTo(targetPosition));
        return motionResult.failureReason()
                .map(this::toPlaybackFailure)
                .orElse(null);
    }

    private boolean willCollide(ServerLevel level, net.minecraft.world.entity.Entity vehicle, Vec3 targetPosition) {
        Vec3 delta = targetPosition.subtract(vehicle.position());
        double distance = delta.length();
        if (distance <= ARRIVAL_DISTANCE) {
            return false;
        }

        double maxSpeed = Math.max(0.02D, 0.35D * AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get());
        Vec3 velocity = delta.normalize().scale(Math.min(distance, maxSpeed));
        return !level.noCollision(vehicle, vehicle.getBoundingBox().move(velocity));
    }

    private PlaybackFailure toPlaybackFailure(FailureReason reason) {
        return switch (reason) {
            case COLLISION_OR_OBSTRUCTION -> PlaybackFailure.COLLISION_OR_OBSTRUCTION;
            case VEHICLE_DESTROYED_OR_MISSING -> PlaybackFailure.VEHICLE_MISSING;
            case VEHICLE_UNLOADED -> PlaybackFailure.VEHICLE_UNLOADED;
            case START_TOO_FAR_FROM_ROUTE -> PlaybackFailure.START_TOO_FAR_FROM_ROUTE;
            case MISSING_AUTOPILOT_CONTROLLER -> PlaybackFailure.MISSING_CONTROLLER;
            case MISSING_STATION -> PlaybackFailure.STATION_MISSING;
            case INVALID_ROUTE_DATA, DIMENSION_MISMATCH -> PlaybackFailure.INVALID_ROUTE;
            case MOVEMENT_FAILURE, NONE -> PlaybackFailure.MOVEMENT_FAILURE;
        };
    }

    private void fail(ServerLevel level, ActivePlayback activePlayback, PlaybackFailure failure) {
        activePlayback.controller().stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> station.failPlayback(failure.failureReason()));
    }

    private void complete(ServerLevel level, ActivePlayback activePlayback) {
        activePlayback.controller().stop(level);
        activePlayback.completed(true);
        stationAt(level, activePlayback.stationPos()).ifPresent(AirshipStationBlockEntity::stopPlayback);
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos stationPos) {
        if (level.getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    private static final class ActivePlayback {
        private final Route route;
        private final BlockPos stationPos;
        private final VehicleController controller;
        private final Vec3 joinOffset;
        private final Optional<RouteRotation> joinStartRotation;
        private int targetIndex;
        private int direction;
        private long segmentDurationTicks;
        private int segmentElapsedTicks;
        private double previousDistance = Double.MAX_VALUE;
        private int stalledTicks;
        private int playbackTicks;
        private int initialJoinSegmentsAdvanced;
        private int endpointSettleTicks;
        private Optional<RouteStop> waitingStop = Optional.empty();
        private int waitTicksRemaining;
        private boolean completed;

        private ActivePlayback(
                Route route,
                BlockPos stationPos,
                VehicleController controller,
                Vec3 joinOffset,
                Optional<RouteRotation> joinStartRotation,
                int targetIndex,
                int direction
        ) {
            this.route = route;
            this.stationPos = stationPos;
            this.controller = controller;
            this.joinOffset = joinOffset;
            this.joinStartRotation = joinStartRotation;
            this.targetIndex = targetIndex;
            this.direction = direction;
            resetSegmentTiming();
        }

        private static ActivePlayback create(Route route, BlockPos stationPos, VehicleController controller) {
            Vec3 vehiclePosition = controller.position();
            Optional<RouteRotation> vehicleRotation = controller.routeRotation();
            Vec3 first = route.points().getFirst().position();
            Vec3 last = route.points().getLast().position();
            if (vehiclePosition.distanceToSqr(first) <= vehiclePosition.distanceToSqr(last)) {
                return new ActivePlayback(route, stationPos, controller, vehiclePosition.subtract(first), vehicleRotation, 1, 1);
            }
            int lastIndex = route.points().size() - 1;
            return new ActivePlayback(
                    route,
                    stationPos,
                    controller,
                    vehiclePosition.subtract(last),
                    vehicleRotation,
                    lastIndex - 1,
                    -1
            );
        }

        private static double nearestEndpointDistance(Route route, VehicleController controller) {
            Vec3 vehiclePosition = controller.position();
            Vec3 first = route.points().getFirst().position();
            Vec3 last = route.points().getLast().position();
            return Math.min(vehiclePosition.distanceTo(first), vehiclePosition.distanceTo(last));
        }

        private Route route() {
            return route;
        }

        private BlockPos stationPos() {
            return stationPos;
        }

        private VehicleController controller() {
            return controller;
        }

        private RoutePoint targetPoint() {
            return route.points().get(targetIndex);
        }

        private Vec3 targetPosition() {
            return pointPosition(targetIndex);
        }

        private int targetIndex() {
            return targetIndex;
        }

        private double targetSpeedBlocksPerTick() {
            return adjacentSegmentSpeedBlocksPerTick();
        }

        private double adjacentSegmentSpeedBlocksPerTick() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return MIN_REPLAY_SPEED;
            }

            RoutePoint previous = route.points().get(previousIndex);
            RoutePoint target = targetPoint();
            long ticks = Math.max(1L, Math.abs(target.tickOffset() - previous.tickOffset()));
            double speed = previous.position().distanceTo(target.position()) / ticks;
            return Math.max(MIN_REPLAY_SPEED, Math.min(MAX_REPLAY_SPEED, speed));
        }

        private void advanceTarget() {
            initialJoinSegmentsAdvanced++;
            endpointSettleTicks = 0;
            if (route.playbackMode() == net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode.ONE_WAY
                    && targetIndex == route.points().size() - 1
                    && direction > 0) {
                return;
            }
            if (targetIndex == route.points().size() - 1) {
                direction = -1;
            } else if (targetIndex == 0) {
                direction = 1;
            }
            targetIndex += direction;
            resetSegmentTiming();
        }

        private boolean beginWaitAtCurrentTarget() {
            Optional<RouteStop> stop = route.stops().stream()
                    .filter(routeStop -> routeStop.pointIndex() == targetIndex)
                    .findFirst();
            if (stop.isEmpty()) {
                return false;
            }

            int runtimeTicks = stop.get().waitCondition().runtimeTicks();
            if (!stop.get().waitCondition().waits() || runtimeTicks <= 0) {
                return false;
            }

            waitingStop = stop;
            waitTicksRemaining = runtimeTicks;
            return true;
        }

        private boolean isComplete() {
            return route.playbackMode() == net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode.ONE_WAY
                    && targetIndex == route.points().size() - 1
                    && direction > 0
                    && waitingStop.isEmpty();
        }

        private boolean completed() {
            return completed;
        }

        private void completed(boolean completed) {
            this.completed = completed;
        }

        private boolean isWaiting() {
            return waitingStop.isPresent();
        }

        private Optional<RouteStop> waitingStop() {
            return waitingStop;
        }

        private int waitTicksRemaining() {
            return waitTicksRemaining;
        }

        private boolean tickWait() {
            if (waitTicksRemaining > 0) {
                waitTicksRemaining--;
            }
            return waitTicksRemaining <= 0;
        }

        private void clearWait() {
            waitingStop = Optional.empty();
            waitTicksRemaining = 0;
        }

        private Vec3 pointPosition(int pointIndex) {
            return route.points().get(pointIndex).position().add(blendedJoinOffset(pointIndex));
        }

        private Vec3 blendedJoinOffset(int pointIndex) {
            double blendFactor = positionJoinBlendFactor(pointIndex);
            if (blendFactor <= 0.0D) {
                return Vec3.ZERO;
            }

            return joinOffset.scale(blendFactor);
        }

        private double positionJoinBlendFactor(int pointIndex) {
            int ordinal = initialJoinOrdinal(pointIndex);
            if (ordinal == Integer.MAX_VALUE) {
                return 0.0D;
            }
            if (ordinal <= 1) {
                return 1.0D;
            }
            if (ordinal > JOIN_BLEND_SEGMENTS) {
                return 0.0D;
            }

            return 1.0D - ((double) (ordinal - 1) / JOIN_BLEND_SEGMENTS);
        }

        private int initialJoinOrdinal(int pointIndex) {
            if (pointIndex == targetIndex) {
                return initialJoinSegmentsAdvanced + 1;
            }
            if (pointIndex == targetIndex - direction) {
                return initialJoinSegmentsAdvanced;
            }
            return Integer.MAX_VALUE;
        }

        private Optional<RouteRotation> targetRotation(Vec3 currentPosition) {
            int previousIndex = targetIndex - direction;
            Optional<RouteRotation> targetRotation = pointRotation(targetIndex);
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return targetRotation;
            }

            Optional<RouteRotation> previousRotation = pointRotation(previousIndex);
            if (previousRotation.isEmpty()) {
                return targetRotation;
            }
            if (targetRotation.isEmpty()) {
                return previousRotation;
            }

            Vec3 segmentStart = pointPosition(previousIndex);
            Vec3 segmentEnd = pointPosition(targetIndex);
            Vec3 segment = segmentEnd.subtract(segmentStart);
            double lengthSquared = segment.lengthSqr();
            if (lengthSquared < 1.0E-6D) {
                return targetRotation;
            }

            double progress = currentPosition.subtract(segmentStart).dot(segment) / lengthSquared;
            progress = Math.max(0.0D, Math.min(1.0D, progress));
            return Optional.of(previousRotation.get().slerp(targetRotation.get(), progress));
        }

        private Optional<RouteRotation> pointRotation(int pointIndex) {
            Optional<RouteRotation> routeRotation = route.points().get(pointIndex).rotation();
            if (routeRotation.isEmpty() || joinStartRotation.isEmpty()) {
                return routeRotation;
            }

            int ordinal = initialJoinOrdinal(pointIndex);
            if (ordinal == Integer.MAX_VALUE) {
                return routeRotation;
            }
            if (ordinal <= 0) {
                return joinStartRotation;
            }
            if (ordinal > JOIN_BLEND_SEGMENTS) {
                return routeRotation;
            }

            double joinProgress = Math.min(1.0D, (double) ordinal / JOIN_BLEND_SEGMENTS);
            return Optional.of(joinStartRotation.get().slerp(routeRotation.get(), joinProgress));
        }

        private boolean isRotationAligned(Optional<RouteRotation> targetRotation, VehicleController controller) {
            if (targetRotation.isEmpty()) {
                return true;
            }
            return controller.routeRotation()
                    .map(currentRotation -> angularDifferenceDegrees(currentRotation, targetRotation.get()) <= ARRIVAL_ROTATION_TOLERANCE_DEGREES)
                    .orElse(true);
        }

        private double arrivalDistance() {
            return isPreciseArrivalPoint(targetIndex) ? ENDPOINT_ARRIVAL_DISTANCE : ARRIVAL_DISTANCE;
        }

        private boolean shouldHoldAtTarget(double distanceToTarget) {
            return isStationarySegment()
                    && distanceToTarget <= arrivalDistance()
                    && segmentElapsedTicks < segmentDurationTicks;
        }

        private boolean shouldSettleEndpoint(double distanceToTarget, boolean rotationAligned) {
            return isPreciseArrivalPoint(targetIndex)
                    && rotationAligned
                    && distanceToTarget <= ENDPOINT_SETTLE_DISTANCE;
        }

        private boolean tickEndpointSettle() {
            endpointSettleTicks++;
            return endpointSettleTicks >= ENDPOINT_SETTLE_TICKS;
        }

        private void resetEndpointSettle() {
            endpointSettleTicks = 0;
        }

        private boolean requiresRotationAlignmentForArrival() {
            return isPreciseArrivalPoint(targetIndex);
        }

        private double angularDifferenceDegrees(RouteRotation current, RouteRotation target) {
            double dot = Math.abs(
                    current.x() * target.x()
                            + current.y() * target.y()
                            + current.z() * target.z()
                            + current.w() * target.w()
            );
            dot = Math.max(-1.0D, Math.min(1.0D, dot));
            return Math.toDegrees(2.0D * Math.acos(dot));
        }

        private boolean isStalled(double currentDistance) {
            playbackTicks++;
            if (playbackTicks <= INITIAL_STALL_GRACE_TICKS) {
                resetProgress(currentDistance);
                return false;
            }

            if (currentDistance < previousDistance - MIN_PROGRESS_PER_TICK) {
                resetProgress(currentDistance);
                return false;
            }

            previousDistance = currentDistance;
            stalledTicks++;
            return stalledTicks >= MAX_STALLED_TICKS;
        }

        private void resetProgress(double currentDistance) {
            previousDistance = currentDistance;
            stalledTicks = 0;
        }

        private void resetSegmentTiming() {
            segmentDurationTicks = computeSegmentDurationTicks();
            segmentElapsedTicks = 0;
        }

        private long computeSegmentDurationTicks() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return 1L;
            }
            return Math.max(1L, Math.abs(route.points().get(targetIndex).tickOffset() - route.points().get(previousIndex).tickOffset()));
        }

        private boolean isStationarySegment() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return false;
            }
            return route.points().get(previousIndex).position().distanceTo(route.points().get(targetIndex).position()) <= STATIONARY_SEGMENT_DISTANCE;
        }

        private boolean isEndpoint(int pointIndex) {
            return pointIndex == 0 || pointIndex == route.points().size() - 1;
        }

        private boolean isPreciseArrivalPoint(int pointIndex) {
            return isEndpoint(pointIndex) || route.stops().stream().anyMatch(stop -> stop.pointIndex() == pointIndex);
        }

        private void tickSegment() {
            if (segmentElapsedTicks < Integer.MAX_VALUE) {
                segmentElapsedTicks++;
            }
        }

        private int playbackTicks() {
            return playbackTicks;
        }

        private double previousDistance() {
            return previousDistance;
        }

        private int stalledTicks() {
            return stalledTicks;
        }

        private int endpointSettleTicks() {
            return endpointSettleTicks;
        }

        private boolean shouldLogProgress() {
            return playbackTicks <= 10 || playbackTicks % 20 == 0;
        }
    }
}
