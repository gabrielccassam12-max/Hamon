package com.fightstyles.client;

import com.fightstyles.FightStyles;
import com.fightstyles.core.Style;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/** Vampires get glowing red eyes and fangs; the Hamon glows golden in the hands, the Spin green. */
public class StyleFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier WHITE = FightStyles.id("textures/misc/white.png");

    public StyleFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ClientState.Vis v = ClientState.VIS.get(entity.getId());
        if (v == null || v.mask == 0 || entity.isInvisible()) return;
        Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        PlayerEntityModel<AbstractClientPlayerEntity> m = getContextModel();
        float age = entity.age + tickDelta;
        int pulse = (int) (200 + 55 * Math.sin(age * 0.2));

        if ((v.mask & Style.VAMPIRE.bit()) != 0) {
            matrices.push();
            m.head.rotate(matrices);
            MatrixStack.Entry e = matrices.peek();
            for (int side = -1; side <= 1; side += 2) {
                float x0 = side > 0 ? 1.0f : -3.2f, x1 = side > 0 ? 3.2f : -1.0f;
                Boxes.box(e, vc, x0, -4.7f, -4.12f, x1, -3.1f, -4.0f, 0xFF1A2A, 255, Boxes.FULL_LIGHT);
                Boxes.box(e, vc, x0 - 0.5f, -5.2f, -4.3f, x1 + 0.5f, -2.6f, -4.12f, 0xC4102E, 90, Boxes.FULL_LIGHT);
            }
            Boxes.box(e, vc, -1.6f, -1.9f, -4.1f, -0.8f, -0.4f, -4.0f, 0xFFFFFF, 255, Boxes.FULL_LIGHT);   // fangs
            Boxes.box(e, vc, 0.8f, -1.9f, -4.1f, 1.6f, -0.4f, -4.0f, 0xFFFFFF, 255, Boxes.FULL_LIGHT);
            matrices.pop();
        }
        Style act = Style.of(v.active);
        if (act == Style.HAMON || act == Style.SPIN) {
            int col = act.color;
            for (int side = 0; side < 2; side++) {
                ModelPart arm = side == 0 ? m.rightArm : m.leftArm;
                float cx = side == 0 ? -1f : 1f;
                matrices.push();
                arm.rotate(matrices);
                MatrixStack.Entry a = matrices.peek();
                Boxes.box(a, vc, cx - 2.3f, 8.6f, -2.3f, cx + 2.3f, 12.4f, 2.3f, col, pulse / 3, Boxes.FULL_LIGHT);
                Boxes.box(a, vc, cx - 1.6f, 9.2f, -1.7f, cx + 1.6f, 11.8f, 1.7f, 0xFFFFFF, pulse / 2, Boxes.FULL_LIGHT);
                matrices.pop();
            }
        }
    }
}
