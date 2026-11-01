package com.fightstyles.client;

import com.fightstyles.core.SData;
import com.fightstyles.core.Skill;
import com.fightstyles.core.Style;
import com.fightstyles.net.SyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Style name, rank, energy bar, mastery bar and the seven techniques with their cooldowns. */
public final class FightHud {
    private FightHud() {}

    private static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext g, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SyncPayload s = ClientState.last;
        if (mc.player == null || mc.options.hudHidden || s == null) return;
        Style st = Style.of(s.active());
        if (st == null) return;
        long t = ClientState.time();
        int col = st.color;
        int sw = g.getScaledWindowWidth(), sh = g.getScaledWindowHeight();

        if (ClientState.flash > 0.02f) {                              // colour flash at the screen edges when a technique fires
            int a = (int) (ClientState.flash * 150), edge = 36 + (int) (ClientState.flash * 40);
            g.fillGradient(0, 0, sw, edge, argb(ClientState.flashColor, a), argb(ClientState.flashColor, 0));
            g.fillGradient(0, sh - edge, sw, sh, argb(ClientState.flashColor, 0), argb(ClientState.flashColor, a));
        }

        ClientState.shownEnergy += (s.energy() - ClientState.shownEnergy) * 0.2f;
        float pct = Math.max(0f, Math.min(1f, ClientState.shownEnergy / Math.max(1, s.max())));
        int xp = st.ordinal() < s.xp().size() ? s.xp().get(st.ordinal()) : 0;
        int rank = SData.rankOf(xp);

        int w = 138, x = sw - w - 12, rows = 7;
        int y = sh - 30 - rows * 12 - 56;
        Text head = Text.empty().append(st.displayName()).append(" · ").append(Text.translatable("rank.fightstyles." + rank));
        g.drawTextWithShadow(mc.textRenderer, head, x, y, argb(col, 255));

        // other unlocked styles as small chips
        int cx = x;
        for (Style o : Style.values()) {
            if ((s.unlocked() & o.bit()) == 0) continue;
            int cw = 14;
            g.fill(cx, y - 11, cx + cw, y - 5, argb(o.color, o == st ? 0xFF : 0x50));
            cx += cw + 3;
        }

        int by = y + 12;
        g.fill(x - 3, by - 3, x + w + 3, by + 11, argb(0x0B0F14, 0xB4));
        g.drawBorder(x - 3, by - 3, w + 6, 14, argb(col, 0xFF));
        int fw = (int) (w * pct);
        if (fw > 0) {
            g.fillGradient(x, by, x + fw, by + 4, argb(0xFFFFFF, 0xE0), argb(col, 0xFF));
            g.fillGradient(x, by + 4, x + fw, by + 8, argb(col, 0xFF), argb(col, 0x80));
            int gx = x + (int) ((t * 2) % Math.max(1, fw));
            g.fill(gx, by, Math.min(gx + 3, x + fw), by + 8, argb(0xFFFFFF, 0x50));
        }
        String en = s.energy() + "/" + s.max();
        g.drawTextWithShadow(mc.textRenderer, st.energyName(), x, by - 12 + 0, 0xFFB8C4D0);
        g.drawTextWithShadow(mc.textRenderer, en, x + w - mc.textRenderer.getWidth(en), by - 12, 0xFFC8D8E8);

        int my = by + 14;
        float mp = rank >= 5 ? 1f : (float) (xp - SData.RANK_XP[rank]) / (SData.RANK_XP[rank + 1] - SData.RANK_XP[rank]);
        g.fill(x, my, x + w, my + 3, argb(0x0B0F14, 0xB4));
        g.fill(x, my, x + (int) (w * Math.max(0f, Math.min(1f, mp))), my + 3, argb(0xFFD35A, 0xFF));

        boolean noBall = st == Style.SPIN && !s.hasBall();
        int ay = my + 8;
        for (int i = 0; i < rows; i++) {
            Skill sk = Skill.of(st, i);
            if (sk == null) continue;
            int ry = ay + i * 12;
            long left = Math.max(0, ClientState.cdEnd[sk.ordinal()] - t);
            boolean locked = rank < sk.minRank;
            boolean afford = s.energy() >= sk.cost && !noBall;
            int tcol = locked ? 0xFF666E7A : left > 0 ? 0xFF8A94A0 : afford ? 0xFFFFFFFF : 0xFFFF7777;
            g.fill(x - 3, ry - 1, x + w + 3, ry + 10, argb(0x0B0F14, 0x88));
            g.drawTextWithShadow(mc.textRenderer, Text.literal("[").append(FightClient.SLOT[i].getBoundKeyLocalizedText()).append("] ").append(sk.displayName()), x, ry + 1, tcol);
            String right = locked ? Text.translatable("rank.fightstyles." + sk.minRank).getString() : String.valueOf(sk.cost);
            g.drawTextWithShadow(mc.textRenderer, right, x + w - mc.textRenderer.getWidth(right), ry + 1, locked ? 0xFF666E7A : argb(col, 255));
            if (left > 0) {
                int cw = (int) (w * (1f - Math.min(1f, (float) left / Math.max(1, sk.cooldown))));
                g.fill(x, ry + 9, x + w, ry + 10, argb(0x000000, 0x90));
                g.fill(x, ry + 9, x + cw, ry + 10, argb(col, 0xFF));
            }
        }
        if (noBall) g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.fightstyles.no_ball"), x, ay + rows * 12 + 3, 0xFFFF7777);
        else if (st == Style.VAMPIRE && (t / 20) % 2 == 0 && s.energy() < 10)
            g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.fightstyles.hungry"), x, ay + rows * 12 + 3, 0xFFFF7777);
    }
}
