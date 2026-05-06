package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public interface RouteStorageService {
    RouteOperationResult<Route> saveRoute(ServerLevel level, BlockPos stationPos, Route route);

    Optional<Route> loadRoute(ServerLevel level, BlockPos stationPos, RouteId routeId);

    void deleteRoute(ServerLevel level, BlockPos stationPos, RouteId routeId);
}
