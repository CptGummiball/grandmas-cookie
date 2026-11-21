package dev.cptgummiball.omacore.credits;

import dev.cptgummiball.omacore.OmaCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open the credits screen. Carries no data; the text lives in the client's resources. */
public record CreditsPayload() implements CustomPacketPayload {
    public static final Type<CreditsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OmaCore.MOD_ID, "show_credits"));
    public static final StreamCodec<ByteBuf, CreditsPayload> STREAM_CODEC = StreamCodec.unit(new CreditsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
