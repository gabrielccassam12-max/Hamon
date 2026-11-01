package com.fightstyles.server;

import com.fightstyles.core.Style;
import com.fightstyles.particle.Fx;
import com.fightstyles.util.Effects;
import com.fightstyles.util.Scheduler;
import com.fightstyles.util.Snd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** SPIN: golden-ratio rotation and spinning steel balls. Emerald and silver cubes in spirals. */
public final class Spin {
    private Spin() {}

    private static final Random R = Fx.R;

    /** A spiral of cubes around a travelling point. */
    private static void spiral(ServerWorld w, Vec3d pos, Vec3d dir, int t, double radius) {
        Vec3d d = dir.normalize();
        Vec3d a = Ops.rightOf(d), b = a.crossProduct(d).normalize();
        for (int k = 0; k < 2; k++) {
            double ang = t * 1.1 + k * Math.PI;
            Vec3d q = pos.add(a.multiply(Math.cos(ang) * radius)).add(b.multiply(Math.sin(ang) * radius));
            Fx.vel(w, "sp_swirl", q, Vec3d.ZERO);
        }
    }

    private static void ballFx(ServerWorld w, Vec3d pos, Vec3d dir, int t) {
        Fx.vel(w, "st_steel", pos, Vec3d.ZERO);
        Fx.vel(w, "sp_big", pos, Vec3d.ZERO);
        spiral(w, pos, dir, t, 0.35);
        if (t % 2 == 0) Fx.vel(w, "sp_spark", pos.add(Fx.jitter(0.1)), dir.multiply(-0.1));
    }

