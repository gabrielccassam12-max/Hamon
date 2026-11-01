package com.fightstyles.net;

import com.fightstyles.FightStyles;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server. kind: 0 use technique (slot 0..6), 1 switch to the next unlocked style. */
public record ActionPayload(int kind, int slot) implements CustomPayload {
    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(FightStyles.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ActionPayload::kind,
            PacketCodecs.VAR_INT, ActionPayload::slot,
            ActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
