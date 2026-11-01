package com.fightstyles.net;

import com.fightstyles.FightStyles;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> player: camera shake of the given strength for N ticks. */
public record ShakePayload(int strength, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<ShakePayload> ID = new CustomPayload.Id<>(FightStyles.id("shake"));
    public static final PacketCodec<RegistryByteBuf, ShakePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ShakePayload::strength,
            PacketCodecs.VAR_INT, ShakePayload::ticks,
            ShakePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
