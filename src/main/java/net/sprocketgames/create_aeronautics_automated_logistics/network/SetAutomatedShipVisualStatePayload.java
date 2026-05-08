package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;

public record SetAutomatedShipVisualStatePayload(UUID shipId, boolean active) implements CustomPacketPayload {
    public static final Type<SetAutomatedShipVisualStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_automated_ship_visual_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetAutomatedShipVisualStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetAutomatedShipVisualStatePayload::write, SetAutomatedShipVisualStatePayload::read);

    private static SetAutomatedShipVisualStatePayload read(RegistryFriendlyByteBuf buffer) {
        return new SetAutomatedShipVisualStatePayload(buffer.readUUID(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(shipId);
        buffer.writeBoolean(active);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetAutomatedShipVisualStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AutomatedShipVisualClientState.setShipActive(payload.shipId(), payload.active()));
    }
}
