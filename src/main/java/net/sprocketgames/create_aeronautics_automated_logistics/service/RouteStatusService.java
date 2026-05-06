package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRuntimeState;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;

public interface RouteStatusService {
    void setStatus(ServerLevel level, BlockPos stationPos, RouteStatus status, Optional<FailureReason> failureReason);

    RouteRuntimeState getStatus(ServerLevel level, BlockPos stationPos);
}
