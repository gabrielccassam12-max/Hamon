package com.fightstyles.client;

import com.fightstyles.core.SData;
import com.fightstyles.core.Skill;
import com.fightstyles.core.Style;
import com.fightstyles.net.ActionPayload;
import com.fightstyles.net.SyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Menu (key U): overview of the three styles, technique browser, statistics and controls. */
public class MenuScreen extends Screen {
    private static final int W = 360, H = 220;
    private static final String[] TABS = {"overview", "skills", "stats", "controls"};
    private static int lastTab = 0;
    private int tab = lastTab;
    private int style = 0;

    public MenuScreen() {
        super(Text.translatable("menu.fightstyles.title"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int x = left(), y = top();
        int tw = W / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.translatable("menu.fightstyles.tab." + TABS[i]), b -> {
                tab = idx;
                lastTab = idx;
                clearAndInit();
            }).dimensions(x + i * tw + 2, y + 4, tw - 4, 18).build());
        }
        if (tab == 1) {
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                addDrawableChild(ButtonWidget.builder(Style.values()[i].displayName(), b -> {
                    style = idx;
                    clearAndInit();
                }).dimensions(x + 10 + i * 100, y + 28, 96, 16).build());
            }
        }
        if (tab == 0) {
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                SyncPayload s = ClientState.last;
                boolean has = s != null && (s.unlocked() & Style.values()[i].bit()) != 0;
                ButtonWidget w = ButtonWidget.builder(Text.translatable("menu.fightstyles.use"), b -> {
                    for (int k = 0; k < 3; k++) {
                        SyncPayload c = ClientState.last;
                        if (c != null && c.active() == idx) break;
                        ClientPlayNetworking.send(new ActionPayload(1, 0));
                    }
                }).dimensions(x + 10 + i * 116, y + H - 28, 100, 16).build();
                w.active = has;
                addDrawableChild(w);
            }
        }
    }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, 0xE00B0F14);
        g.drawBorder(x, y, W, H, 0xFF3A4656);
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        int x = left(), y = top();
        SyncPayload s = ClientState.last;
        if (s == null) {
            g.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.fightstyles.nodata"), width / 2, y + 100, 0xFFFFFFFF);
            return;
        }
        switch (tab) {
            case 0 -> overview(g, s, x, y);
            case 1 -> skills(g, s, x, y);
            case 2 -> stats(g, s, x, y);
            default -> controls(g, x, y);
        }
    }

    private void overview(DrawContext g, SyncPayload s, int x, int y) {
        for (int i = 0; i < 3; i++) {
            Style st = Style.values()[i];
            int cx = x + 10 + i * 116;
            boolean has = (s.unlocked() & st.bit()) != 0;
            int xp = i < s.xp().size() ? s.xp().get(i) : 0;
            int rank = SData.rankOf(xp);
            g.fill(cx, y + 30, cx + 108, y + H - 34, 0x80000000);
            g.drawBorder(cx, y + 30, 108, H - 64, (has ? 0xFF000000 : 0x60000000) | st.color);
            g.drawTextWithShadow(textRenderer, st.displayName(), cx + 6, y + 36, (0xFF000000) | st.color);
            if (!has) {
                g.drawTextWrapped(textRenderer, Text.translatable("menu.fightstyles.how." + st.name().toLowerCase(Locale.ROOT)), cx + 6, y + 54, 96, 0xFFB8C4D0);
                continue;
            }
            g.drawTextWithShadow(textRenderer, Text.translatable("rank.fightstyles." + rank), cx + 6, y + 50, 0xFFFFD35A);
            float mp = rank >= 5 ? 1f : (float) (xp - SData.RANK_XP[rank]) / (SData.RANK_XP[rank + 1] - SData.RANK_XP[rank]);
            g.fill(cx + 6, y + 64, cx + 102, y + 68, 0xFF202830);
            g.fill(cx + 6, y + 64, cx + 6 + (int) (96 * mp), y + 68, 0xFFFFD35A);
            g.drawTextWithShadow(textRenderer, "XP " + xp, cx + 6, y + 74, 0xFFC8D8E8);
            if (s.active() == i) g.drawTextWithShadow(textRenderer, Text.translatable("menu.fightstyles.active"), cx + 6, y + 88, 0xFF7CFF9A);
            int n = 0;
            for (int k = 0; k < 7; k++) {
                Skill sk = Skill.of(st, k);
                if (sk != null && rank >= sk.minRank) n++;
            }
            g.drawTextWithShadow(textRenderer, Text.translatable("menu.fightstyles.techs", n, 7), cx + 6, y + 102, 0xFFB8C4D0);
        }
    }

    private void skills(DrawContext g, SyncPayload s, int x, int y) {
        Style st = Style.values()[style];
        int xp = style < s.xp().size() ? s.xp().get(style) : 0;
        int rank = SData.rankOf(xp);
        g.drawTextWrapped(textRenderer, Text.translatable("menu.fightstyles.desc." + st.name().toLowerCase(Locale.ROOT)), x + 10, y + 48, W - 20, 0xFFB8C4D0);
        for (int k = 0; k < 7; k++) {
            Skill sk = Skill.of(st, k);
            if (sk == null) continue;
            int ry = y + 76 + k * 19;
            boolean locked = rank < sk.minRank;
            g.fill(x + 8, ry, x + W - 8, ry + 17, 0x80000000);
            g.fill(x + 8, ry, x + 11, ry + 17, (locked ? 0xFF444C58 : 0xFF000000 | st.color));
            Text key = FightClient.SLOT[k].getBoundKeyLocalizedText();
            g.drawTextWithShadow(textRenderer, Text.literal("[").append(key).append("] ").append(sk.displayName()), x + 16, ry + 2, locked ? 0xFF666E7A : 0xFFFFFFFF);
            g.drawTextWithShadow(textRenderer, Text.translatable("skill.fightstyles." + sk.name().toLowerCase(Locale.ROOT) + ".desc"), x + 16, ry + 9, 0xFF8A94A0);
            String r = locked ? Text.translatable("rank.fightstyles." + sk.minRank).getString() : sk.cost + " / " + (sk.cooldown / 20) + "s";
            g.drawTextWithShadow(textRenderer, r, x + W - 14 - textRenderer.getWidth(r), ry + 5, locked ? 0xFF666E7A : 0xFFFFD35A);
        }
    }

    private void stats(DrawContext g, SyncPayload s, int x, int y) {
        String[] keys = {"casts", "kills_hamon", "kills_spin", "kills_vampire", "bites", "undead"};
        for (int i = 0; i < keys.length; i++) {
            int v = i < s.stats().size() ? s.stats().get(i) : 0;
            g.drawTextWithShadow(textRenderer, Text.translatable("menu.fightstyles.stat." + keys[i]).append(": " + v), x + 20, y + 40 + i * 16, 0xFFFFFFFF);
        }
    }

    private void controls(DrawContext g, int x, int y) {
        for (int i = 0; i < 7; i++) {
            g.drawTextWithShadow(textRenderer, Text.literal("[").append(FightClient.SLOT[i].getBoundKeyLocalizedText()).append("] ").append(Text.translatable("menu.fightstyles.slot", i + 1)), x + 20, y + 34 + i * 12, 0xFFFFFFFF);
        }
        g.drawTextWithShadow(textRenderer, Text.literal("[").append(FightClient.SWITCH.getBoundKeyLocalizedText()).append("] ").append(Text.translatable("menu.fightstyles.switch")), x + 20, y + 124, 0xFFFFD35A);
        g.drawTextWithShadow(textRenderer, Text.literal("[").append(FightClient.MENU.getBoundKeyLocalizedText()).append("] ").append(Text.translatable("menu.fightstyles.menu")), x + 20, y + 136, 0xFFFFD35A);
        g.drawTextWrapped(textRenderer, Text.translatable("menu.fightstyles.tip"), x + 20, y + 158, W - 40, 0xFFB8C4D0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
