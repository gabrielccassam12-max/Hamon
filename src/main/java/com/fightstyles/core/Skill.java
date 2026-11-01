package com.fightstyles.core;

import net.minecraft.text.Text;

/**
 * Every technique, 7 per style (key slots 0..6).
 * pose: 1 one arm forward, 2 both arms forward, 3 both raised, 4 arms wide, 5 kick, 6 hands pushing down.
 */
public enum Skill {
    // ---- HAMON
    HAMON_PUNCH(Style.HAMON, 0, 0, 8, 20, 1, 8),
    DEEP_BREATH(Style.HAMON, 1, 0, 0, 200, 4, 30),
    SUN_PULSE(Style.HAMON, 2, 1, 18, 80, 4, 12),
    RIPPLE_SHIELD(Style.HAMON, 3, 1, 22, 220, 4, 14),
    ZOOM_PUNCH(Style.HAMON, 4, 2, 24, 90, 1, 10),
    SUN_DISC(Style.HAMON, 5, 3, 32, 160, 1, 12),
    SUN_FLARE(Style.HAMON, 6, 4, 60, 360, 3, 40),
    // ---- SPIN
    STEEL_THROW(Style.SPIN, 0, 0, 8, 18, 1, 8),
    BALL_FAN(Style.SPIN, 1, 1, 20, 70, 2, 10),
    SPIN_AURA(Style.SPIN, 2, 1, 22, 220, 4, 14),
    SPIN_SLASH(Style.SPIN, 3, 2, 20, 60, 1, 10),
    SPIN_DASH(Style.SPIN, 4, 2, 26, 90, 2, 8),
    STEEL_RAIN(Style.SPIN, 5, 3, 36, 180, 3, 24),
    INFINITE_SPIN(Style.SPIN, 6, 4, 60, 340, 1, 30),
    // ---- VAMPIRE
    BLOOD_BITE(Style.VAMPIRE, 0, 0, 0, 30, 1, 8),
    BLOOD_LASH(Style.VAMPIRE, 1, 0, 10, 40, 1, 10),
    BAT_FLIGHT(Style.VAMPIRE, 2, 1, 20, 100, 2, 10),
    EYE_JETS(Style.VAMPIRE, 3, 2, 26, 100, 2, 10),
    BLOOD_FRENZY(Style.VAMPIRE, 4, 2, 30, 260, 4, 14),
    FREEZE_TOUCH(Style.VAMPIRE, 5, 3, 34, 160, 6, 14),
    NIGHT_DOMINION(Style.VAMPIRE, 6, 4, 60, 360, 3, 40);

    public final Style style;
    public final int slot, minRank, cost, cooldown, pose, poseTicks;

    Skill(Style style, int slot, int minRank, int cost, int cooldown, int pose, int poseTicks) {
        this.style = style;
        this.slot = slot;
        this.minRank = minRank;
        this.cost = cost;
        this.cooldown = cooldown;
        this.pose = pose;
        this.poseTicks = poseTicks;
    }

    public Text displayName() {
        return Text.translatable("skill.fightstyles." + name().toLowerCase());
    }

    public static Skill of(Style s, int slot) {
        if (slot < 0 || slot > 6) return null;
        for (Skill k : values()) if (k.style == s && k.slot == slot) return k;
        return null;
    }
}
