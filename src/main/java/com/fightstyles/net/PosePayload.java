package com.fightstyles.net;

import com.fightstyles.FightStyles;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> everyone nearby: this player is casting (arm / leg pose) for N ticks. */
public record PosePayload(int entityId, int pose, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<PosePayload> ID = new CustomPayload.Id<>(FightStyles.id("pose"));
    public static final PacketCodec<RegistryByteBuf, PosePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PosePayload::entityId,
            PacketCodecs.VAR_INT, PosePayload::pose,
            PacketCodecs.VAR_INT, PosePayload::ticks,
            PosePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