    private static void hit(ServerWorld w, ServerPlayerEntity p, LivingEntity e, float dmg) {
        Ops.hurt(w, e, p, dmg);
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1));
        Fx.burst(w, "st_steel", Ops.center(e), 8, 0.3, 0.3);
        Fx.burst(w, "sp_spark", Ops.center(e), 8, 0.25, 0.3);
    }

    /** After a hit the ball bounces to up to two more enemies close by. */
    private static void ricochet(ServerWorld w, ServerPlayerEntity p, Vec3d from, Set<Integer> done, float dmg, int bounces) {
        Vec3d cur = from;
        for (int i = 0; i < bounces; i++) {
            LivingEntity next = null;
            double best = 64;
            for (LivingEntity e : Ops.around(w, cur, 8, p)) {
                if (done.contains(e.getId())) continue;
                double d = Ops.center(e).squaredDistanceTo(cur);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) return;
            done.add(next.getId());
            final LivingEntity target = next;
            final Vec3d a = cur;
            final float dm = dmg * (float) Math.pow(0.8, i);
            Scheduler.later(3 + i * 3, () -> {
                if (!target.isAlive()) return;
                Vec3d b = Ops.center(target);
                int n = (int) Math.max(3, a.distanceTo(b) * 2);
                for (int k = 0; k <= n; k++) ballFx(w, a.add(b.subtract(a).multiply((double) k / n)), b.subtract(a), k);
                Snd.play(w, b, SoundEvents.BLOCK_CHAIN_HIT, 1.0f, 1.6f);
                hit(w, p, target, dm);
            });
            cur = Ops.center(next);
        }
    }

    private static void shoot(Cast c, Vec3d dir, float dmg, boolean bounce) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Ops.projectile(w, p, c.hand(), dir, 1.9, 28, 0.6, false,
                (q, d, t) -> ballFx(w, q, d, t),
                (q, target) -> {
                    Snd.play(w, q, SoundEvents.BLOCK_CHAIN_HIT, 1.2f, 1.3f);
                    Fx.burst(w, "st_steel", q, 10, 0.3, 0.3);
                    Fx.burst(w, "sp_spark", q, 12, 0.3, 0.3);
                    if (target != null) {
                        hit(w, p, target, dmg);
                        if (bounce) {
                            Set<Integer> done = new HashSet<>();
                            done.add(target.getId());
                            ricochet(w, p, Ops.center(target), done, dmg * 0.7f, 2);
                        }
                    }
                });
    }

    // ------------------------------------------------------------------ 1. Steel Throw
    public static boolean throwBall(Cast c) {
        Snd.play(c.w(), c.hand(), SoundEvents.ITEM_TRIDENT_THROW, 1.0f, 1.8f);
        Snd.play(c.w(), c.hand(), SoundEvents.BLOCK_CHAIN_PLACE, 1.0f, 1.8f);
        Fx.burst(c.w(), "sp_spark", c.hand(), 8, 0.2, 0.1);
        shoot(c, c.look(), 6.5f * c.mult(), true);
        return true;
    }

    // ------------------------------------------------------------------ 2. Ball Fan
    public static boolean fan(Cast c) {
        Snd.play(c.w(), c.hand(), SoundEvents.ITEM_TRIDENT_THROW, 1.2f, 1.4f);
        Snd.play(c.w(), c.hand(), SoundEvents.BLOCK_CHAIN_PLACE, 1.2f, 1.2f);
        Fx.burst(c.w(), "sp_spark", c.hand(), 14, 0.3, 0.1);
        for (int i = -1; i <= 1; i++) shoot(c, c.look().rotateY((float) Math.toRadians(i * 16)), 5f * c.mult(), false);
        return true;
    }

    // ------------------------------------------------------------------ 3. Spin Aura
    public static boolean aura(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long until = w.getTime() + 160;
        s.spinAuraUntil = until;
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_CHAIN_PLACE, 1.5f, 0.8f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 3, 8, Style.SPIN);
        Effects.add(t -> {
            if (w.getTime() >= until || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            for (int k = 0; k < 3; k++) {                                          // three balls orbit and a golden spiral winds around the body
                double a = t * 0.35 + k * Math.PI * 2 / 3;
                Fx.vel(w, "st_steel", b.x + Math.cos(a) * 1.3, b.y + 1.0 + Math.sin(t * 0.1 + k) * 0.3, b.z + Math.sin(a) * 1.3, 0, 0, 0);
                Fx.vel(w, "sp_big", b.x + Math.cos(a) * 1.3, b.y + 1.0 + Math.sin(t * 0.1 + k) * 0.3, b.z + Math.sin(a) * 1.3, 0, 0, 0);
            }
            double y = (t * 0.08) % 2.0;
            for (int k = 0; k < 2; k++) {
                double a = t * 0.6 + k * Math.PI;
                Fx.vel(w, "sp_swirl", b.x + Math.cos(a) * 0.8, b.y + y, b.z + Math.sin(a) * 0.8, 0, 0.03, 0);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Spin Slash
    public static boolean slash(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = Ops.flat(c.look()), base = c.pos().add(0, 1.0, 0);
        Snd.play(w, base, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.8f);
        Snd.play(w, base, SoundEvents.BLOCK_CHAIN_HIT, 1.0f, 0.8f);
        for (int deg = -80; deg <= 80; deg += 8) {                                  // a spinning arc sweeps in front
            Vec3d d = look.rotateY((float) Math.toRadians(deg));
            for (double r = 1.0; r <= 3.4; r += 0.7) {
                Vec3d q = base.add(d.multiply(r));
                Fx.vel(w, r > 2.6 ? "st_steel" : "sp_swirl", q, d.rotateY((float) Math.toRadians(90)).multiply(0.1));
            }
        }
        int n = 0;
        for (LivingEntity e : Ops.around(w, base.add(look.multiply(1.8)), 3.4, p)) {
            Vec3d to = Ops.center(e).subtract(base);
            if (to.length() > 3.8 || Ops.flat(to).dotProduct(look) < 0.2) continue;
            hit(w, p, e, 9f * c.mult());
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 50, 0));
            Ops.push(e, look, 0.8, 0.25);
            n++;
        }
        if (n > 0) Ops.shake(w, base, 8, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 5. Spin Dash
    public static boolean dash(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d dir = Ops.flat(c.look());
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.7f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_CHAIN_PLACE, 1.2f, 1.4f);
        Set<Integer> hit = new HashSet<>();
        float m = c.mult();
        Effects.add(t -> {
            if (t > 8 || !p.isAlive()) {
                if (p.isAlive()) p.setVelocity(p.getVelocity().multiply(0.4));
                return false;
            }
            p.setVelocity(dir.x * 1.5, p.getVelocity().y, dir.z * 1.5);
            p.velocityModified = true;
            p.fallDistance = 0;
            Vec3d b = p.getPos().add(0, 0.9, 0);
            spiral(w, b, dir, t, 0.9);
            spiral(w, b.add(dir.multiply(-0.8)), dir, t + 3, 1.1);
            Fx.vel(w, "st_steel", b.add(Fx.jitter(0.4)), dir.multiply(-0.2));
            Fx.vel(w, "sp_big", b, dir.multiply(-0.1));
            for (LivingEntity e : Ops.around(w, b, 1.7, p)) {
                if (!hit.add(e.getId())) continue;
                hit(w, p, e, 7f * m);
                Ops.push(e, dir, 1.0, 0.35);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Steel Rain
    public static boolean rain(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d ctr = Ops.aimPoint(w, p, 24);
        float m = c.mult();
        Snd.play(w, ctr, SoundEvents.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        Ops.ring(w, ctr.add(0, 0.1, 0), 5, 14, Style.SPIN);
        Effects.add(t -> {
            if (t > 24 || !p.isAlive()) return false;
            for (int k = 0; k < 3; k++) {
                double a = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * 5;
                Vec3d spot = ctr.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                double gy = Ops.groundPos(w, spot).getY();
                for (int s = 0; s < 8; s++) Fx.vel(w, s % 3 == 0 ? "sp_swirl" : "st_steel", spot.x, gy + 9 - s * 0.25, spot.z, 0, -1.0, 0);
                Scheduler.later(9, () -> {
                    Vec3d at = new Vec3d(spot.x, gy + 0.2, spot.z);
                    Snd.play(w, at, SoundEvents.BLOCK_CHAIN_HIT, 0.8f, 1.5f);
                    Fx.burst(w, "st_steel", at, 6, 0.25, 0.3);
                    Fx.burst(w, "sp_spark", at, 6, 0.25, 0.3);
                    Ops.blast(w, at, p, 1.6, 4f * m, 0.3, 0.2, null);
                });
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 7. Infinite Spin
    public static boolean infinite(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity target = Ops.aimEntity(w, p, 22, 0.9);
        if (target == null) return false;
        float m = c.mult();
        Snd.play(w, target.getPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 1.2f);
        Snd.play(w, target.getPos(), SoundEvents.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        Effects.add(t -> {
            if (t > 32 || !p.isAlive() || !target.isAlive()) return false;
            Vec3d ctr = Ops.center(target);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 6));
            for (int ring = 0; ring < 3; ring++) {                                  // the target is held inside a closing spiral
                double y = -0.8 + ring * 0.8, rr = 1.5 - t * 0.02;
                for (int k = 0; k < 3; k++) {
                    double a = t * 0.9 * (ring % 2 == 0 ? 1 : -1) + k * Math.PI * 2 / 3;
                    Vec3d q = ctr.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
                    Fx.vel(w, k == 0 ? "st_steel" : "sp_swirl", q, Vec3d.ZERO);
                }
            }
            Fx.vel(w, "sp_big", ctr.add(Fx.jitter(0.3)), Vec3d.ZERO);
            if (t % 3 == 0) {
                Ops.hurt(w, target, p, 1.7f * m);
                Snd.play(w, ctr, SoundEvents.BLOCK_CHAIN_HIT, 0.7f, 0.8f + t * 0.02f);
            }
            if (t == 32) {
                Snd.play(w, ctr, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 1.3f);
                Fx.burst(w, "st_steel", ctr, 50, 0.6, 0.3);
                Fx.burst(w, "sp_spark", ctr, 50, 0.6, 0.3);
                Fx.burst(w, "sp_big", ctr, 6, 0.3, 0.3);
                Ops.ring(w, target.getPos().add(0, 0.1, 0), 5, 10, Style.SPIN);
                Ops.blast(w, ctr, p, 4.0, 9f * m, 1.1, 0.5, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1)));
                Ops.shake(w, ctr, 20, 6, 10);
            }
            return true;
        });
        return true;
    }
}
