package com.fightstyles.particle;

import com.fightstyles.FightStyles;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's 3D cube particles, one palette per style: young -> middle -> old.
 * Names match assets/fightstyles/particles/<name>.json. h_ Hamon (sunlight), sp_ Spin, v_ Vampire, st_ steel.
 */
public final class ModParticles {
    private ModParticles() {}

    public static final Map<String, CubeProfile> PROFILES = new LinkedHashMap<>();
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    static {
        //                               young     middle    old       size   var    life var  grav    drag   shrink spin  flat  alpha inner
        // ---- hamon: white -> sunlight yellow -> orange
        PROFILES.put("h_spark", new CubeProfile(0xFFFFFF, 0xFFE46A, 0xFF9A1F, 0.17f, 0.07f, 14, 5, 0.0f,    0.90f, 0.955f, 18f, 1f,    0.90f, true));
        PROFILES.put("h_big",   new CubeProfile(0xFFFFF0, 0xFFD84A, 0xF08A10, 0.52f, 0.20f, 11, 4, 0.0f,    0.88f, 0.930f, 8f,  1f,    0.55f, true));
        PROFILES.put("h_ray",   new CubeProfile(0xFFFFFF, 0xFFF3A0, 0xFFC93C, 0.13f, 0.03f, 8,  2, 0.0f,    0.97f, 0.930f, 0f,  0.35f, 0.95f, true));
        PROFILES.put("h_ring",  new CubeProfile(0xFFF6C8, 0xFFD24A, 0xE8901C, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        // ---- spin: white -> mint -> emerald
        PROFILES.put("sp_spark",new CubeProfile(0xFFFFFF, 0xA8FFD8, 0x2CC98A, 0.15f, 0.06f, 14, 5, 0.0f,    0.92f, 0.960f, 22f, 1f,    0.90f, true));
        PROFILES.put("sp_big",  new CubeProfile(0xF0FFF8, 0x6CF0B8, 0x20A870, 0.48f, 0.18f, 11, 4, 0.0f,    0.88f, 0.935f, 8f,  1f,    0.50f, true));
        PROFILES.put("sp_swirl",new CubeProfile(0xFFFFF0, 0xDFFF9A, 0x3FD69A, 0.12f, 0.03f, 8,  2, 0.0f,    0.96f, 0.930f, 0f,  0.35f, 0.95f, true));
        PROFILES.put("sp_ring", new CubeProfile(0xE8FFF4, 0x7CF5C0, 0x2CC08A, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        PROFILES.put("st_steel",new CubeProfile(0xFFFFFF, 0xD4DAE2, 0x7C8794, 0.15f, 0.05f, 16, 5, 0.006f,  0.96f, 0.965f, 40f, 1f,    1.00f, false));
        // ---- vampire: blood red, dark mist, bats
        PROFILES.put("v_drop",  new CubeProfile(0xFF8A8A, 0xC4102E, 0x4A0612, 0.16f, 0.06f, 18, 6, 0.010f,  0.95f, 0.970f, 14f, 1f,    0.90f, true));
        PROFILES.put("v_big",   new CubeProfile(0xE04A5A, 0x8A0A22, 0x30040C, 0.52f, 0.20f, 13, 4, 0.0f,    0.90f, 0.945f, 6f,  1f,    0.55f, true));
        PROFILES.put("v_mist",  new CubeProfile(0x5A1428, 0x34101C, 0x180810, 0.38f, 0.16f, 28, 10, -0.002f, 0.94f, 1.008f, 5f,  1f,    0.45f, false));
        PROFILES.put("v_ring",  new CubeProfile(0xFF9AA8, 0xD01430, 0x5A0818, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        PROFILES.put("v_frost", new CubeProfile(0xFFFFFF, 0xCDEBFF, 0x7EB8E8, 0.16f, 0.06f, 22, 8, 0.002f,  0.95f, 0.975f, 12f, 1f,    0.90f, true));
        PROFILES.put("v_bat",   new CubeProfile(0x4A3A5C, 0x241830, 0x0C0614, 0.22f, 0.08f, 22, 8, 0.0f,    0.93f, 0.975f, 30f, 1f,    1.00f, false));
    }

    public static SimpleParticleType get(String name) {
        return TYPES.get(name);
    }

    public static void init() {
        for (String name : PROFILES.keySet()) {
            SimpleParticleType t = FabricParticleTypes.simple(true);
            Registry.register(Registries.PARTICLE_TYPE, FightStyles.id(name), t);
            TYPES.put(name, t);
        }
    }
}
