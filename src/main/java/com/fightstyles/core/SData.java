package com.fightstyles.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved with the player and kept after death.
 * unlocked: bit per Style. active: Style ordinal in use (-1 none). xp / energy: one entry per style.
 * stats: 0 techniques used, 1..3 defeats per style, 4 bites, 5 undead defeated.
 */
public record SData(int unlocked, int active, List<Integer> xp, List<Integer> energy, List<Integer> stats) {
    public static final int ST_CASTS = 0, ST_KILLS = 1, ST_BITES = 4, ST_UNDEAD = 5, STATS = 6;
    public static final int[] RANK_XP = {0, 120, 400, 1000, 2200, 4000};

    public static final SData DEFAULT = new SData(0, -1, blank(3), blank(3), blank(STATS));

    public static final Codec<SData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("unlocked").forGetter(SData::unlocked),
            Codec.INT.fieldOf("active").forGetter(SData::active),
            Codec.INT.listOf().fieldOf("xp").forGetter(SData::xp),
            Codec.INT.listOf().fieldOf("energy").forGetter(SData::energy),
            Codec.INT.listOf().fieldOf("stats").forGetter(SData::stats)
    ).apply(i, SData::new));

    public static List<Integer> blank(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(0);
        return l;
    }

    private static int at(List<Integer> l, int i) {
        return i >= 0 && i < l.size() ? l.get(i) : 0;
    }

    private static List<Integer> put(List<Integer> l, int i, int v, int size) {
        List<Integer> n = new ArrayList<>(l);
        while (n.size() < size) n.add(0);
        n.set(i, v);
        return n;
    }

    public static int rankOf(int xp) {
        int r = 0;
        for (int i = 0; i < RANK_XP.length; i++) if (xp >= RANK_XP[i]) r = i;
        return r;
    }

    public boolean has(Style s) {
        return (unlocked & s.bit()) != 0;
    }

    public Style style() {
        return Style.of(active);
    }

    public int xp(Style s) {
        return at(xp, s.ordinal());
    }

    public int rank(Style s) {
        return rankOf(xp(s));
    }

    public int energy(Style s) {
        return at(energy, s.ordinal());
    }

    public int maxEnergy(Style s) {
        return 100 + 20 * rank(s);
    }

    public int stat(int i) {
        return at(stats, i);
    }

    public SData withUnlocked(int v) { return new SData(v, active, xp, energy, stats); }
    public SData withActive(int v) { return new SData(unlocked, v, xp, energy, stats); }
    public SData withXp(Style s, int v) { return new SData(unlocked, active, put(xp, s.ordinal(), v, 3), energy, stats); }
    public SData withEnergy(Style s, int v) {
        return new SData(unlocked, active, xp, put(energy, s.ordinal(), Math.max(0, Math.min(maxEnergy(s), v)), 3), stats);
    }
    public SData addStat(int i, int n) { return new SData(unlocked, active, xp, energy, put(stats, i, at(stats, i) + n, STATS)); }
}
