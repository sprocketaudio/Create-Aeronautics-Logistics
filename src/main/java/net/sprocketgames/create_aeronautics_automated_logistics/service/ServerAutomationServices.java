package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Objects;

public record ServerAutomationServices(
        RouteRecordingService recording,
        RouteStorageService storage,
        RoutePlaybackService playback,
        RouteStatusService status
) {
    public ServerAutomationServices {
        Objects.requireNonNull(recording, "recording");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(playback, "playback");
        Objects.requireNonNull(status, "status");
    }
}
