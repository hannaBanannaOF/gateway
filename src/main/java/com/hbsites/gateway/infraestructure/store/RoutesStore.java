package com.hbsites.gateway.infraestructure.store;

import com.hbsites.commons.infrastructure.messages.gateway.GatewayUpdatePaths;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class RoutesStore {
    private static RoutesStore instance;

    private RoutesStore() {

    }

    public static RoutesStore getInstance() {
        if (instance == null) {
            instance = new RoutesStore();
        }
        return instance;
    }

    private final List<GatewayUpdatePaths> storedPaths = new ArrayList<>();

    public void addPaths(GatewayUpdatePaths add) {
        this.storedPaths.removeIf(gatewayUpdatePaths -> gatewayUpdatePaths.getInstance().equals(add.getInstance()));
        this.storedPaths.add(add);
    }

    public void clearAll() {
        this.storedPaths.clear();
    }
}
