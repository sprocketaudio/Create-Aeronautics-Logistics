package net.sprocketgames.create_aeronautics_automated_logistics.service;

public final class AutomatedLogisticsServices {
    public static final RiddenEntityRouteRecordingService RECORDING = new RiddenEntityRouteRecordingService();
    public static final RiddenEntityRoutePlaybackService PLAYBACK = new RiddenEntityRoutePlaybackService();
    public static final AirshipScheduleExecutionService SCHEDULES = new AirshipScheduleExecutionService();

    private AutomatedLogisticsServices() {
    }
}
