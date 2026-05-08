package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;

public record SyncAutomatedShipVisualsPayload(List<UUID> shipIds) implements CustomPacketPayload {
    public static final Type<SyncAutomatedShipVisualsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "sync_automated_ship_visuals")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAutomatedShipVisualsPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncAutomatedShipVisualsPayload::write, SyncAutomatedShipVisualsPayload::read);

    private static SyncAutomatedShipVisualsPayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<UUID> shipIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shipIds.add(buffer.readUUID());
        }
        return new SyncAutomatedShipVisualsPayload(shipIds);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(shipIds.size());
        for (UUID shipId : shipIds) {
            buffer.writeUUID(shipId);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAutomatedShipVisualsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AutomatedShipVisualClientState.replaceActiveShips(payload.shipIds()));
    }
}
