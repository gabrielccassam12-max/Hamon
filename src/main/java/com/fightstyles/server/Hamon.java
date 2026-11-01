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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** HAMON: sunlight energy from controlled breathing. Warm golden cubes; devastating to the undead and to vampires. */
public final class Hamon {
    private Hamon() {}

    private static final Random R = Fx.R;

    /** A Hamon hit: sparks, and vampires (players) take extra damage and burn. Undead mobs are handled by the damage event. */
    public static void shock(ServerWorld w, ServerPlayerEntity owner, LivingEntity e, float dmg) {
        if (dmg > 0f) Ops.hurt(w, e, owner, dmg);          // undead and vampires take extra through the damage event
        mark(w, e);
    }

    /** Sparks on the victim; undead and vampires also catch fire. */
    public static void mark(ServerWorld w, LivingEntity e) {
        boolean vamp = e instanceof ServerPlayerEntity v && Fighter.get(v).has(Style.VAMPIRE);
        if (vamp || e.getType().isIn(net.minecraft.registry.tag.EntityTypeTags.UNDEAD)) {
            e.setOnFireFor(5);
            Fx.burst(w, "h_big", Ops.center(e), 3, 0.12, 0.2);
        }
        Fx.burst(w, "h_spark", Ops.center(e), 10, 0.25, 0.3);
    }

