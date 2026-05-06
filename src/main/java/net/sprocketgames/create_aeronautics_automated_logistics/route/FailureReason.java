package net.sprocketgames.create_aeronautics_automated_logistics.route;

public enum FailureReason {
    NONE,
    COLLISION_OR_OBSTRUCTION,
    VEHICLE_DESTROYED_OR_MISSING,
    VEHICLE_UNLOADED,
    START_TOO_FAR_FROM_ROUTE,
    MISSING_AUTOPILOT_CONTROLLER,
    MISSING_STATION,
    INVALID_ROUTE_DATA,
    DIMENSION_MISMATCH,
    MOVEMENT_FAILURE
}
