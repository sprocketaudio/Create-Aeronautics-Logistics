package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import dev.eriksonn.aeronautics.content.blocks.propeller.small.smart_propeller.SmartPropellerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmartPropellerBlockEntity.class)
abstract class SmartPropellerBlockEntityMixin {
    @Inject(method = "onActiveTick", at = @At("HEAD"), cancellable = true)
    private void aal$cancelFakeWindAndPush(CallbackInfo ci) {
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity((BlockEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
