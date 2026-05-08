package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BasePropellerBlockEntity.class)
abstract class BasePropellerBlockEntityMixin {
    @Shadow public float rotationSpeed;
    @Shadow public abstract float getPreviousAngle();
    @Shadow public abstract void setPreviousAngle(float previousAngle);
    @Shadow public abstract float getAngle();
    @Shadow public abstract void setAngle(float angle);

    @Inject(method = "tick", at = @At("TAIL"))
    private void aal$spinForAutomatedPlayback(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (!AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity)) {
            return;
        }

        float visualSpeed = 24.0F;
        float currentAngle = getAngle();
        setPreviousAngle(currentAngle);
        rotationSpeed = visualSpeed;
        setAngle(currentAngle + visualSpeed);
    }

    @Inject(method = "onActiveTick", at = @At("HEAD"), cancellable = true)
    private void aal$cancelFakeWindAndPush(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity)) {
            ci.cancel();
        }
    }
}
