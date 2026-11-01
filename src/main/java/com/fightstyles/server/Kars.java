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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** KARS, the Ultimate Being: blades grown from the arm, light blades, divine sandals, the tornado, the ultimate form. */
public final class Kars {
    private Kars() {}

    private static final Random R = Fx.R;

    private static void cut(ServerWorld w, LivingEntity e) {
        Fx.burst(w, "k_spark", Ops.center(e), 14, 0.3, 0.3);
        Fx.burst(w, "k_big", Ops.center(e), 2, 0.1, 0.2);
    }

    // ------------------------------------------------------------------ 1. Arm Blade
    public static boolean armBlade(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look().normalize(), from = c.hand();
        Snd.play(w, from, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        Snd.play(w, from, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.4f);
        Vec3d side = Ops.rightOf(look);
        for (double a = -1.0; a <= 1.0; a += 0.1) {                              // a wide white arc of light
            for (double d = 0.8; d <= 3.4; d += 0.45) {
                Vec3d q = from.add(look.multiply(d * Math.cos(a))).add(side.multiply(d * Math.sin(a)));
                Fx.vel(w, "k_light", q, Vec3d.ZERO);
            }
            Vec3d tip = from.add(look.multiply(3.4 * Math.cos(a))).add(side.multiply(3.4 * Math.sin(a)));
            Fx.vel(w, "k_spark", tip, Vec3d.ZERO);
        }
        int n = 0;
        for (LivingEntity e : Ops.around(w, c.eye().add(look.multiply(2.0)), 2.9, p)) {
            Vec3d to = Ops.center(e).subtract(c.eye());
            if (to.length() > 3.7 || to.normalize().dotProduct(look) < 0.35 || n >= 3) continue;
            Ops.hurt(w, e, p, 8f * c.mult());
            Ops.push(e, to, 0.5, 0.15);
            cut(w, e);
            n++;
        }
        if (n > 0) Ops.shake(w, from, 8, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 2. Flesh Spikes
    public static boolean spikes(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d centre = c.pos();
        Snd.play(w, centre, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        Ops.ring(w, centre.add(0, 0.1, 0), 4.5, 10, Style.KARS);
        Effects.add(t -> {
            if (t > 7) return false;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + t * 0.12;
                for (double d = 0.5; d <= t * 0.65; d += 0.4)
                    Fx.vel(w, "k_light", centre.add(Math.cos(a) * d, 0.2 + d * 0.12, Math.sin(a) * d), Vec3d.ZERO);
                Fx.vel(w, "k_big", centre.add(Math.cos(a) * t * 0.65, 0.3, Math.sin(a) * t * 0.65), Vec3d.ZERO);
            }
            if (t == 3) {
                Ops.shake(w, centre, 12, 3, 6);
                Ops.blast(w, centre.add(0, 0.5, 0), p, 4.6, 8f * m, 1.0, 0.45, e -> cut(w, e));
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Light Blade: a beam that pierces everything
    public static boolean lightBlade(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look().normalize(), from = c.hand();
        Vec3d far = c.eye().add(look.multiply(20));
        BlockHitResult bh = w.raycast(new RaycastContext(c.eye(), far, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        Vec3d end = bh.getType() == HitResult.Type.MISS ? far : bh.getPos();
        Snd.play(w, from, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.8f);
        Snd.play(w, from, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        Vec3d seg = end.subtract(from);
        double len = seg.length();
        Vec3d dir = len < 0.01 ? look : seg.normalize();
        for (double d = 0; d <= len; d += 0.35) {
            Vec3d q = from.add(dir.multiply(d));
            Fx.vel(w, "k_light", q, Vec3d.ZERO);
            if (((int) (d * 3)) % 3 == 0) Fx.vel(w, "k_spark", q.add(Fx.jitter(0.15)), Fx.jitter(0.05));
        }
        Fx.burst(w, "k_big", end, 4, 0.1, 0.2);
        Fx.burst(w, "k_spark", end, 20, 0.35, 0.3);
        int n = 0;
        for (LivingEntity e : Ops.targets(w, new net.minecraft.util.math.Box(from, end).expand(1.4), p)) {
            Vec3d to = Ops.center(e).subtract(from);
            double along = Math.max(0, Math.min(len, to.dotProduct(dir)));
            if (to.subtract(dir.multiply(along)).length() > 1.4 || n >= 6) continue;
            Ops.hurt(w, e, p, 10f * c.mult());
            cut(w, e);
            n++;
        }
        Ops.shake(w, from, 10, 2, 5);
        return true;
    }

    // ------------------------------------------------------------------ 4. Divine Sandals: a dash that slashes
    public static boolean sandals(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d dir = Ops.flat(c.look());
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.4f);
        Fx.burst(w, "k_big", c.pos().add(0, 0.3, 0), 4, 0.1, 0.1);
        Set<Integer> hit = new HashSet<>();
        Effects.add(t -> {
            if (t > 9 || !p.isAlive()) return false;
            if (t <= 6) {
                p.setVelocity(dir.x * 1.7, p.isOnGround() ? 0.08 : p.getVelocity().y, dir.z * 1.7);
                p.velocityModified = true;
            }
            p.fallDistance = 0;
            Vec3d b = p.getPos().add(0, 0.9, 0);
            for (int i = 0; i < 5; i++) Fx.vel(w, "k_light", b.add(Fx.jitter(0.5)), dir.multiply(-0.05));
            Fx.vel(w, "k_spark", b.add(0, -0.7, 0), Fx.jitter(0.05));
            for (LivingEntity e : Ops.around(w, b, 1.9, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 7f * m);
                Ops.push(e, dir, 0.8, 0.2);
                cut(w, e);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Wind Vortex
    public static boolean vortex(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d centre = Ops.aimPoint(w, p, 9.0);
        Snd.play(w, centre, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.6f);
        Ops.ring(w, centre.add(0, 0.1, 0), 5, 14, Style.KARS);
        Effects.add(t -> {
            if (t > 26 || !p.isAlive()) return false;
            for (int i = 0; i < 18; i++) {
                double h = R.nextDouble() * 5.0, a = h * 2.2 + t * 0.6 + i;
                double r = 0.6 + h * 0.45;
                Fx.vel(w, "k_light", centre.add(Math.cos(a) * r, h, Math.sin(a) * r), new Vec3d(-Math.sin(a) * 0.12, 0.05, Math.cos(a) * 0.12));
            }
            if (t % 3 == 0) Fx.vel(w, "k_big", centre.add(Fx.jitter(1.5)).add(0, R.nextDouble() * 4, 0), Vec3d.ZERO);
            for (LivingEntity e : Ops.around(w, centre.add(0, 1.5, 0), 5.0, p)) {
                Vec3d in = centre.add(0, 1.2, 0).subtract(Ops.center(e));
                e.addVelocity(in.x * 0.05, 0.07, in.z * 0.05);
                e.velocityModified = true;
                if (t % 5 == 0) {
                    Ops.hurt(w, e, p, 2.5f * m);
                    cut(w, e);
                }
            }
            if (t % 8 == 0) Ops.shake(w, centre, 12, 1, 5);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Ultimate Form
    public static boolean ultimate(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 1.8f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.7f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 400, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 0));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
        Ops.ring(w, c.pos().add(0, 0.1, 0), 7, 18, Style.KARS);
        Ops.shake(w, c.pos(), 20, 4, 16);
        Effects.add(t -> {
            if (t > 24 || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            Fx.circle(w, "k_light", b.add(0, t * 0.1, 0), 1.0 + t * 0.12, 26, t * 0.4, 0.05);
            if (t % 3 == 0) Fx.sphere(w, "k_big", b.add(0, 1, 0), 1.4, 2, Vec3d.ZERO);
            if (t == 6) Ops.blast(w, b.add(0, 1, 0), p, 6.0, 7f * m, 1.3, 0.5, e -> cut(w, e));
            return true;
        });
        return true;
    }
}
