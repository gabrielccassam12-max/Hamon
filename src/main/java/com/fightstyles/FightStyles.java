package com.fightstyles;

import com.fightstyles.core.StyleConfig;
import com.fightstyles.item.ModItems;
import com.fightstyles.net.ActionPayload;
import com.fightstyles.net.PosePayload;
import com.fightstyles.net.ShakePayload;
import com.fightstyles.net.StatePayload;
import com.fightstyles.net.SyncPayload;
import com.fightstyles.particle.ModParticles;
import com.fightstyles.server.Fighter;
import com.fightstyles.util.Effects;
import com.fightstyles.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FightStyles implements ModInitializer {
    public static final String ID = "fightstyles";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        StyleConfig.load();
        ModParticles.init();
        ModItems.init();
        Effects.init();
        Scheduler.init();
        Fighter.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PosePayload.ID, PosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShakePayload.ID, ShakePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) ->
                Fighter.action(context.player(), payload.kind(), payload.slot()));
        LOG.info("Fight Styles: Hamon, Spin and Vampirism are awake.");
    }
}
