package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                UpdateIdentityNamePayload.TYPE,
                UpdateIdentityNamePayload.STREAM_CODEC,
                UpdateIdentityNamePayload::handle
        );
        registrar.playToServer(
                UpdateAirshipScheduleTitlePayload.TYPE,
                UpdateAirshipScheduleTitlePayload.STREAM_CODEC,
                UpdateAirshipScheduleTitlePayload::handle
        );
        registrar.playToServer(
                UpdateAirshipSchedulePayload.TYPE,
                UpdateAirshipSchedulePayload.STREAM_CODEC,
                UpdateAirshipSchedulePayload::handle
        );
        registrar.playToServer(
                SelectAirshipScheduleStationPayload.TYPE,
                SelectAirshipScheduleStationPayload.STREAM_CODEC,
                SelectAirshipScheduleStationPayload::handle
        );
    }
}
