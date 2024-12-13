package com.hbsites.gateway;

import com.hbsites.gateway.application.service.RouteLocatorService;
import com.hbsites.gateway.infraestructure.routelocator.DinamicApiPathRouteLocator;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@EnableRabbit
@SpringBootApplication
public class GatewayApplication {

	@Bean
	public Jackson2JsonMessageConverter converter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RouteLocator routeLocator(RouteLocatorBuilder routeLocatorBuilder, RouteLocatorService service) {
		return new DinamicApiPathRouteLocator(routeLocatorBuilder, service);
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
