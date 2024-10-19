package com.hbsites.gateway.infraestructure.routelocator;

import com.hbsites.commons.domain.params.DefaultParams;
import com.hbsites.commons.infrastructure.messages.gateway.GatewayUpdatePaths;
import com.hbsites.gateway.domain.RegexGenerator;
import com.hbsites.gateway.infraestructure.store.RoutesStore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@AllArgsConstructor
public class DinamicApiPathRouteLocator implements RouteLocator {

    private final RouteLocatorBuilder routeLocatorBuilder;

    @Override
    public Flux<Route> getRoutes() {
        log.info("Updating routes");
        RoutesStore s = RoutesStore.getInstance();
        RouteLocatorBuilder.Builder routesBuilder = routeLocatorBuilder.routes();
        s.getStoredPaths().forEach(gatewayUpdatePaths -> routesBuilder.route(gatewayUpdatePaths.getInstance(), predicateSpec ->
                setPredicateSpec(gatewayUpdatePaths, predicateSpec)
        ));
        return routesBuilder.build().getRoutes();
    }

    private Buildable<Route> setPredicateSpec(GatewayUpdatePaths route, PredicateSpec predicateSpec) {
        BooleanSpec booleanSpec = predicateSpec.path(route.getPathRegex());
        return booleanSpec.uri(route.getUrl());
    }
}
