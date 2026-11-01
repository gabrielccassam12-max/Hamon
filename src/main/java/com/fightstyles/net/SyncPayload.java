package com.fightstyles.net;

import com.fightstyles.FightStyles;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> owner: everything the HUD and the menu show. cd = remaining cooldown ticks per Skill ordinal. */
public record SyncPayload(int unlocked, int active, int energy, int max, boolean hasBall, List<Integer> xp, int[] cd, List<Integer> stats)
        implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(FightStyles.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(SyncPayload::write, SyncPayload::read);

    private static void write(SyncPayload p, RegistryByteBuf b) {
        b.writeVarInt(p.unlocked());
        b.writeVarInt(p.active() + 1);
        b.writeVarInt(p.energy());
        b.writeVarInt(p.max());
        b.writeBoolean(p.hasBall());
        b.writeVarInt(p.xp().size());
        for (int v : p.xp()) b.writeVarInt(v);
        b.writeVarInt(p.cd().length);
        for (int v : p.cd()) b.writeVarInt(v);
        b.writeVarInt(p.stats().size());
        for (int v : p.stats()) b.writeVarInt(v);
    }

    private static SyncPayload read(RegistryByteBuf b) {
        int un = b.readVarInt(), act = b.readVarInt() - 1, en = b.readVarInt(), max = b.readVarInt();
        boolean ball = b.readBoolean();
        int n = b.readVarInt();
        List<Integer> xp = new ArrayList<>();
        for (int i = 0; i < n; i++) xp.add(b.readVarInt());
        int[] cd = new int[b.readVarInt()];
        for (int i = 0; i < cd.length; i++) cd[i] = b.readVarInt();
        int m = b.readVarInt();
        List<Integer> st = new ArrayList<>();
        for (int i = 0; i < m; i++) st.add(b.readVarInt());
        return new SyncPayload(un, act, en, max, ball, xp, cd, st);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
