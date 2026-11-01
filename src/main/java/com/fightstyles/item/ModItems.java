package com.fightstyles.item;

import com.fightstyles.FightStyles;
import com.fightstyles.server.Fighter;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.function.Predicate;

public final class ModItems {
    private ModItems() {}

    /** An item that does something to the player on right click (server side) and is used up when it works. */
    private static final class Trigger extends Item {
        private final Predicate<ServerPlayerEntity> action;
        private final boolean consume;

        Trigger(Settings settings, Predicate<ServerPlayerEntity> action, boolean consume) {
            super(settings);
            this.action = action;
            this.consume = consume;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack);
            if (action.test(sp)) {
                if (consume && !sp.isCreative()) stack.decrement(1);
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.fail(stack);
        }
    }

    /** Stone Mask: becomes a vampire. */
    public static final Item STONE_MASK = Registry.register(Registries.ITEM, FightStyles.id("stone_mask"),
            new Trigger(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), Fighter::useMask, true));
    /** Breathing manual: teaches the Hamon (and trains it afterwards). */
    public static final Item HAMON_MANUAL = Registry.register(Registries.ITEM, FightStyles.id("hamon_manual"),
            new Trigger(new Item.Settings().maxCount(16).rarity(Rarity.RARE), Fighter::useManual, true));
    /** Steel ball: right click once to learn the Spin. Spin techniques need one in the inventory. */
    public static final Item STEEL_BALL = Registry.register(Registries.ITEM, FightStyles.id("steel_ball"),
            new Trigger(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON), Fighter::useBall, false));

    /** Caesar's gloves: learn the Bubble Hamon. */
    public static final Item CAESAR_GLOVES = Registry.register(Registries.ITEM, FightStyles.id("caesar_gloves"),
            new Trigger(new Item.Settings().maxCount(16).rarity(Rarity.RARE), Fighter::useGloves, true));
    /** Red Stone of Aja: becomes the Ultimate Being. */
    public static final Item AJA_STONE = Registry.register(Registries.ITEM, FightStyles.id("aja_stone"),
            new Trigger(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), Fighter::useAja, true));
    /** Spawns a rival fighter (Hamon, Spin, vampire or Kars). */
    public static final Item RIVAL_EGG = Registry.register(Registries.ITEM, FightStyles.id("rival_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(com.fightstyles.entity.ModEntities.RIVAL, 0xFFC93C, 0x2A1A3A, new Item.Settings()));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(RIVAL_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(STONE_MASK);
            e.add(HAMON_MANUAL);
            e.add(STEEL_BALL);
            e.add(CAESAR_GLOVES);
            e.add(AJA_STONE);
        });
    }
}
