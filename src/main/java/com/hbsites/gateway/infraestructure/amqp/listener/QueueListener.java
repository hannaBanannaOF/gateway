package com.hbsites.gateway.infraestructure.amqp.listener;

import com.hbsites.commons.infrastructure.messages.gateway.GatewayUpdatePaths;
import com.hbsites.gateway.infraestructure.mongo.GatewayPathDocument;
import com.hbsites.gateway.infraestructure.mongo.UpdatePathsRepository;
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

    @Autowired
    private UpdatePathsRepository repository;

    @RabbitListener(queues = {"${hbsites.gateway.amqp.queue}"})
    public void read(@Payload GatewayUpdatePaths payload) {
        GatewayPathDocument exist = repository.findByInstance(payload.instance());
        if (exist != null) {
            exist = new GatewayPathDocument(exist.id(), payload.instance(), payload.regex(), payload.url());
        } else {
            exist = new GatewayPathDocument(null, payload.instance(), payload.regex(), payload.url());
        }
        repository.save(exist);
        repository.findAll();
        applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
        System.out.println("RECEIVED -> " + payload.instance());
    }
}
