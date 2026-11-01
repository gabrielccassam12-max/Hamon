package com.fightstyles.client;

import com.fightstyles.core.Style;
import com.fightstyles.net.ActionPayload;
import com.fightstyles.net.PosePayload;
import com.fightstyles.net.ShakePayload;
import com.fightstyles.net.StatePayload;
import com.fightstyles.net.SyncPayload;
import com.fightstyles.particle.CubeProfile;
import com.fightstyles.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class FightClient implements ClientModInitializer {
    private static final String CAT = "key.categories.fightstyles";
    /** The seven techniques of the style in use. */
    public static final KeyBinding[] SLOT = new KeyBinding[7];
    public static KeyBinding SWITCH, MENU;
    private static final boolean[] down = new boolean[7];

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fightstyles." + name, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        for (Map.Entry<String, SimpleParticleType> en : ModParticles.TYPES.entrySet()) {
            CubeProfile pf = ModParticles.PROFILES.get(en.getKey());
            ParticleFactoryRegistry.getInstance().register(en.getValue(), sprites ->
                    (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz) ->
                            new CubeParticle(world, x, y, z, vx, vy, vz, sprites, pf));
        }

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer r) helper.register(new StyleFeature(r));
        });

        int[] codes = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M};
        for (int i = 0; i < SLOT.length; i++) SLOT[i] = key("slot" + (i + 1), codes[i]);
        SWITCH = key("switch", GLFW.GLFW_KEY_Y);
        MENU = key("menu", GLFW.GLFW_KEY_U);

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> ClientState.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(PosePayload.ID, (payload, context) -> {
            ClientState.startPose(payload.entityId(), payload.pose(), payload.ticks());
            MinecraftClient mc = context.client();
            if (mc.player != null && payload.entityId() == mc.player.getId()) {
                Style st = currentStyle();
                ClientState.cast(payload.pose(), payload.ticks(), st == null ? 0xFFFFFF : st.color);
                handBurst(mc, st, 14);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, context) ->
                ClientState.setState(payload.entityId(), payload.mask(), payload.active()));
        ClientPlayNetworking.registerGlobalReceiver(ShakePayload.ID, (payload, context) ->
                ClientState.shake(payload.strength(), payload.ticks()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.tick(client);
            if (client.player == null) return;
            boolean free = client.currentScreen == null;
            for (int i = 0; i < SLOT.length; i++) {
                boolean now = free && SLOT[i].isPressed();
                if (now && !down[i]) ClientPlayNetworking.send(new ActionPayload(0, i));
                down[i] = now;
            }
            while (SWITCH.wasPressed()) if (free) ClientPlayNetworking.send(new ActionPayload(1, 0));
            while (MENU.wasPressed()) if (free) client.setScreen(new MenuScreen());
            for (KeyBinding k : SLOT) while (k.wasPressed()) { }
        });

        HudRenderCallback.EVENT.register(FightHud::render);
    }

    /** The style whose techniques are on the keys right now. */
    public static Style currentStyle() {
        SyncPayload s = ClientState.last;
        return s == null ? null : Style.of(s.active());
    }

    private static Vec3d handPos(MinecraftClient mc) {
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : right.normalize();
        return mc.player.getEyePos().add(look.multiply(0.8)).add(right.multiply(0.35)).add(0, -0.35, 0);
    }

    /** A puff of cubes leaving the hand when a technique is cast (first person feel). */
    private static void handBurst(MinecraftClient mc, Style st, int n) {
        if (st == null || mc.world == null || mc.player == null) return;
        if (!com.fightstyles.core.StyleConfig.INSTANCE.firstPersonEffects) return;
        SimpleParticleType t = ModParticles.get(st.small), t2 = ModParticles.get(st.accent);
        Vec3d hand = handPos(mc), look = mc.player.getRotationVec(1f);
        for (int i = 0; i < n; i++) {
            SimpleParticleType pt = i % 3 == 0 && t2 != null ? t2 : t;
            if (pt == null) continue;
            Vec3d v = look.multiply(0.12 + Math.random() * 0.12).add((Math.random() - 0.5) * 0.08, (Math.random() - 0.5) * 0.08, (Math.random() - 0.5) * 0.08);
            mc.world.addParticle(pt, hand.x, hand.y, hand.z, v.x, v.y, v.z);
        }
    }
}
