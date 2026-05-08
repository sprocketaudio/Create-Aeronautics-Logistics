package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AutomatedShipVisualClientState {
    private static final Set<UUID> ACTIVE_SHIPS = ConcurrentHashMap.newKeySet();

    private AutomatedShipVisualClientState() {
    }

    public static void setShipActive(UUID shipId, boolean active) {
        if (active) {
            ACTIVE_SHIPS.add(shipId);
        } else {
            ACTIVE_SHIPS.remove(shipId);
        }
    }

    public static void replaceActiveShips(Collection<UUID> shipIds) {
        ACTIVE_SHIPS.clear();
        ACTIVE_SHIPS.addAll(shipIds);
    }

    public static void clearIfWorldMissing() {
        if (Minecraft.getInstance().level == null) {
            ACTIVE_SHIPS.clear();
        }
    }

    public static boolean isAutomatedShipBlockEntity(BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide) {
            return false;
        }
        SubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        return subLevel != null && ACTIVE_SHIPS.contains(subLevel.getUniqueId());
    }
}
