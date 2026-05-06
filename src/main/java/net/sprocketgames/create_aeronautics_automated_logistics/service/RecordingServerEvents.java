package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RecordingServerEvents {
    private RecordingServerEvents() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        AutomatedLogisticsServices.RECORDING.tickAll(event.getServer());
        AutomatedLogisticsServices.PLAYBACK.tickAll(event.getServer());
        AutomatedLogisticsServices.SCHEDULES.tickAll(event.getServer());
    }
}
