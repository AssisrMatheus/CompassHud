package com.vanillapings.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PingEvents {
    private static final List<PingListener> LISTENERS = new CopyOnWriteArrayList<>();

    private PingEvents() {
    }

    public static void register(PingListener listener) {
        LISTENERS.add(listener);
    }

    public static void fire(PingCreatedEvent event) {
        for (PingListener listener : LISTENERS) {
            listener.onPingCreated(event);
        }
    }
}
