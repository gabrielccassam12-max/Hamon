package com.fightstyles.core;

import net.minecraft.text.Text;

/** The three fighting styles. color = HUD / particle colour; ring, small, big, accent = particle names. */
public enum Style {
    HAMON(0xFFC93C, "h_ring", "h_spark", "h_big", "h_ray"),
    SPIN(0x4DF0A8, "sp_ring", "sp_spark", "sp_big", "sp_swirl"),
    VAMPIRE(0xD0102E, "v_ring", "v_drop", "v_big", "v_mist"),
    BUBBLE(0x6FD6FF, "b_ring", "b_spark", "b_big", "b_irid"),
    KARS(0xB58CFF, "k_ring", "k_spark", "k_big", "k_light");

    public final int color;
    public final String ring, small, big, accent;

    Style(int color, String ring, String small, String big, String accent) {
        this.color = color;
        this.ring = ring;
        this.small = small;
        this.big = big;
        this.accent = accent;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static Style of(int i) {
        Style[] v = values();
        return i < 0 || i >= v.length ? null : v[i];
    }

    public Text displayName() {
        return Text.translatable("style.fightstyles." + name().toLowerCase());
    }

    /** What the energy bar is called for this style. */
    public Text energyName() {
        return Text.translatable("energy.fightstyles." + name().toLowerCase());
    }
}
