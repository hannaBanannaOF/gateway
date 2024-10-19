package com.hbsites.gateway.infraestructure.amqp.listener;

import com.hbsites.commons.infrastructure.messages.gateway.GatewayUpdatePaths;
import com.hbsites.gateway.infraestructure.store.RoutesStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class QueueListener {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @RabbitListener(queues = {"${hbsites.gateway.amqp.queue}"})
    public void read(@Payload GatewayUpdatePaths payload) {
        RoutesStore s = RoutesStore.getInstance();
        s.addPaths(payload);
        applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
        System.out.println("RECEIVED -> " + payload.getInstance());
    }
}
