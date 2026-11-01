package com.fightstyles.entity;

import com.fightstyles.FightStyles;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<Rival> RIVAL = Registry.register(Registries.ENTITY_TYPE, FightStyles.id("rival"),
            EntityType.Builder.create(Rival::new, SpawnGroup.MONSTER).dimensions(0.6f, 1.8f).maxTrackingRange(8).build("rival"));

    public static void init() {
        FabricDefaultAttributeRegistry.register(RIVAL, Rival.attributes());
        SpawnRestriction.register(RIVAL, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, RIVAL, 3, 1, 1);
    }
}
