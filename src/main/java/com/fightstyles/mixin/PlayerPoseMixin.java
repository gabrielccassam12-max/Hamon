package com.fightstyles.mixin;

import com.fightstyles.client.ClientState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arm "casting" poses: while a power is being used the arms reach out like a spell caster. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("RETURN"), require = 0)
    private void fs$pose(LivingEntity entity, float limbAngle, float limbDistance, float age, float headYaw,
                           float headPitch, CallbackInfo ci) {
        float[] s = ClientState.poseFor(entity.getId());
        if (s == null) return;
        PlayerEntityModel<?> m = (PlayerEntityModel<?>) (Object) this;
        int pose = (int) s[0];
        float k = s[1];
        float aim = m.head.pitch; // follow where the player looks
        float half = (float) (Math.PI / 2);
        float wob = (float) Math.sin(age * 0.9f) * 0.04f * k;
        switch (pose) {
            case 1 -> { // one arm pointing forward
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, -half + aim + wob);
                m.rightArm.yaw = lerp(k, m.rightArm.yaw, -0.08f);
                m.rightArm.roll = lerp(k, m.rightArm.roll, 0f);
            }
            case 2 -> { // both arms forward (flying)
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, -half + aim);
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, -half + aim);
                m.rightArm.yaw = lerp(k, m.rightArm.yaw, -0.1f);
                m.leftArm.yaw = lerp(k, m.leftArm.yaw, 0.1f);
            }
            case 3 -> { // both arms raised to the sky
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, -3.0f + wob);
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, -3.0f - wob);
                m.rightArm.roll = lerp(k, m.rightArm.roll, 0.25f);
                m.leftArm.roll = lerp(k, m.leftArm.roll, -0.25f);
            }
            case 4 -> { // arms thrown wide
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, 0f);
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, 0f);
                m.rightArm.roll = lerp(k, m.rightArm.roll, half + wob);
                m.leftArm.roll = lerp(k, m.leftArm.roll, -half - wob);
            }
            case 5 -> { // high kick with the right leg, arms back for balance
                m.rightLeg.pitch = lerp(k, m.rightLeg.pitch, -1.45f);
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, -0.6f);
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, 0.5f);
                m.leftArm.roll = lerp(k, m.leftArm.roll, -0.5f);
            }
            case 6 -> { // hands pressing down into the ground
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, -0.75f + wob);
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, -0.75f - wob);
                m.rightArm.roll = lerp(k, m.rightArm.roll, 0.3f);
                m.leftArm.roll = lerp(k, m.leftArm.roll, -0.3f);
            }
            default -> { }
        }
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftPants.copyTransform(m.leftLeg);
        m.rightPants.copyTransform(m.rightLeg);
    }

    private static float lerp(float k, float a, float b) {
        return a + (b - a) * k;
    }
}
