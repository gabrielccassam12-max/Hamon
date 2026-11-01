package com.fightstyles.client;

import com.fightstyles.FightStyles;
import com.fightstyles.entity.Rival;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** The rival is drawn with the player model and one of four generated skins. */
public class RivalRenderer extends BipedEntityRenderer<Rival, PlayerEntityModel<Rival>> {
    private static final Identifier[] SKINS = {
            FightStyles.id("textures/entity/rival_0.png"), FightStyles.id("textures/entity/rival_1.png"),
            FightStyles.id("textures/entity/rival_2.png"), FightStyles.id("textures/entity/rival_3.png")};

    public RivalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(Rival entity) {
        return SKINS[Math.max(0, Math.min(3, entity.variant()))];
    }
}
