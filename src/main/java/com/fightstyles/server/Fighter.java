package com.fightstyles.server;

import com.fightstyles.FightStyles;
import com.fightstyles.core.SData;
import com.fightstyles.core.Skill;
import com.fightstyles.core.Style;
import com.fightstyles.core.StyleConfig;
import com.fightstyles.item.ModItems;
import com.fightstyles.net.PosePayload;
import com.fightstyles.net.StatePayload;
import com.fightstyles.net.SyncPayload;
import com.fightstyles.particle.Fx;
import com.fightstyles.util.Effects;
import com.fightstyles.util.Snd;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server brain: saved data, ranks, energy, casting rules, passives (sunlight, night), defences and syncing. */
public final class Fighter {
    private Fighter() {}

    public static final AttachmentType<SData> DATA = AttachmentRegistry.<SData>builder()
            .persistent(SData.CODEC)
            .copyOnDeath()
            .buildAndRegister(FightStyles.id("fighter"));

    private static final Map<UUID, PState> STATES = new HashMap<>();
    private static boolean GUARD = false;
    private static final java.util.Random R = Fx.R;

    public static SData get(ServerPlayerEntity p) {
        return ((AttachmentTarget) p).getAttachedOrElse(DATA, SData.DEFAULT);
    }

    public static void set(ServerPlayerEntity p, SData d) {
        ((AttachmentTarget) p).setAttached(DATA, d);
    }

