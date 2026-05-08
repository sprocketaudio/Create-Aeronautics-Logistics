package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncAutomatedShipVisualsPayload;

public final class AutomationVisualServerEvents {
    private AutomationVisualServerEvents() {
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new SyncAutomatedShipVisualsPayload(AutomatedLogisticsServices.PLAYBACK.activeVisualShipIds().stream().toList())
        );
    }
}
