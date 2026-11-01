package com.fightstyles.mixin;

import com.fightstyles.client.ClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** First-person feel: the field of view pulses when a technique fires and narrows while one is charging. */
@Mixin(GameRenderer.class)
public abstract class CameraFxMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void fs$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;
        double d = ClientState.fovDelta(tickDelta);
        if (d != 0.0) cir.setReturnValue(Math.max(20.0, Math.min(140.0, cir.getReturnValue() + d)));
    }
}
