package com.fightstyles.net;

import com.fightstyles.FightStyles;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> everyone nearby: which styles a player has and which is active (for eyes and auras). */
public record StatePayload(int entityId, int mask, int active) implements CustomPayload {
    public static final CustomPayload.Id<StatePayload> ID = new CustomPayload.Id<>(FightStyles.id("state"));
    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StatePayload::entityId,
            PacketCodecs.VAR_INT, StatePayload::mask,
            PacketCodecs.VAR_INT, StatePayload::active,
            StatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
