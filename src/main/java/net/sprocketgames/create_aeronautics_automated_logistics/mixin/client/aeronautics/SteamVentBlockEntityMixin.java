package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SteamVentBlockEntity.class)
abstract class SteamVentBlockEntityMixin {
    @Shadow public int signalStrength;
    @Shadow public int rawSignalStrength;
    @Shadow private double efficiency;

    @Unique private boolean aal$forcedVisualState;
    @Unique private int aal$savedSignalStrength;
    @Unique private int aal$savedRawSignalStrength;
    @Unique private double aal$savedEfficiency;

    @Inject(method = "tick", at = @At("HEAD"))
    private void aal$applyAutomatedVisualState(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide) {
            return;
        }

        boolean automated = AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity);
        if (automated) {
            if (!aal$forcedVisualState) {
                aal$savedSignalStrength = signalStrength;
                aal$savedRawSignalStrength = rawSignalStrength;
                aal$savedEfficiency = efficiency;
                aal$forcedVisualState = true;
            }
            signalStrength = 15;
            rawSignalStrength = 15;
            efficiency = 1.0D;
            return;
        }

        if (aal$forcedVisualState) {
            signalStrength = aal$savedSignalStrength;
            rawSignalStrength = aal$savedRawSignalStrength;
            efficiency = aal$savedEfficiency;
            aal$forcedVisualState = false;
        }
    }
}
