package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public record RecordingSession(
        RouteId routeId,
        UUID playerId,
        BlockPos stationPos,
        VehicleControllerRef controllerRef,
        long startedAtGameTime
) {
    public RecordingSession {
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(controllerRef, "controllerRef");

        if (startedAtGameTime < 0L) {
            throw new IllegalArgumentException("startedAtGameTime must be non-negative");
        }
    }

    public static RecordingSession create(ServerPlayer player, BlockPos stationPos, VehicleControllerRef controllerRef) {
        return new RecordingSession(
                RouteId.create(),
                player.getUUID(),
                stationPos,
                controllerRef,
                player.serverLevel().getGameTime()
        );
    }
}