    public static PState st(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> new PState());
    }

    public static void note(ServerPlayerEntity p, String key, Formatting f) {
        p.sendMessage(Text.translatable(key).formatted(f), true);
    }

    private static ServerWorld world(ServerPlayerEntity p) {
        return (ServerWorld) p.getWorld();
    }

    public static boolean hasBall(ServerPlayerEntity p) {
        return p.getInventory().contains(stack -> stack.isOf(ModItems.STEEL_BALL));
    }

    private static float damageMult(SData d, Style s) {
        return (float) (StyleConfig.INSTANCE.damageMultiplier * (1.0 + 0.12 * d.rank(s)));
    }

    /** True while direct sunlight reaches the player (what hurts vampires and feeds the Hamon). */
    public static boolean sunExposed(ServerPlayerEntity p) {
        ServerWorld w = world(p);
        return w.getDimension().hasSkyLight() && w.isDay() && !w.isRaining() && w.isSkyVisible(p.getBlockPos().up())
                && !p.isTouchingWater();
    }

    // ================================================================== init
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                SData d = get(p);
                if (d.unlocked() == 0) continue;
                PState s = st(p);
                if (!p.isAlive()) continue;
                ServerWorld w = world(p);
                boolean dirty = false;
                if (d.style() == null || !d.has(d.style())) {
                    for (Style x : Style.values()) {
                        if (d.has(x)) {
                            d = d.withActive(x.ordinal());
                            break;
                        }
                    }
                    set(p, d);
                    dirty = true;
                }
                boolean sun = sunExposed(p);
                if (tick % 8 == 0) {                                              // passive energy
                    for (Style x : Style.values()) {
                        if (!d.has(x) || d.energy(x) >= d.maxEnergy(x)) continue;
                        double add = switch (x) {
                            case HAMON -> sun ? 2.0 : 1.0;
                            case SPIN -> 1.0;
                            case VAMPIRE -> sun ? 0.0 : (w.isNight() || !w.isSkyVisible(p.getBlockPos().up())) ? 0.5 : 0.0;
                        };
                        int whole = (int) Math.floor(add * StyleConfig.INSTANCE.energyRegenMultiplier + R.nextDouble());
                        if (whole > 0) {
                            d = d.withEnergy(x, d.energy(x) + whole);
                            dirty = true;
                        }
                    }
                    set(p, d);
                }
                if (d.has(Style.VAMPIRE) && tick % 20 == 0) vampirePassive(p, s, d, w, sun);
                if (s.frenzyUntil > w.getTime() && tick % 10 == 0) {                 // frenzy: feeds on whatever is close
                    for (LivingEntity e : Ops.around(w, p.getPos().add(0, 1, 0), 3.0, p)) {
                        Ops.hurt(w, e, p, 1.5f);
                        p.heal(0.8f);
                        Fx.burst(w, "v_drop", Ops.center(e), 4, 0.15, 0.2);
                        break;
                    }
                }
                if (dirty || tick % 10 == 0) sync(p);
                int hash = d.unlocked() | ((d.active() + 1) << 4);
                if (hash != s.lastHash || tick % 100 == 0) {
                    s.lastHash = hash;
                    broadcastState(p);
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sync(handler.getPlayer());
            broadcastState(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUuid()));

        // defences + the Hamon against the undead
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (GUARD) return true;
            if (!(entity instanceof ServerPlayerEntity sp)) {
                if (entity.getType().isIn(net.minecraft.registry.tag.EntityTypeTags.UNDEAD) && source.getAttacker() instanceof ServerPlayerEntity ap && get(ap).style() == Style.HAMON) {
                    GUARD = true;
                    try {
                        entity.damage(source, amount * (float) StyleConfig.INSTANCE.undeadBonus);
                        entity.setOnFireFor(2);
                        Fx.burst(world(ap), "h_spark", Ops.center(entity), 8, 0.2, 0.3);
                    } finally {
                        GUARD = false;
                    }
                    return false;
                }
                return true;
            }
            PState s = STATES.get(sp.getUuid());
            long now = sp.getWorld().getTime();
            float amt = amount;
            if (source.getAttacker() instanceof ServerPlayerEntity ap && ap != sp && get(ap).style() == Style.HAMON && get(sp).has(Style.VAMPIRE)) {
                amt *= (float) StyleConfig.INSTANCE.undeadBonus;                     // the Hamon burns vampires
                sp.setOnFireFor(4);
            }
            float m = 1f;
            if (s != null && s.rippleUntil > now) {
                m *= 0.5f;
                if (source.getAttacker() instanceof LivingEntity a && a != sp) {              // the ripple bites back
                    GUARD = true;
                    try {
                        Hamon.shock(world(sp), sp, a, 2.5f);
                    } finally {
                        GUARD = false;
                    }
                }
            }
            if (s != null && s.spinAuraUntil > now) m *= source.getSource() instanceof ProjectileEntity ? 0f : 0.6f;
            if (m >= 0.999f && amt == amount) return true;
            if (m <= 0.001f) return false;
            GUARD = true;
            try {
                sp.damage(source, amt * m);
            } finally {
                GUARD = false;
            }
            return false;
        });

        // kills feed mastery
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killed) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return;
            SData d = get(sp);
            PState s = STATES.get(sp.getUuid());
            Style st = d.style();
            if (st == null || s == null || world.getTime() - s.lastCast > 80) return;
            addXp(sp, st, 10);
            d = get(sp).addStat(SData.ST_KILLS + st.ordinal(), 1);
            if (killed.getType().isIn(net.minecraft.registry.tag.EntityTypeTags.UNDEAD)) d = d.addStat(SData.ST_UNDEAD, 1);
            set(sp, d);
            if (st == Style.VAMPIRE) gainBlood(sp, 12);
        });

        registerCommands();
    }

    private static void vampirePassive(ServerPlayerEntity p, PState s, SData d, ServerWorld w, boolean sun) {
        if (StyleConfig.INSTANCE.sunlightBurns && sun && p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            if (!p.isCreative() && !p.isSpectator()) {
                p.setOnFireFor(4);
                Fx.burst(w, "v_mist", p.getPos().add(0, 1, 0), 10, 0.15, 0.5);
                if (!s.sunWarned) {
                    note(p, "message.fightstyles.sun", Formatting.RED);
                    s.sunWarned = true;
                }
            }
        } else {
            s.sunWarned = false;
            boolean dark = !sun;
            if (dark) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
            }
        }
        if (d.energy(Style.VAMPIRE) <= 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, true, false, false));
        }
    }

    public static void gainBlood(ServerPlayerEntity p, int amount) {
        SData d = get(p);
        if (!d.has(Style.VAMPIRE)) return;
        set(p, d.withEnergy(Style.VAMPIRE, d.energy(Style.VAMPIRE) + amount));
        sync(p);
    }

    // ================================================================== casting
    public static void action(ServerPlayerEntity p, int kind, int slot) {
        if (!p.isAlive() || p.isSpectator()) return;
        SData d = get(p);
        if (d.unlocked() == 0) {
            note(p, "message.fightstyles.no_style", Formatting.GRAY);
            return;
        }
        if (kind == 1) {
            for (int i = 1; i <= Style.values().length; i++) {
                Style x = Style.of(((d.active() < 0 ? 0 : d.active()) + i) % Style.values().length);
                if (x != null && d.has(x)) {
                    set(p, d.withActive(x.ordinal()));
                    Snd.play(world(p), p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                    Fx.burst(world(p), x.accent, p.getPos().add(0, 1, 0), 20, 0.25, 0.3);
                    p.sendMessage(Text.translatable("message.fightstyles.switched", x.displayName()).formatted(Formatting.GOLD), true);
                    sync(p);
                    broadcastState(p);
                    return;
                }
            }
            return;
        }
        Style st = d.style();
        if (st == null) return;
        Skill sk = Skill.of(st, slot);
        if (sk != null) fire(p, sk);
    }

    private static int costOf(Skill sk) {
        return StyleConfig.cost(sk.cost);
    }

    private static void fire(ServerPlayerEntity p, Skill sk) {
        SData d = get(p);
        PState s = st(p);
        ServerWorld w = world(p);
        long now = w.getTime();
        Style st = sk.style;
        if (!d.has(st)) return;
        if (d.rank(st) < sk.minRank) {
            p.sendMessage(Text.translatable("message.fightstyles.rank_needed", Text.translatable("rank.fightstyles." + sk.minRank)).formatted(Formatting.RED), true);
            return;
        }
        if (st == Style.SPIN && StyleConfig.INSTANCE.spinNeedsBall && !hasBall(p)) {
            note(p, "message.fightstyles.no_ball", Formatting.RED);
            return;
        }
        if (now < s.cd[sk.ordinal()]) {
            note(p, "message.fightstyles.cooldown", Formatting.GRAY);
            return;
        }
        int cost = costOf(sk);
        if (d.energy(st) < cost) {
            note(p, "message.fightstyles." + (st == Style.VAMPIRE ? "no_blood" : "energy"), Formatting.RED);
            return;
        }
        int chain = (now - s.lastCast <= 40 && s.combo > 0) ? s.combo + 1 : 1;
        float mult = damageMult(d, st) * (1f + 0.07f * Math.min(chain - 1, 6));
        if (s.sustained != null) {
            s.sustained.dead = true;
            s.sustained = null;
        }
        Cast c = new Cast(w, p, mult, 0f, false);
        boolean ok;
        try {
            ok = dispatch(sk, c, s);
        } catch (RuntimeException e) {
            FightStyles.LOG.error("technique " + sk + " failed", e);
            ok = false;
        }
        if (!ok) {
            note(p, "message.fightstyles.fail", Formatting.GRAY);
            return;
        }
        d = get(p);                                                        // techniques may have changed the data (blood, breath)
        d = d.withEnergy(st, d.energy(st) - cost).addStat(SData.ST_CASTS, 1);
        set(p, d);
        s.cd[sk.ordinal()] = now + Math.max(1, (long) StyleConfig.cooldown(sk.cooldown));
        s.combo = chain;
        s.lastCast = now;
        if (chain >= 3) p.sendMessage(Text.translatable("message.fightstyles.combo", chain).formatted(Formatting.GOLD), true);
        if (sk.pose > 0) broadcastPose(p, sk.pose, sk.poseTicks);
        addXp(p, st, 2 + sk.cost / 8);
        sync(p);
    }

    private static boolean dispatch(Skill sk, Cast c, PState s) {
        return switch (sk) {
            case HAMON_PUNCH -> Hamon.punch(c);
            case DEEP_BREATH -> Hamon.breath(c);
            case SUN_PULSE -> Hamon.pulse(c);
            case RIPPLE_SHIELD -> Hamon.ripple(c, s);
            case ZOOM_PUNCH -> Hamon.zoomPunch(c);
            case SUN_DISC -> Hamon.disc(c);
            case SUN_FLARE -> Hamon.flare(c);
            case STEEL_THROW -> Spin.throwBall(c);
            case BALL_FAN -> Spin.fan(c);
            case SPIN_AURA -> Spin.aura(c, s);
            case SPIN_SLASH -> Spin.slash(c);
            case SPIN_DASH -> Spin.dash(c);
            case STEEL_RAIN -> Spin.rain(c);
            case INFINITE_SPIN -> Spin.infinite(c);
            case BLOOD_BITE -> Vampire.bite(c);
            case BLOOD_LASH -> Vampire.lash(c);
            case BAT_FLIGHT -> Vampire.bats(c);
            case EYE_JETS -> Vampire.eyes(c);
            case BLOOD_FRENZY -> Vampire.frenzy(c, s);
            case FREEZE_TOUCH -> Vampire.freeze(c);
            case NIGHT_DOMINION -> Vampire.night(c);
        };
    }

    // ================================================================== progression
    public static void addXp(ServerPlayerEntity p, Style st, int n) {
        SData d = get(p);
        int before = d.rank(st);
        d = d.withXp(st, d.xp(st) + n);
        set(p, d);
        int after = d.rank(st);
        if (after > before) {
            ServerWorld w = world(p);
            p.sendMessage(Text.translatable("message.fightstyles.rank_up", st.displayName(), Text.translatable("rank.fightstyles." + after)).formatted(Formatting.GOLD, Formatting.BOLD), false);
            Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            Fx.burst(w, st.small, p.getPos().add(0, 1, 0), 40, 0.35, 0.4);
            Ops.ring(w, p.getPos().add(0, 0.1, 0), 3, 10, st);
        }
    }

    /** Unlocks a style, makes it the active one and celebrates. */
    public static void unlock(ServerPlayerEntity p, Style st) {
        SData d = get(p);
        d = d.withUnlocked(d.unlocked() | st.bit()).withActive(st.ordinal());
        d = d.withEnergy(st, d.maxEnergy(st));
        set(p, d);
        ServerWorld w = world(p);
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("message.fightstyles.unlocked_sub." + st.name().toLowerCase()).formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(st.displayName().copy().formatted(Formatting.BOLD).styled(x -> x.withColor(st.color))));
        Snd.play(w, p.getPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        Fx.burst(w, st.small, p.getPos().add(0, 1, 0), 60, 0.4, 0.4);
        Fx.burst(w, st.big, p.getPos().add(0, 1, 0), 6, 0.2, 0.3);
        Ops.ring(w, p.getPos().add(0, 0.1, 0), 4, 12, st);
        Ops.shake(w, p.getPos(), 10, 3, 8);
        sync(p);
        broadcastState(p);
    }

    public static boolean useMask(ServerPlayerEntity p) {
        SData d = get(p);
        if (d.has(Style.HAMON)) {
            note(p, "message.fightstyles.mask_rejects", Formatting.RED);
            return false;
        }
        if (d.has(Style.VAMPIRE)) {
            note(p, "message.fightstyles.already", Formatting.GRAY);
            return false;
        }
        ServerWorld w = world(p);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
        for (int i = 0; i < 6; i++) Fx.vel(w, "v_drop", p.getEyePos().add(Fx.jitter(0.2)), new Vec3d(R.nextGaussian() * 0.1, 0.05, R.nextGaussian() * 0.1));
        unlock(p, Style.VAMPIRE);
        return true;
    }

    public static boolean useManual(ServerPlayerEntity p) {
        SData d = get(p);
        if (d.has(Style.VAMPIRE)) {
            note(p, "message.fightstyles.hamon_rejects", Formatting.RED);
            return false;
        }
        if (d.has(Style.HAMON)) {
            addXp(p, Style.HAMON, 100);
            note(p, "message.fightstyles.trained", Formatting.GOLD);
            Snd.play(world(p), p.getPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            sync(p);
            return true;
        }
        unlock(p, Style.HAMON);
        return true;
    }

    public static boolean useBall(ServerPlayerEntity p) {
        SData d = get(p);
        if (d.has(Style.SPIN)) return false;
        unlock(p, Style.SPIN);
        return true;
    }

    // ================================================================== syncing
    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, SyncPayload.ID)) return;
        SData d = get(p);
        PState s = st(p);
        long now = world(p).getTime();
        int[] cd = new int[Skill.values().length];
        for (int i = 0; i < cd.length; i++) cd[i] = (int) Math.max(0, s.cd[i] - now);
        Style st = d.style();
        int energy = st == null ? 0 : d.energy(st), max = st == null ? 100 : d.maxEnergy(st);
        ServerPlayNetworking.send(p, new SyncPayload(d.unlocked(), d.active(), energy, max, hasBall(p), d.xp(), cd, d.stats()));
    }

    public static void broadcastState(ServerPlayerEntity p) {
        SData d = get(p);
        StatePayload pl = new StatePayload(p.getId(), d.unlocked(), d.active());
        if (ServerPlayNetworking.canSend(p, StatePayload.ID)) ServerPlayNetworking.send(p, pl);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) if (ServerPlayNetworking.canSend(o, StatePayload.ID)) ServerPlayNetworking.send(o, pl);
    }

    public static void broadcastPose(ServerPlayerEntity p, int pose, int ticks) {
        if (pose <= 0) return;
        PosePayload pl = new PosePayload(p.getId(), pose, ticks);
        if (ServerPlayNetworking.canSend(p, PosePayload.ID)) ServerPlayNetworking.send(p, pl);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) if (ServerPlayNetworking.canSend(o, PosePayload.ID)) ServerPlayNetworking.send(o, pl);
    }

    // ================================================================== commands
    private static Style parse(String n) {
        return switch (n.toLowerCase()) {
            case "hamon" -> Style.HAMON;
            case "spin", "rotacao", "rotação" -> Style.SPIN;
            case "vampire", "vampiro", "vampirismo" -> Style.VAMPIRE;
            default -> null;
        };
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("styles")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("give").then(CommandManager.argument("style", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            Style st = parse(StringArgumentType.getString(ctx, "style"));
                            if (st == null) return 0;
                            unlock(p, st);
                            return 1;
                        })))
                        .then(CommandManager.literal("rank").then(CommandManager.argument("n", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            SData d = get(p);
                            Style st = d.style();
                            if (st == null) return 0;
                            d = d.withXp(st, SData.RANK_XP[IntegerArgumentType.getInteger(ctx, "n")]);
                            set(p, d.withEnergy(st, d.maxEnergy(st)));
                            sync(p);
                            return 1;
                        })))
                        .then(CommandManager.literal("master").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            SData d = get(p);
                            if (d.unlocked() == 0) d = d.withUnlocked(Style.HAMON.bit()).withActive(0);
                            for (Style x : Style.values()) if (d.has(x)) d = d.withXp(x, SData.RANK_XP[5]);
                            for (Style x : Style.values()) if (d.has(x)) d = d.withEnergy(x, d.maxEnergy(x));
                            set(p, d);
                            sync(p);
                            broadcastState(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("energy").then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            SData d = get(p);
                            Style st = d.style();
                            if (st == null) return 0;
                            set(p, d.withEnergy(st, IntegerArgumentType.getInteger(ctx, "amount")));
                            sync(p);
                            return 1;
                        })))
                        .then(CommandManager.literal("reset").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            set(p, SData.DEFAULT);
                            STATES.remove(p.getUuid());
                            sync(p);
                            broadcastState(p);
                            return 1;
                        }))));
    }
}
