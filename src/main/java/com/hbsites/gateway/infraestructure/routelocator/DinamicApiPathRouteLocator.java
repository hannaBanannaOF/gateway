package com.hbsites.gateway.infraestructure.routelocator;

import com.hbsites.gateway.application.service.RouteLocatorService;
import com.hbsites.gateway.infraestructure.mongo.GatewayPathDocument;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import reactor.core.publisher.Flux;

@Slf4j
@AllArgsConstructor
public class DinamicApiPathRouteLocator implements RouteLocator {

    private final RouteLocatorBuilder routeLocatorBuilder;

    @Autowired
    private RouteLocatorService serv;

    @Override
    public Flux<Route> getRoutes() {
        log.info("Updating routes");
        RouteLocatorBuilder.Builder routesBuilder = routeLocatorBuilder.routes();
        serv.findAll().forEach(gatewayUpdatePaths -> routesBuilder.route(gatewayUpdatePaths.instance(), predicateSpec ->
                setPredicateSpec(gatewayUpdatePaths, predicateSpec)
        ));
        return routesBuilder.build().getRoutes();
    }

    private Buildable<Route> setPredicateSpec(GatewayPathDocument route, PredicateSpec predicateSpec) {
        BooleanSpec booleanSpec = predicateSpec.path(route.regex());
        return booleanSpec.uri(route.uri());
    }
}
