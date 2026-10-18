package com.example.darkentity.network;

import com.example.darkentity.client.ClientFx;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Тонкая обёртка, чтобы главный класс не тянул клиентские классы на сервере. */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void handle(FxPayload payload, IPayloadContext context) {
        ClientFx.add(payload);
    }
}
