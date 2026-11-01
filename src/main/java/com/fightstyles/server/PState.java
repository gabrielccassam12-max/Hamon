package com.fightstyles.server;

import com.fightstyles.core.Skill;
import com.fightstyles.util.Effects;

/** Per-player runtime state (not saved). */
public final class PState {
    public final long[] cd = new long[Skill.values().length];
    public long rippleUntil, spinAuraUntil, frenzyUntil;
    public Effects.Handle sustained;
    public int combo;
    public long lastCast;
    public int lastHash = Integer.MIN_VALUE;
    public boolean sunWarned;
}
