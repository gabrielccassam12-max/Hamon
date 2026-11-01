package com.fightstyles.server;

import com.fightstyles.core.StyleConfig;
import com.fightstyles.net.ShakePayload;
import com.fightstyles.particle.Fx;
import com.fightstyles.util.Effects;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Random;

/** Shared building blocks of every technique: targeting, damage, knockback, projectiles, camera shake. */
public final class Ops {
    private Ops() {}

    public static final Random R = Fx.R;

    public static Vec3d rightOf(Vec3d look) {
        Vec3d r = look.crossProduct(new Vec3d(0, 1, 0));
        return r.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : r.normalize();
    }

    public static Vec3d center(LivingEntity e) {
        return e.getPos().add(0, e.getHeight() / 2, 0);
    }

    public static List<LivingEntity> targets(ServerWorld w, Box box, PlayerEntity owner) {
        boolean pvp = StyleConfig.INSTANCE.allowPvp && w.getServer() != null && w.getServer().isPvpEnabled();
        return w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != owner && !e.isSpectator()
                && !(e instanceof ArmorStandEntity) && (pvp || !(e instanceof PlayerEntity)));
    }

    public static List<LivingEntity> around(ServerWorld w, Vec3d c, double r, PlayerEntity owner) {
        return targets(w, new Box(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r), owner).stream()
                .filter(e -> center(e).squaredDistanceTo(c) <= r * r).toList();
    }

    public static void hurt(ServerWorld w, LivingEntity t, PlayerEntity owner, float amount) {
        t.damage(owner != null ? w.getDamageSources().playerAttack(owner) : w.getDamageSources().generic(), amount);
    }

    public static void push(LivingEntity e, Vec3d dir, double horiz, double up) {
        Vec3d h = new Vec3d(dir.x, 0, dir.z);
        h = h.lengthSquared() < 1e-4 ? Vec3d.ZERO : h.normalize();
        e.addVelocity(h.x * horiz, up, h.z * horiz);
        e.velocityModified = true;
    }

    /** Pushes away from c with fall-off (k = 1 at the centre). */
    public static void blow(LivingEntity e, Vec3d c, double force, double up) {
        Vec3d d = e.getPos().subtract(c);
        push(e, d, force, up);
    }

    /** Block hit by the player's look, or a spot on the ground in front of them. */
    public static Vec3d aimPoint(ServerWorld w, ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        BlockHitResult bh = w.raycast(new RaycastContext(eye, eye.add(look.multiply(range)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) {
            Vec3d h = bh.getPos();
            return bh.getSide().getAxis().isVertical() ? h : h.add(bh.getSide().getOffsetX() * 0.6, 0, bh.getSide().getOffsetZ() * 0.6);
        }
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        return p.getPos().add(flat.multiply(Math.min(range, 8.0)));
    }

    /** Nearest living entity inside a narrow cone in front of the player. */
    public static LivingEntity aimEntity(ServerWorld w, ServerPlayerEntity p, double range, double minDot) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        LivingEntity best = null;
        double bd = 1e9;
        for (LivingEntity e : targets(w, new Box(eye, eye.add(look.multiply(range))).expand(3), p)) {
            Vec3d to = center(e).subtract(eye);
            double d = to.length();
            if (d < 0.3 || d > range || to.normalize().dotProduct(look) < minDot) continue;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** First open block above the ground at the given spot. */
    public static BlockPos groundPos(ServerWorld w, Vec3d at) {
        BlockPos b = BlockPos.ofFloored(at);
        for (int i = 0; i < 6 && w.getBlockState(b).isSolidBlock(w, b); i++) b = b.up();
        for (int i = 0; i < 14 && !w.getBlockState(b.down()).isSolidBlock(w, b.down()) && !w.isOutOfHeightLimit(b.down()); i++) b = b.down();
        return b;
    }

    public static void shake(ServerWorld w, Vec3d c, double radius, int strength, int ticks) {
        if (!StyleConfig.INSTANCE.cameraShake) return;
        for (ServerPlayerEntity pl : w.getPlayers(q -> q.squaredDistanceTo(c) < radius * radius)) {
            if (ServerPlayNetworking.canSend(pl, ShakePayload.ID)) ServerPlayNetworking.send(pl, new ShakePayload(strength, ticks));
        }
    }

    public static void ring(ServerWorld w, Vec3d c, double r, int ticks, com.fightstyles.core.Style e) {
        Effects.groundRing(w, c, r, ticks, e.ring, e.small);
    }

    // ------------------------------------------------------------------ projectiles
    public interface Trail {
        void at(Vec3d pos, Vec3d dir, int t);
    }

    public interface Impact {
        void hit(Vec3d pos, LivingEntity target);
    }

    /**
     * Flies a bolt in a straight line. Stops on a block or the first living target (radius), then calls impact.
     * Reaching maxTicks ends it quietly (impact is only called if endImpact is true).
     */
    public static void projectile(ServerWorld w, ServerPlayerEntity owner, Vec3d start, Vec3d dir, double speed, int maxTicks,
                                  double radius, boolean endImpact, Trail trail, Impact impact) {
        Vec3d step = dir.normalize().multiply(speed);
        Vec3d[] pos = {start};
        Effects.add(t -> {
            if (t > maxTicks || !owner.isAlive()) {
                if (endImpact) impact.hit(pos[0], null);
                return false;
            }
            Vec3d old = pos[0], next = old.add(step);
            BlockHitResult bh = w.raycast(new RaycastContext(old, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, owner));
            boolean block = bh.getType() != HitResult.Type.MISS;
            Vec3d at = block ? bh.getPos().subtract(step.normalize().multiply(0.3)) : next;
            List<LivingEntity> near = targets(w, new Box(old, next).expand(radius), owner);
            if (block || !near.isEmpty()) {
                impact.hit(at, near.isEmpty() ? null : near.get(0));
                return false;
            }
            pos[0] = next;
            trail.at(next, step, t);
            return true;
        });
    }

    /** Area damage with fall-off and knockback. Returns how many were hit. */
    public static int blast(ServerWorld w, Vec3d c, PlayerEntity owner, double radius, float dmg, double force, double up,
                            java.util.function.Consumer<LivingEntity> extra) {
        int n = 0;
        for (LivingEntity e : around(w, c, radius, owner)) {
            double d = center(e).distanceTo(c);
            float k = (float) (1.0 - 0.5 * d / radius);
            hurt(w, e, owner, dmg * k);
            blow(e, c.add(0, -0.4, 0), force * k, up * k);
            if (extra != null) extra.accept(e);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ movement helpers
    /** True when a player-sized body fits at this feet position. */
    public static boolean freeAt(ServerWorld w, Vec3d feet) {
        BlockPos a = BlockPos.ofFloored(feet), b = a.up();
        return w.getBlockState(a).getCollisionShape(w, a).isEmpty() && w.getBlockState(b).getCollisionShape(w, b).isEmpty();
    }

    /** Walks from start towards end in half-block steps and returns the last free feet position. */
    public static Vec3d walkFree(ServerWorld w, Vec3d start, Vec3d end) {
        int steps = (int) Math.max(1, Math.ceil(start.distanceTo(end) / 0.5));
        Vec3d last = start;
        for (int i = 1; i <= steps; i++) {
            Vec3d q = start.add(end.subtract(start).multiply((double) i / steps));
            if (!freeAt(w, q)) break;
            last = q;
        }
        return last;
    }

    public static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : f.normalize();
    }
}
