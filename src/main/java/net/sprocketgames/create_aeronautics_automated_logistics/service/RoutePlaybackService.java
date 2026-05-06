package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public interface RoutePlaybackService {
    PlaybackOperationResult<RouteId> startPlayback(ServerLevel level, BlockPos stationPos, Route route);

    void stopPlayback(ServerLevel level, RouteId routeId, FailureReason reason);

    void tickPlayback(ServerLevel level);
}
