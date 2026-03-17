package com.tlq.openclaw.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractChannel implements Channel {
    @Getter
    private final String id;
    @Getter
    private final String name;
    @Getter
    private final String type;
    @Getter
    @Setter
    private boolean enabled;
    
    public AbstractChannel(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.enabled = false;
    }
    
    @Override
    public void start() {
        if (!enabled) {
            log.warn("Channel {} is disabled, skipping start", name);
            return;
        }
        log.info("Starting channel: {}", name);
        doStart();
    }
    
    @Override
    public void stop() {
        log.info("Stopping channel: {}", name);
        doStop();
    }
    
    @Override
    public void sendMessage(String recipient, String message) {
        if (!enabled) {
            log.warn("Channel {} is disabled, skipping sendMessage", name);
            return;
        }
        log.info("Sending message to {} via {}: {}", recipient, name, message);
        doSendMessage(recipient, message);
    }
    
    @Override
    public void receiveMessage(String sender, String message) {
        log.info("Received message from {} via {}: {}", sender, name, message);
        doReceiveMessage(sender, message);
    }
    
    protected abstract void doStart();
    protected abstract void doStop();
    protected abstract void doSendMessage(String recipient, String message);
    protected abstract void doReceiveMessage(String sender, String message);
}