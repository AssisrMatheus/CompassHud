package com.vanillapings.api;

@FunctionalInterface
public interface PingListener {
    void onPingCreated(PingCreatedEvent event);
}
