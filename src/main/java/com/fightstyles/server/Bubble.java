package com.fightstyles.server;

import com.fightstyles.core.Style;
import com.fightstyles.particle.Fx;
import com.fightstyles.util.Effects;
import com.fightstyles.util.Snd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** BUBBLE HAMON (Caesar): the Hamon carried by soap bubbles. Iridescent translucent cubes. */
public final class Bubble {
    private Bubble() {}

    private static final Random R = Fx.R;

    private static void pop(ServerWorld w, Vec3d at, int n) {
        Fx.burst(w, "b_spark", at, n, 0.25, 0.2);
        Fx.burst(w, "b_big", at, 2, 0.08, 0.1);
        Snd.play(w, at, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1.0f, 1.0f + R.nextFloat() * 0.5f);
    }

    private static void soap(ServerWorld w, ServerPlayerEntity p, LivingEntity e, float dmg) {
        Hamon.shock(w, p, e, dmg);
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
    }

    // ------------------------------------------------------------------ 1. Bubble Shot
    public static boolean shot(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_GENERIC_SPLASH, 0.8f, 1.6f);
        Ops.projectile(w, p, c.hand(), c.look(), 1.1, 26, 0.6, true,
                (pos, dir, t) -> {
                    Fx.vel(w, "b_irid", pos.add(Fx.jitter(0.1)), Vec3d.ZERO);
                    if (t % 2 == 0) Fx.vel(w, "b_big", pos, Vec3d.ZERO);
                },
                (pos, target) -> {
                    pop(w, pos, 16);
                    if (target != null) soap(w, p, target, 5.5f * m);
                });
        return true;
    }

    // ------------------------------------------------------------------ 2. Bubble Barrier
    public static boolean barrier(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 140, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 140, 0));
        Snd.play(w, c.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 1.8f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 2.5, 8, Style.BUBBLE);
        Effects.add(t -> {
            if (t > 140 || !p.isAlive()) {
                if (p.isAlive()) pop(w, p.getPos().add(0, 1, 0), 24);
                return false;
            }
            if (t % 3 == 0) Fx.sphere(w, "b_irid", p.getPos().add(0, 1, 0), 1.35, 6, Vec3d.ZERO);
            if (t % 12 == 0) Fx.sphere(w, "b_big", p.getPos().add(0, 1, 0), 1.35, 1, Vec3d.ZERO);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Bubble Cutter: five bubbles in a fan
    public static boolean cutter(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        for (int i = -2; i <= 2; i++) {
            Vec3d dir = c.look().rotateY((float) Math.toRadians(i * 13.0));
            Ops.projectile(w, p, c.hand(), dir, 1.4, 16, 0.5, true,
                    (pos, d, t) -> Fx.vel(w, "b_spark", pos.add(Fx.jitter(0.08)), Vec3d.ZERO),
                    (pos, target) -> {
                        pop(w, pos, 8);
                        if (target != null) soap(w, p, target, 4.5f * m);
                    });
        }
        return true;
    }

    // ------------------------------------------------------------------ 4. Bubble Trap: lifts and holds the target
    public static boolean trap(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity t = Ops.aimEntity(w, p, 9.0, 0.85);
        if (t == null) return false;
        float m = c.mult();
        Snd.play(w, Ops.center(t), SoundEvents.ENTITY_GENERIC_SPLASH, 1.0f, 1.2f);
        t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 0));
        t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3));
        t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
        Effects.add(n -> {
            if (n > 45 || !t.isAlive()) {
                if (t.isAlive()) {
                    pop(w, Ops.center(t), 26);
                    Hamon.shock(w, p, t, 6f * m);
                }
                return false;
            }
            if (n % 2 == 0) Fx.sphere(w, "b_irid", Ops.center(t), 1.1, 6, Vec3d.ZERO);
            if (n % 10 == 0) Fx.sphere(w, "b_big", Ops.center(t), 1.1, 1, Vec3d.ZERO);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Bubble Bomb
    public static boolean bomb(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_GENERIC_SPLASH, 1.2f, 0.8f);
        Ops.projectile(w, p, c.hand(), c.look(), 0.9, 34, 0.9, true,
                (pos, dir, t) -> {
                    Fx.sphere(w, "b_big", pos, 0.5, 2, Vec3d.ZERO);
                    Fx.vel(w, "b_irid", pos.add(Fx.jitter(0.2)), Vec3d.ZERO);
                },
                (pos, target) -> {
                    Snd.play(w, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.6f);
                    Fx.burst(w, "b_big", pos, 8, 0.3, 0.2);
                    Fx.burst(w, "b_spark", pos, 60, 0.5, 0.3);
                    Fx.sphere(w, "b_irid", pos, 3.5, 70, Vec3d.ZERO);
                    Ops.ring(w, pos.add(0, 0.1, 0), 4.5, 12, Style.BUBBLE);
                    Ops.shake(w, pos, 14, 3, 8);
                    Ops.blast(w, pos, p, 4.5, 11f * m, 1.1, 0.5, e -> {
                        Hamon.mark(w, e);
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                    });
                });
        return true;
    }

    // ------------------------------------------------------------------ 6. Bubble Storm
    public static boolean storm(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d centre = Ops.aimPoint(w, p, 9.0);
        Snd.play(w, centre, SoundEvents.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);
        Ops.ring(w, centre.add(0, 0.1, 0), 6, 16, Style.BUBBLE);
        Effects.add(t -> {
            if (t > 36 || !p.isAlive()) return false;
            for (int i = 0; i < 3; i++) {
                double a = R.nextDouble() * Math.PI * 2, r = R.nextDouble() * 5.5;
                Vec3d at = centre.add(Math.cos(a) * r, 0.6 + R.nextDouble() * 2.5, Math.sin(a) * r);
                Fx.sphere(w, "b_big", at, 0.45, 2, new Vec3d(0, 0.03, 0));
                Fx.burst(w, "b_irid", at, 4, 0.1, 0.1);
            }
            if (t % 6 == 0) {
                double a = R.nextDouble() * Math.PI * 2, r = R.nextDouble() * 4.5;
                Vec3d at = centre.add(Math.cos(a) * r, 0.8, Math.sin(a) * r);
                pop(w, at, 14);
                Ops.blast(w, at, p, 2.4, 5f * m, 0.6, 0.3, e -> Hamon.mark(w, e));
                Ops.shake(w, at, 10, 1, 3);
            }
            return true;
        });
        return true;
    }
}
