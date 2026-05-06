package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class ShipTransponderBlockEntity extends BlockEntity implements MenuProvider {
    private static final String DATA_VERSION = "dataVersion";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SHIP_NAME = "shipName";
    private static final String RUNTIME_SHIP_ID = "runtimeShipId";
    private static final String LAST_X = "lastKnownX";
    private static final String LAST_Y = "lastKnownY";
    private static final String LAST_Z = "lastKnownZ";
    private static final String LAST_SEEN = "lastSeenGameTime";
    private static final int CURRENT_DATA_VERSION = 1;
    private static final int REFRESH_INTERVAL_TICKS = 40;

    private UUID transponderId = UUID.randomUUID();
    private String shipName = "";
    private Optional<UUID> runtimeShipId = Optional.empty();
    private Optional<Vec3> lastKnownPosition = Optional.empty();
    private long lastSeenGameTime = -1L;

    public ShipTransponderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SHIP_TRANSPONDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShipTransponderBlockEntity transponder) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % REFRESH_INTERVAL_TICKS == 0L) {
            transponder.refreshRuntimeShip(serverLevel);
        }
    }

    public UUID transponderId() {
        return transponderId;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ShipTransponderMenu(containerId, playerInventory, worldPosition);
    }

    public String shipName() {
        if (shipName == null || shipName.isBlank()) {
            return IdentityNames.defaultShipName(transponderId);
        }
        return shipName;
    }

    public Optional<UUID> runtimeShipId() {
        return runtimeShipId;
    }

    public Optional<Vec3> lastKnownPosition() {
        return lastKnownPosition;
    }

    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    public void setShipName(String shipName) {
        String sanitized = IdentityNames.sanitize(shipName);
        this.shipName = sanitized.isBlank() ? IdentityNames.defaultShipName(transponderId) : sanitized;
        setChanged();
    }

    public Optional<VehicleControllerRef> controllerRef(ServerLevel level) {
        refreshRuntimeShip(level);
        return runtimeShipId.map(shipId -> new VehicleControllerRef(
                SableSubLevelVehicleController.TYPE,
                level.dimension(),
                Optional.of(shipId),
                Optional.of(worldPosition)
        ));
    }

    public void refreshRuntimeShip(ServerLevel level) {
        Optional<SableSubLevelVehicleController> controller = SableSubLevelVehicleController.resolveControllerBlock(
                level,
                worldPosition,
                ModBlocks.SHIP_TRANSPONDER.get()
        );
        Optional<UUID> previousId = runtimeShipId;
        Optional<Vec3> previousPosition = lastKnownPosition;
        long previousSeen = lastSeenGameTime;

        if (controller.isPresent()) {
            runtimeShipId = controller.get().ref().vehicleId();
            lastKnownPosition = Optional.of(controller.get().position());
            lastSeenGameTime = level.getGameTime();
        } else {
            runtimeShipId = Optional.empty();
        }

        if (!previousId.equals(runtimeShipId)
                || !previousPosition.equals(lastKnownPosition)
                || previousSeen != lastSeenGameTime) {
            setChanged();
        }
        ShipTransponderRegistry.register(snapshot(level));
    }

    private ShipTransponderSnapshot snapshot(ServerLevel level) {
        Optional<VehicleControllerRef> controllerRef = runtimeShipId.map(shipId -> new VehicleControllerRef(
                SableSubLevelVehicleController.TYPE,
                level.dimension(),
                Optional.of(shipId),
                Optional.of(worldPosition)
        ));
        return new ShipTransponderSnapshot(
                transponderId,
                shipName(),
                level.dimension(),
                worldPosition,
                runtimeShipId,
                controllerRef,
                lastKnownPosition,
                lastSeenGameTime
        );
    }

    public Component statusMessage() {
        Component runtime = runtimeShipId
                .map(id -> Component.literal(id.toString()))
                .orElseGet(() -> Component.translatable("message.create_aeronautics_automated_logistics.ship_transponder.unavailable"));
        return Component.translatable(
                "message.create_aeronautics_automated_logistics.ship_transponder.status",
                shipName(),
                IdentityNames.shortId(transponderId),
                runtime
        );
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(tag);
    }

    private void writeData(CompoundTag tag) {
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putUUID(TRANSPONDER_ID, transponderId);
        tag.putString(SHIP_NAME, shipName());
        runtimeShipId.ifPresent(id -> tag.putUUID(RUNTIME_SHIP_ID, id));
        lastKnownPosition.ifPresent(pos -> {
            tag.putDouble(LAST_X, pos.x);
            tag.putDouble(LAST_Y, pos.y);
            tag.putDouble(LAST_Z, pos.z);
        });
        tag.putLong(LAST_SEEN, lastSeenGameTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TRANSPONDER_ID)) {
            transponderId = tag.getUUID(TRANSPONDER_ID);
        }
        shipName = tag.contains(SHIP_NAME, Tag.TAG_STRING)
                ? IdentityNames.sanitize(tag.getString(SHIP_NAME))
                : "";
        runtimeShipId = tag.hasUUID(RUNTIME_SHIP_ID)
                ? Optional.of(tag.getUUID(RUNTIME_SHIP_ID))
                : Optional.empty();
        lastKnownPosition = tag.contains(LAST_X, Tag.TAG_ANY_NUMERIC)
                && tag.contains(LAST_Y, Tag.TAG_ANY_NUMERIC)
                && tag.contains(LAST_Z, Tag.TAG_ANY_NUMERIC)
                ? Optional.of(new Vec3(tag.getDouble(LAST_X), tag.getDouble(LAST_Y), tag.getDouble(LAST_Z)))
                : Optional.empty();
        lastSeenGameTime = tag.contains(LAST_SEEN, Tag.TAG_ANY_NUMERIC) ? tag.getLong(LAST_SEEN) : -1L;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeData(tag);
        return tag;
    }
}
