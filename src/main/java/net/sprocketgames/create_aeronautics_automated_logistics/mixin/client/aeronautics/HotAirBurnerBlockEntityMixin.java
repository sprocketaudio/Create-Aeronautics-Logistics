package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HotAirBurnerBlockEntity.class)
abstract class HotAirBurnerBlockEntityMixin {
    @Shadow protected boolean powered;
    @Shadow protected int signalStrength;

    @Unique private boolean aal$forcedVisualState;
    @Unique private boolean aal$savedPowered;
    @Unique private int aal$savedSignalStrength;

    @Inject(method = "tick", at = @At("HEAD"))
    private void aal$applyAutomatedVisualState(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide) {
            return;
        }

        boolean automated = AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity);
        if (automated) {
            if (!aal$forcedVisualState) {
                aal$savedPowered = powered;
                aal$savedSignalStrength = signalStrength;
                aal$forcedVisualState = true;
            }
            powered = true;
            signalStrength = 15;
            return;
        }

        if (aal$forcedVisualState) {
            powered = aal$savedPowered;
            signalStrength = aal$savedSignalStrength;
            aal$forcedVisualState = false;
        }
    }
}
