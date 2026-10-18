package com.example.darkentity.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Серверные хелперы: отправить игрокам рядом луч или тряску камеры. */
public final class Fx {
    private Fx() {}

    public static void beam(ServerLevel level, Vec3 a, Vec3 b, float radius, int color, int life) {
        send(level, a, new FxPayload((byte) 0, a, b, radius, color, life));
    }

    public static void shake(ServerLevel level, Vec3 pos, float intensity, int ticks) {
        send(level, pos, new FxPayload((byte) 1, pos, pos, intensity, 0, ticks));
    }

    private static void send(ServerLevel level, Vec3 at, FxPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 160.0, payload);
    }
}
