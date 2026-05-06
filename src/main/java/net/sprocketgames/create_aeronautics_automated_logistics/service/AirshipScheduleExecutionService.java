package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class AirshipScheduleExecutionService {
    private final Map<BlockPos, ActiveSchedule> activeSchedules = new HashMap<>();

    public PlaybackOperationResult<RouteId> start(
            ServerPlayer player,
            AirshipStationBlockEntity station,
            BlockPos stationPos,
            AirshipSchedule schedule
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(schedule, "schedule");

        if (schedule.entries().isEmpty()) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        if (activeSchedules.containsKey(stationPos)) {
            return PlaybackOperationResult.failure(PlaybackFailure.ALREADY_RUNNING);
        }
        Optional<UUID> transponderId = station.selectedTransponderId();
        if (transponderId.isEmpty()) {
            station.setFailure(FailureReason.MISSING_AUTOPILOT_CONTROLLER);
            return PlaybackOperationResult.failure(PlaybackFailure.MISSING_CONTROLLER);
        }
        Optional<ShipTransponderSnapshot> ship = ShipTransponderRegistry.snapshot(transponderId.get())
                .filter(snapshot -> snapshot.dimension().equals(player.serverLevel().dimension()));
        if (ship.isEmpty() || ship.get().controllerRef().isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            return PlaybackOperationResult.failure(PlaybackFailure.VEHICLE_MISSING);
        }

        ActiveSchedule active = new ActiveSchedule(
                stationPos,
                player.serverLevel().dimension(),
                schedule,
                transponderId.get(),
                station.stationId(),
                station.stationName(),
                0,
                Optional.empty()
        );
        PlaybackOperationResult<RouteId> result = startEntry(player.serverLevel(), station, active, ship.get());
        result.value().ifPresent(routeId -> activeSchedules.put(stationPos, active.withActiveRoute(routeId)));
        return result;
    }

    public void stop(ServerLevel level, BlockPos stationPos) {
        ActiveSchedule active = activeSchedules.remove(stationPos);
        if (active == null) {
            return;
        }
        active.activeRouteId().ifPresent(routeId -> AutomatedLogisticsServices.PLAYBACK.stopPlayback(level, routeId, FailureReason.NONE));
        stationAt(level, stationPos).ifPresent(AirshipStationBlockEntity::stopPlayback);
    }

    public boolean isRunning(BlockPos stationPos) {
        return activeSchedules.containsKey(stationPos);
    }

    public void tickAll(MinecraftServer server) {
        var iterator = activeSchedules.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ActiveSchedule> entry = iterator.next();
            ActiveSchedule active = entry.getValue();
            ServerLevel level = server.getLevel(active.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            Optional<AirshipStationBlockEntity> station = stationAt(level, active.stationPos());
            if (station.isEmpty()) {
                iterator.remove();
                continue;
            }
            if (station.get().status() == RouteStatus.FAILED
                    || station.get().status() == RouteStatus.BLOCKED
                    || station.get().status() == RouteStatus.MISSING_VEHICLE
                    || station.get().status() == RouteStatus.INVALID_ROUTE) {
                iterator.remove();
                continue;
            }
            if (active.activeRouteId().isPresent() && AutomatedLogisticsServices.PLAYBACK.isRunning(active.activeRouteId().get())) {
                continue;
            }

            ActiveSchedule advanced = active.advance();
            if (advanced.isFinished()) {
                if (!advanced.schedule().loop()) {
                    station.get().stopPlayback();
                    iterator.remove();
                    continue;
                }
                advanced = advanced.restart();
            }

            Optional<ShipTransponderSnapshot> ship = ShipTransponderRegistry.snapshot(advanced.transponderId())
                    .filter(snapshot -> snapshot.dimension().equals(level.dimension()));
            if (ship.isEmpty() || ship.get().controllerRef().isEmpty()) {
                station.get().failPlayback(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
                iterator.remove();
                continue;
            }

            PlaybackOperationResult<RouteId> result = startEntry(level, station.get(), advanced, ship.get());
            if (result.value().isPresent()) {
                entry.setValue(advanced.withActiveRoute(result.value().get()));
            } else {
                result.failure().ifPresent(failure -> station.get().failPlayback(failure.failureReason()));
                iterator.remove();
            }
        }
    }

    private PlaybackOperationResult<RouteId> startEntry(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActiveSchedule active,
            ShipTransponderSnapshot ship
    ) {
        AirshipScheduleEntry entry = active.currentEntry();
        if (entry.targetStationId().isEmpty()) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }

        Optional<RouteSegment> segment = entry.pinnedSegmentId()
                .flatMap(segmentId -> station.routeSegments().stream()
                        .filter(candidate -> candidate.id().equals(segmentId))
                        .findFirst())
                .filter(candidate -> candidate.startStationId().equals(active.currentStationId()))
                .filter(candidate -> candidate.endStationId().equals(entry.targetStationId().get()))
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(active.transponderId()))
                .or(() -> RouteSegmentResolver.newestFor(
                        station,
                        entry.targetStationId().get(),
                        level.dimension(),
                        active.transponderId()
                ));

        if (segment.isEmpty()) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }

        VehicleControllerRef controllerRef = ship.controllerRef().orElse(segment.get().controllerRef());
        Route route = routeFor(active, entry, segment.get(), controllerRef);
        PlaybackOperationResult<RouteId> result = AutomatedLogisticsServices.PLAYBACK.startPlayback(level, active.stationPos(), route);
        result.failure().ifPresent(failure -> station.failPlayback(failure.failureReason()));
        return result;
    }

    private Route routeFor(
            ActiveSchedule active,
            AirshipScheduleEntry entry,
            RouteSegment segment,
            VehicleControllerRef controllerRef
    ) {
        List<RouteStop> stops = entry.waitCondition().waits()
                ? List.of(RouteStop.create(entry.displayStationName(), segment.points().size() - 1, entry.waitCondition()))
                : List.of();
        return new Route(
                RouteId.create(),
                active.currentStationName() + " -> " + entry.displayStationName(),
                segment.dimension(),
                segment.points(),
                controllerRef,
                PlaybackMode.ONE_WAY,
                RouteStatus.RECORDED,
                stops,
                segment.ownerId()
        );
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos stationPos) {
        if (level.getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    private record ActiveSchedule(
            BlockPos stationPos,
            net.minecraft.resources.ResourceKey<Level> dimension,
            AirshipSchedule schedule,
            UUID transponderId,
            UUID currentStationId,
            String currentStationName,
            int entryIndex,
            Optional<RouteId> activeRouteId
    ) {
        private AirshipScheduleEntry currentEntry() {
            return schedule.entries().get(entryIndex);
        }

        private ActiveSchedule withActiveRoute(RouteId routeId) {
            return new ActiveSchedule(
                    stationPos,
                    dimension,
                    schedule,
                    transponderId,
                    currentStationId,
                    currentStationName,
                    entryIndex,
                    Optional.of(routeId)
            );
        }

        private ActiveSchedule advance() {
            AirshipScheduleEntry completed = currentEntry();
            return new ActiveSchedule(
                    stationPos,
                    dimension,
                    schedule,
                    transponderId,
                    completed.targetStationId().orElse(currentStationId),
                    completed.displayStationName(),
                    entryIndex + 1,
                    Optional.empty()
            );
        }

        private ActiveSchedule restart() {
            return new ActiveSchedule(
                    stationPos,
                    dimension,
                    schedule,
                    transponderId,
                    currentStationId,
                    currentStationName,
                    0,
                    Optional.empty()
            );
        }

        private boolean isFinished() {
            return entryIndex >= schedule.entries().size();
        }
    }
}
