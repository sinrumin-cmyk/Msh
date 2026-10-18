package com.example.darkentity.network;

import com.example.darkentity.DarkEntityMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** kind 0 - луч (a -> b), kind 1 - тряска камеры (в точке a, radius = сила в градусах, life = тики). */
public record FxPayload(byte kind, Vec3 a, Vec3 b, float radius, int color, int life)
        implements CustomPacketPayload {

    public static final Type<FxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DarkEntityMod.MODID, "fx"));
    public static final StreamCodec<FriendlyByteBuf, FxPayload> CODEC =
            CustomPacketPayload.codec(FxPayload::write, FxPayload::new);

    public FxPayload(FriendlyByteBuf buf) {
        this(buf.readByte(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readInt(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeDouble(a.x);
        buf.writeDouble(a.y);
        buf.writeDouble(a.z);
        buf.writeDouble(b.x);
        buf.writeDouble(b.y);
        buf.writeDouble(b.z);
        buf.writeFloat(radius);
        buf.writeInt(color);
        buf.writeVarInt(life);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
