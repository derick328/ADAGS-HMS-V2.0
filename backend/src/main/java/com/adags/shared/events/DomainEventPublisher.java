package com.adags.shared.events;

public interface DomainEventPublisher {

    void publish(String eventType, Object payload);
}