    // ------------------------------------------------------------------ 1. Hamon Punch
    public static boolean punch(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look(), o = c.eye().add(look.multiply(1.6)).add(0, -0.4, 0);
        Snd.play(w, o, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
        Snd.play(w, o, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.7f);
        Fx.burst(w, "h_spark", o, 22, 0.28, 0.1);
        for (int i = 0; i < 5; i++) Fx.vel(w, "h_ray", o.add(Fx.jitter(0.2)), look.multiply(0.3));
        Fx.circle(w, "h_ring", o.add(0, -0.2, 0), 0.7, 14, 0, 0);
        int hit = 0;
        for (LivingEntity e : Ops.around(w, c.eye().add(look.multiply(1.8)), 2.3, p)) {
            shock(w, p, e, 6.5f * c.mult());
            Ops.push(e, look, 0.9, 0.3);
            hit++;
        }
        if (hit > 0) {
            Snd.play(w, o, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2.0f);
            Ops.shake(w, o, 8, 2, 4);
        }
        return true;
    }

    // ------------------------------------------------------------------ 2. Deep Breath: refills energy
    public static boolean breath(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.9f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_BREATH, 1.0f, 0.7f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
        Ops.ring(w, c.pos().add(0, 0.1, 0), 3.2, 10, Style.HAMON);
        Effects.add(t -> {
            if (t > 30 || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            for (int i = 0; i < 5; i++) {                                           // sunlight flows into the chest
                double a = R.nextDouble() * Math.PI * 2, rr = 2.4 + R.nextDouble() * 0.8, y = 0.2 + R.nextDouble() * 1.6;
                Vec3d from = b.add(Math.cos(a) * rr, y, Math.sin(a) * rr), to = b.add(0, 1.3, 0);
                Fx.vel(w, i % 2 == 0 ? "h_spark" : "h_ray", from, to.subtract(from).multiply(0.09));
            }
            if (t % 3 == 0) {
                SData d = Fighter.get(p);
                Fighter.set(p, d.withEnergy(Style.HAMON, d.energy(Style.HAMON) + 4));
                Fighter.sync(p);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Sun Pulse
    public static boolean pulse(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        Snd.play(w, at, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.7f, 1.8f);
        Ops.ring(w, at.add(0, 0.1, 0), 5, 10, Style.HAMON);
        Effects.groundRing(w, at.add(0, 0.5, 0), 5, 12, "h_ring", "h_ray");
        Fx.burst(w, "h_spark", at.add(0, 1, 0), 50, 0.5, 0.2);
        Fx.burst(w, "h_big", at.add(0, 1, 0), 5, 0.2, 0.2);
        Ops.blast(w, at, p, 5.0, 7f * c.mult(), 1.1, 0.4, e -> mark(w, e));
        Ops.shake(w, at, 14, 4, 8);
        return true;
    }

    // ------------------------------------------------------------------ 4. Ripple Shield
    public static boolean ripple(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long until = w.getTime() + 200;
        s.rippleUntil = until;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0));
        Snd.play(w, c.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 1.4f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 2.5, 8, Style.HAMON);
        Effects.add(t -> {
            if (w.getTime() >= until || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            if (t % 10 == 1) Fx.circle(w, "h_ring", b.add(0, 0.1 + (t % 30) / 15.0, 0), 1.1, 22, R.nextDouble(), 0);
            for (int i = 0; i < 2; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Fx.vel(w, "h_ray", b.x + Math.cos(a) * 0.9, b.y + 0.2 + R.nextDouble() * 1.7, b.z + Math.sin(a) * 0.9, 0, 0.02, 0);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Zoom Punch: the arm reaches far
    public static boolean zoomPunch(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look().normalize(), from = c.hand();
        Snd.play(w, from, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        Snd.play(w, from, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
        Set<Integer> hit = new HashSet<>();
        double len = 9;
        for (double d = 0.5; d <= len; d += 0.4) {
            Vec3d q = from.add(look.multiply(d));
            BlockPos bp = BlockPos.ofFloored(q);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
                Fx.burst(w, "h_spark", q, 16, 0.3, 0.2);
                break;
            }
            Fx.vel(w, "h_ray", q, Vec3d.ZERO);
            if (((int) (d * 2.5)) % 2 == 0) Fx.vel(w, "h_spark", q.add(Fx.jitter(0.12)), look.multiply(0.1));
            for (LivingEntity e : Ops.around(w, q, 1.1, p)) {
                if (!hit.add(e.getId())) continue;
                shock(w, p, e, 9f * c.mult());
                Ops.push(e, look, 1.3, 0.4);
                Fx.burst(w, "h_big", Ops.center(e), 4, 0.2, 0.3);
            }
        }
        Fx.burst(w, "h_ring", from, 6, 0.1, 0.1);
        if (!hit.isEmpty()) Ops.shake(w, from, 10, 3, 5);
        return true;
    }

    // ------------------------------------------------------------------ 6. Sun Disc: boomerang of light
    public static boolean disc(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d dir = c.look().normalize();
        Vec3d[] pos = {c.hand()};
        boolean[] back = {false};
        Set<Integer> hit = new HashSet<>();
        Snd.play(w, pos[0], SoundEvents.ITEM_TRIDENT_THROW, 1.0f, 1.6f);
        Effects.add(t -> {
            if (!p.isAlive() || t > 60) return false;
            Vec3d o = pos[0];
            if (!back[0]) {
                Vec3d next = o.add(dir.multiply(1.1));
                BlockPos bp = BlockPos.ofFloored(next);
                if (t > 14 || !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
                    back[0] = true;
                    hit.clear();
                } else pos[0] = next;
            } else {
                Vec3d to = p.getEyePos().add(0, -0.4, 0).subtract(o);
                if (to.length() < 1.4) {
                    Snd.play(w, o, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
                    return false;
                }
                pos[0] = o.add(to.normalize().multiply(1.4));
            }
            o = pos[0];
            for (int i = 0; i < 14; i++) {                                           // a spinning ring of light
                double a = t * 0.9 + i * Math.PI * 2 / 14;
                Vec3d side = Ops.rightOf(dir);
                Vec3d q = o.add(side.multiply(Math.cos(a) * 0.9)).add(0, Math.sin(a) * 0.9, 0);
                Fx.vel(w, "h_ray", q, Vec3d.ZERO);
            }
            Fx.vel(w, "h_big", o, Vec3d.ZERO);
            for (LivingEntity e : Ops.around(w, o, 1.5, p)) {
                if (!hit.add(e.getId())) continue;
                shock(w, p, e, 6f * m);
                Ops.push(e, dir, 0.4, 0.2);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 7. Sun Flare
    public static boolean flare(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d at = c.pos();
        Snd.play(w, at, SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 1.8f);
        Ops.ring(w, at.add(0, 0.1, 0), 11, 24, Style.HAMON);
        Effects.add(t -> {
            if (!p.isAlive()) return false;
            Vec3d b = p.getPos();
            if (t <= 22) {                                                          // sunlight gathers into a sphere
                Fx.sphere(w, "h_big", b.add(0, 1.2, 0), 0.6 + t * 0.07, 6, Vec3d.ZERO);
                Fx.sphere(w, "h_spark", b.add(0, 1.2, 0), 2.6 - t * 0.09, 8, Vec3d.ZERO);
                if (t % 5 == 0) Snd.play(w, b, SoundEvents.BLOCK_AMETHYST_CLUSTER_STEP, 1.0f, 0.8f + t * 0.04f);
                return true;
            }
            Snd.play(w, b, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0f, 1.4f);
            Snd.play(w, b, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.8f);
            Fx.burst(w, "h_spark", b.add(0, 1.2, 0), 120, 0.9, 0.2);
            Fx.burst(w, "h_big", b.add(0, 1.2, 0), 14, 0.5, 0.2);
            for (int i = 0; i < 30; i++) Fx.vel(w, "h_ray", b.x, b.y + i * 0.4, b.z, 0, 0.25, 0);
            Effects.groundRing(w, b.add(0, 0.4, 0), 11, 16, "h_ring", "h_ray");
            Effects.groundRing(w, b.add(0, 0.8, 0), 8, 12, "h_ring", "h_spark");
            Ops.blast(w, b, p, 11.0, 16f * m, 1.5, 0.7, e -> {
                mark(w, e);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
            });
            for (ServerPlayerEntity o : w.getPlayers(q -> q != p && q.squaredDistanceTo(p) < 144)) healAlly(w, o);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 1));
            Ops.shake(w, b, 30, 8, 14);
            return false;
        });
        return true;
    }

    private static void healAlly(ServerWorld w, ServerPlayerEntity o) {
        o.heal(6f);
        o.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
        Fx.burst(w, "h_spark", o.getPos().add(0, 1, 0), 12, 0.2, 0.4);
    }
}
