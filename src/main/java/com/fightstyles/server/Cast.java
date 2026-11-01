package com.fightstyles.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Everything a technique needs: where, who, how strong (mult), how long the key was held (charge 0..1). */
public record Cast(ServerWorld w, ServerPlayerEntity p, float mult, float charge, boolean boost) {
    public Vec3d eye() {
        return p.getEyePos();
    }

    public Vec3d look() {
        return p.getRotationVec(1f);
    }

    public Vec3d pos() {
        return p.getPos();
    }

    /** Where a hand is: slightly in front, to the right, below the eye. */
    public Vec3d hand() {
        Vec3d look = look(), right = Ops.rightOf(look);
        return eye().add(look.multiply(0.8)).add(right.multiply(0.35)).add(0, -0.35, 0);
    }
}
