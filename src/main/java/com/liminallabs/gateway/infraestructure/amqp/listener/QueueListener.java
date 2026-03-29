package com.liminallabs.gateway.infraestructure.amqp.listener;

import com.liminallabs.gateway.domain.model.GatewayUpdatePaths;
import com.liminallabs.gateway.infraestructure.mongo.GatewayPathDocument;
import com.liminallabs.gateway.infraestructure.mongo.UpdatePathsRepository;
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

    @RabbitListener(queues = {"${liminallabs.gateway.amqp.queue}"})
    public void read(@Payload com.liminallabs.gateway.domain.model.GatewayUpdatePaths payload) {
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
