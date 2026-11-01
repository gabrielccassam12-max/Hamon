package com.fightstyles.server;

import com.fightstyles.core.SData;
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

/** VAMPIRISM: blood is the energy. Crimson cubes, dark mist and bats. */
public final class Vampire {
    private Vampire() {}

    private static final Random R = Fx.R;

    private static void bloodHit(ServerWorld w, LivingEntity e, int n) {
        Fx.burst(w, "v_drop", Ops.center(e), n, 0.3, 0.3);
        Fx.burst(w, "v_big", Ops.center(e), 2, 0.1, 0.2);
    }

    // ------------------------------------------------------------------ 1. Blood Bite: the way a vampire feeds
    public static boolean bite(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity t = Ops.aimEntity(w, p, 3.4, 0.7);
        if (t == null) return false;
        Vec3d at = Ops.center(t).add(0, 0.3, 0);
        Snd.play(w, at, SoundEvents.ENTITY_PHANTOM_BITE, 1.2f, 0.8f);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_DRINK, 1.0f, 0.6f);
        Ops.hurt(w, t, p, 6f * c.mult());
        p.heal(3f);
        bloodHit(w, t, 20);
        Vec3d to = p.getEyePos().subtract(at);
        for (int i = 0; i < 12; i++) Fx.vel(w, "v_drop", at.add(Fx.jitter(0.15)), to.multiply(0.09));    // blood flows to you
        SData d = Fighter.get(p);
        Fighter.set(p, d.withEnergy(Style.VAMPIRE, d.energy(Style.VAMPIRE) + 18).addStat(SData.ST_BITES, 1));
        Ops.shake(w, at, 6, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 2. Blood Lash
    public static boolean lash(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look().normalize(), from = c.hand();
        Snd.play(w, from, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        Snd.play(w, from, SoundEvents.ENTITY_PHANTOM_BITE, 0.8f, 1.6f);
        Vec3d side = Ops.rightOf(look);
        for (double d = 0.5; d <= 9; d += 0.35) {                                   // a curved whip of blood
            double bend = Math.sin(d * 0.7) * 0.6 * (1.0 - d / 10.0);
            Vec3d q = from.add(look.multiply(d)).add(side.multiply(bend));
            Fx.vel(w, "v_drop", q, Vec3d.ZERO);
            if (((int) (d * 3)) % 2 == 0) Fx.vel(w, "v_big", q, Vec3d.ZERO);
        }
        int n = 0;
        for (LivingEntity e : Ops.around(w, c.eye().add(look.multiply(4.5)), 5.0, p)) {
            Vec3d to = Ops.center(e).subtract(c.eye());
            if (to.length() > 9.5 || to.normalize().dotProduct(look) < 0.8 || n >= 2) continue;
            Ops.hurt(w, e, p, 7f * c.mult());
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 50, 0));
            Ops.push(e, p.getPos().subtract(e.getPos()), 0.9, 0.2);                 // dragged towards you
            bloodHit(w, e, 14);
            Fighter.gainBlood(p, 6);
            n++;
        }
        if (n > 0) Ops.shake(w, from, 8, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 3. Bat Flight
    public static boolean bats(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look().normalize();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_BAT_TAKEOFF, 1.5f, 0.7f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_BAT_AMBIENT, 1.5f, 0.6f);
        Fx.burst(w, "v_bat", c.pos().add(0, 1, 0), 40, 0.5, 0.3);
        p.setVelocity(look.x * 1.5, Math.max(0.45, look.y * 1.2 + 0.4), look.z * 1.5);
        p.velocityModified = true;
        p.fallDistance = 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, true, false, false));
        Set<Integer> hit = new HashSet<>();
        Effects.add(t -> {
            if (t > 14 || !p.isAlive()) return false;
            Vec3d b = p.getPos().add(0, 1, 0);
            for (int i = 0; i < 6; i++) Fx.vel(w, "v_bat", b.add(Fx.jitter(0.6)), Fx.jitter(0.1).add(look.multiply(-0.1)));
            if (t % 3 == 0) Fx.vel(w, "v_mist", b.add(Fx.jitter(0.3)), Vec3d.ZERO);
            for (LivingEntity e : Ops.around(w, b, 1.6, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 4f * m);
                bloodHit(w, e, 8);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Eye Jets: pressurised blood from the eyes
    public static boolean eyes(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look().normalize(), eye = c.eye(), side = Ops.rightOf(look);
        double len = 14;
        BlockHitResult bh = w.raycast(new RaycastContext(eye, eye.add(look.multiply(len)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) len = bh.getPos().distanceTo(eye);
        Snd.play(w, eye, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);
        Snd.play(w, eye, SoundEvents.ENTITY_ARROW_SHOOT, 1.2f, 0.5f);
        for (int s = -1; s <= 1; s += 2) {
            Vec3d o = eye.add(side.multiply(0.17 * s)).add(look.multiply(0.3));
            for (double d = 0; d <= len; d += 0.35) {
                Vec3d q = o.add(look.multiply(d));
                Fx.vel(w, "v_drop", q, Vec3d.ZERO);
                if (((int) (d * 3)) % 3 == 0) Fx.vel(w, "v_ring", q, Vec3d.ZERO);
            }
        }
        Set<Integer> hit = new HashSet<>();
        for (double d = 0.5; d <= len; d += 0.5) {
            for (LivingEntity e : Ops.around(w, eye.add(look.multiply(d)), 0.9, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 11f * c.mult());
                bloodHit(w, e, 16);
                Fighter.gainBlood(p, 4);
            }
        }
        if (bh.getType() != HitResult.Type.MISS) Fx.burst(w, "v_drop", bh.getPos(), 20, 0.3, 0.2);
        Ops.shake(w, eye, 10, 3, 6);
        return true;
    }

    // ------------------------------------------------------------------ 5. Blood Frenzy
    public static boolean frenzy(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long until = w.getTime() + 200;
        s.frenzyUntil = until;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
        Snd.play(w, c.pos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.7f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 4, 10, Style.VAMPIRE);
        Fx.burst(w, "v_drop", c.pos().add(0, 1, 0), 50, 0.5, 0.3);
        Ops.shake(w, c.pos(), 12, 4, 8);
        Effects.add(t -> {
            if (w.getTime() >= until || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            for (int i = 0; i < 3; i++) Fx.vel(w, "v_drop", b.add(R.nextGaussian() * 0.4, 0.1 + R.nextDouble() * 1.8, R.nextGaussian() * 0.4), new Vec3d(0, 0.06, 0));
            if (t % 4 == 0) Fx.vel(w, "v_big", b.add(R.nextGaussian() * 0.3, 0.4 + R.nextDouble() * 1.2, R.nextGaussian() * 0.3), new Vec3d(0, 0.04, 0));
            if (t % 20 == 0) Fx.circle(w, "v_ring", b.add(0, 0.1, 0), 1.2, 22, R.nextDouble(), 0);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Freezing Touch
    public static boolean freeze(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        Snd.play(w, at, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        Snd.play(w, at, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        Ops.ring(w, at.add(0, 0.1, 0), 5.5, 12, Style.VAMPIRE);
        Effects.groundRing(w, at.add(0, 0.4, 0), 5.5, 14, "v_ring", "v_frost");
        Fx.burst(w, "v_frost", at.add(0, 1, 0), 70, 0.5, 0.2);
        int n = Ops.blast(w, at, p, 5.5, 7f * c.mult(), 0.3, 0.1, e -> {
            e.setFrozenTicks(260);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
            Fx.burst(w, "v_frost", Ops.center(e), 12, 0.2, 0.3);
        });
        if (n > 0) {
            p.heal(2f * n);
            Fighter.gainBlood(p, 5 * n);
        }
        Ops.shake(w, at, 12, 3, 8);
        return true;
    }

    // ------------------------------------------------------------------ 7. Night Dominion
    public static boolean night(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        float m = c.mult();
        Snd.play(w, at, SoundEvents.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);
        Snd.play(w, at, SoundEvents.ENTITY_BAT_TAKEOFF, 2.0f, 0.5f);
        Ops.ring(w, at.add(0, 0.1, 0), 13, 24, Style.VAMPIRE);
        Fx.burst(w, "v_bat", at.add(0, 1, 0), 80, 0.8, 0.4);
        Ops.shake(w, at, 26, 6, 16);
        Effects.add(t -> {
            if (t > 100 || !p.isAlive()) return false;
            Vec3d b = at;
            Fx.sphere(w, "v_mist", b.add(0, 0.5, 0), 13, 16, Vec3d.ZERO);
            Fx.sphere(w, "v_big", b.add(0, 0.5, 0), 13, 4, Vec3d.ZERO);
            if (t % 3 == 0) Fx.vel(w, "v_bat", b.x + R.nextGaussian() * 6, b.y + 1 + R.nextDouble() * 5, b.z + R.nextGaussian() * 6, R.nextGaussian() * 0.15, 0.05, R.nextGaussian() * 0.15);
            if (t % 10 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, true, false, false));
                for (LivingEntity e : Ops.around(w, b.add(0, 1, 0), 13, p)) {
                    Ops.hurt(w, e, p, 2.5f * m);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    p.heal(1f);
                    Fighter.gainBlood(p, 3);
                    bloodHit(w, e, 6);
                }
            }
            return true;
        });
        return true;
    }
}
