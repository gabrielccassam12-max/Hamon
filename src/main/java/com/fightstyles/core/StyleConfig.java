package com.fightstyles.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/fightstyles.json - balance and performance. Read once at startup. */
public final class StyleConfig {
    public double costMultiplier = 1.0;
    public double cooldownMultiplier = 1.0;
    public double damageMultiplier = 1.0;
    public double energyRegenMultiplier = 1.0;
    public boolean allowPvp = true;
    /** Vampires burn in direct sunlight (no roof, no helmet, no rain, not in water). */
    public boolean sunlightBurns = true;
    /** Hamon deals extra damage to undead and vampires. */
    public double undeadBonus = 2.0;
    /** Spin techniques need at least one steel ball in the inventory. */
    public boolean spinNeedsBall = true;
    public double particleDensity = 1.0;
    public boolean cameraShake = true;
    /** Field of view pulses, colour flash and hand particles in first person. */
    public boolean firstPersonEffects = true;

    public static StyleConfig INSTANCE = new StyleConfig();
    public static double DENSITY = 1.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("fightstyles.json");
        try {
            if (Files.exists(p)) {
                StyleConfig c = GSON.fromJson(Files.readString(p), StyleConfig.class);
                if (c != null) INSTANCE = c;
            }
            INSTANCE.particleDensity = Math.max(0.1, Math.min(3.0, INSTANCE.particleDensity));
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            e.printStackTrace();
        }
        DENSITY = INSTANCE.particleDensity;
    }

    public static int cost(int base) {
        return Math.max(0, (int) Math.round(base * INSTANCE.costMultiplier));
    }

    public static int cooldown(int base) {
        return Math.max(1, (int) Math.round(base * INSTANCE.cooldownMultiplier));
    }
}
