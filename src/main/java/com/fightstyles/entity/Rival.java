package com.fightstyles.entity;

import com.fightstyles.particle.Fx;
import com.fightstyles.util.Effects;
import com.fightstyles.util.Snd;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A fighter to battle. variant 0 Hamon disciple, 1 Spin shooter, 2 vampire, 3 Kars (boss).
 * Each one has its own attacks, drawn with the same cubes as the player techniques.
 */
public class Rival extends HostileEntity {
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(Rival.class, TrackedDataHandlerRegistry.INTEGER);
    private int cooldownA = 60, cooldownB = 120;

    public Rival(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 20;
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.29)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, 0);
    }

    public int variant() {
        return this.dataTracker.get(VARIANT);
    }

    public void setVariant(int v) {
        v = Math.max(0, Math.min(3, v));
        this.dataTracker.set(VARIANT, v);
        double hp = switch (v) {
            case 0 -> 34;
            case 1 -> 30;
            case 2 -> 40;
            default -> 120;
        };
        var attr = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);
        var dmg = getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(v == 3 ? 9.0 : v == 2 ? 6.0 : 4.0);
        setHealth((float) hp);
        this.experiencePoints = v == 3 ? 120 : 25;
        setCustomName(Text.translatable("entity.fightstyles.rival." + v));
        setPersistent();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", variant());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(VARIANT, nbt.getInt("Variant"));
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (!(getWorld() instanceof ServerWorld w)) return;
        int v = variant();
        String[] small = {"h_spark", "sp_spark", "v_drop", "k_spark"};
        if (age % 3 == 0) Fx.vel(w, small[v], getPos().add(Fx.jitter(0.45)).add(0, 1.0 + Fx.R.nextDouble() * 0.8, 0), Vec3d.ZERO);
        if (v == 2 && age % 20 == 0 && w.isDay() && !w.isRaining() && w.isSkyVisible(getBlockPos()) && getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty())
            setOnFireFor(6);                                                      // vampires burn in the sun
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || !canSee(t)) return;
        double dist = distanceTo(t);
        if (cooldownA > 0) cooldownA--;
        if (cooldownB > 0) cooldownB--;
        if (cooldownA <= 0 && dist > 3.0 && dist < 18.0) {
            cooldownA = v == 3 ? 70 : 80 + random.nextInt(30);
            attackA(w, t);
        }
        if (cooldownB <= 0 && dist < 9.0) {
            cooldownB = v == 3 ? 150 : 170 + random.nextInt(60);
            attackB(w, t);
        }
    }

    private void pose(int pose, int ticks) {
        com.fightstyles.net.PosePayload pl = new com.fightstyles.net.PosePayload(getId(), pose, ticks);
        for (net.minecraft.server.network.ServerPlayerEntity o : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this))
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(o, com.fightstyles.net.PosePayload.ID))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(o, pl);
    }

    // ------------------------------------------------------------------ ranged attack
    private void attackA(ServerWorld w, LivingEntity t) {
        int v = variant();
        Vec3d from = getEyePos().add(0, -0.3, 0);
        Vec3d dir = t.getPos().add(0, t.getHeight() * 0.6, 0).subtract(from).normalize();
        pose(1, 10);
        switch (v) {
            case 0 -> shoot(w, from, dir, 0.9, 26, 5.5f, "h_ray", "h_big", "h_spark");
            case 1 -> {
                for (int i = -1; i <= 1; i++) shoot(w, from, dir.rotateY((float) Math.toRadians(i * 10.0)), 1.3, 20, 3.5f, "sp_swirl", "st_steel", "sp_spark");
            }
            case 2 -> {
                shoot(w, from, dir, 1.0, 22, 4.5f, "v_drop", "v_big", "v_drop");
                heal(1.5f);
            }
            default -> shoot(w, from, dir, 1.6, 24, 9f, "k_light", "k_big", "k_spark");
        }
        Snd.play(w, from, SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f, v == 3 ? 1.8f : 1.2f);
    }

    /** A small bolt: flies straight, hurts the first living thing (not another rival) and bursts. */
    private void shoot(ServerWorld w, Vec3d from, Vec3d dir, double speed, int life, float dmg, String trail, String big, String burst) {
        Vec3d step = dir.multiply(speed);
        Vec3d[] pos = {from};
        Rival self = this;
        Effects.add(t -> {
            if (t > life || !self.isAlive()) return false;
            Vec3d next = pos[0].add(step);
            Fx.vel(w, trail, next, Vec3d.ZERO);
            if (t % 2 == 0) Fx.vel(w, big, next, Vec3d.ZERO);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(next, next).expand(0.7), x -> x != self && !(x instanceof Rival))) {
                e.damage(w.getDamageSources().mobAttack(self), dmg);
                Fx.burst(w, burst, next, 16, 0.3, 0.3);
                return false;
            }
            if (!w.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(next)).getCollisionShape(w, net.minecraft.util.math.BlockPos.ofFloored(next)).isEmpty()) {
                Fx.burst(w, burst, pos[0], 10, 0.2, 0.2);
                return false;
            }
            pos[0] = next;
            return true;
        });
    }

    // ------------------------------------------------------------------ close-range / special attack
    private void attackB(ServerWorld w, LivingEntity t) {
        int v = variant();
        Vec3d c = getPos();
        String[] small = {"h_spark", "sp_spark", "v_drop", "k_spark"}, big = {"h_big", "sp_big", "v_big", "k_big"};
        pose(4, 14);
        Snd.play(w, c, v == 3 ? SoundEvents.ENTITY_WARDEN_SONIC_BOOM : SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        Fx.burst(w, big[v], c.add(0, 1, 0), 6, 0.2, 0.2);
        Fx.burst(w, small[v], c.add(0, 1, 0), 50, 0.5, 0.3);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            Fx.vel(w, small[v], c.add(Math.cos(a) * 1.5, 0.2, Math.sin(a) * 1.5), new Vec3d(Math.cos(a) * 0.35, 0.05, Math.sin(a) * 0.35));
        }
        float dmg = v == 3 ? 8f : 5f;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(4.2), x -> x != this && !(x instanceof Rival))) {
            e.damage(w.getDamageSources().mobAttack(this), dmg);
            Vec3d d = e.getPos().subtract(c);
            Vec3d n = d.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : d.normalize();
            e.takeKnockback(0.9, -n.x, -n.z);
            if (v == 1) e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
            if (v == 2) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 50, 0));
                heal(3f);
            }
            if (v == 3) e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0));
        }
    }

    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && target instanceof LivingEntity e && getWorld() instanceof ServerWorld w) {
            int v = variant();
            String[] small = {"h_spark", "sp_spark", "v_drop", "k_spark"};
            Fx.burst(w, small[v], e.getPos().add(0, 1, 0), 14, 0.3, 0.3);
            if (v == 2) heal(2f);
        }
        return ok;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!(getWorld() instanceof ServerWorld w)) return;
        String[] small = {"h_spark", "sp_spark", "v_drop", "k_spark"}, big = {"h_big", "sp_big", "v_big", "k_big"};
        Vec3d c = getPos().add(0, 1, 0);
        Fx.burst(w, small[variant()], c, 60, 0.5, 0.4);
        Fx.burst(w, big[variant()], c, 8, 0.2, 0.3);
        int v = variant();
        if (random.nextInt(3) == 0 || v == 3) {
            ItemStack drop = switch (v) {
                case 0 -> new ItemStack(com.fightstyles.item.ModItems.HAMON_MANUAL);
                case 1 -> new ItemStack(com.fightstyles.item.ModItems.STEEL_BALL, 2);
                case 2 -> new ItemStack(com.fightstyles.item.ModItems.STONE_MASK);
                default -> new ItemStack(com.fightstyles.item.ModItems.AJA_STONE);
            };
            dropStack(drop);
        }
        if (v == 0 && random.nextInt(4) == 0) dropStack(new ItemStack(com.fightstyles.item.ModItems.CAESAR_GLOVES));
        if (random.nextInt(2) == 0) dropStack(new ItemStack(Items.GOLD_NUGGET, 2 + random.nextInt(4)));
    }
}
