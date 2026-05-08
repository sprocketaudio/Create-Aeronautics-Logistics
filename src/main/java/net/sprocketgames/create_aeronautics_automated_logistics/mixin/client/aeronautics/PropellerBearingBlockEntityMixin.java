package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PropellerBearingBlockEntity.class)
abstract class PropellerBearingBlockEntityMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void aal$reportActiveWhenAutomated(CallbackInfoReturnable<Boolean> cir) {
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity((BlockEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getInterpolatedAngle", at = @At("HEAD"), cancellable = true)
    private void aal$useClientVisualAngle(float partialTicks, CallbackInfoReturnable<Float> cir) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity) && blockEntity.getLevel() != null) {
            double time = blockEntity.getLevel().getGameTime() + partialTicks;
            cir.setReturnValue((float) ((time * 24.0D) % 360.0D));
        }
    }

    @Inject(method = "activeTick", at = @At("HEAD"), cancellable = true)
    private void aal$cancelFakeWindAndPush(CallbackInfo ci) {
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity((BlockEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
