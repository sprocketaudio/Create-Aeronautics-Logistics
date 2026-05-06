package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;

public class ShipTransponderMenu extends AbstractContainerMenu {
    private final BlockPos transponderPos;

    public ShipTransponderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos) {
        super(ModMenus.SHIP_TRANSPONDER.get(), containerId);
        this.transponderPos = transponderPos;
    }

    public BlockPos transponderPos() {
        return transponderPos;
    }

    public String shipName(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.shipName();
        }
        return "";
    }

    public Component shipIdText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return Component.literal(IdentityNames.shortId(transponder.transponderId()));
        }
        return Component.literal("-");
    }

    public Component runtimeShipText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.runtimeShipId()
                    .map(id -> Component.literal(IdentityNames.shortId(id)))
                    .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.unavailable"));
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.missing");
    }

    public Component lastKnownPositionText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            Optional<Vec3> position = transponder.lastKnownPosition();
            if (position.isPresent()) {
                Vec3 pos = position.get();
                return Component.literal((int) pos.x + ", " + (int) pos.y + ", " + (int) pos.z);
            }
        }
        return Component.literal("-");
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
                transponderPos.getX() + 0.5D,
                transponderPos.getY() + 0.5D,
                transponderPos.getZ() + 0.5D
        ) <= 64.0D;
    }
}
