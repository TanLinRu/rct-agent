package com.tlq.openclaw.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Component
public class ChannelManager {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    
    public Channel createChannel(String name, String type, Map<String, Object> config) {
        String channelId = UUID.randomUUID().toString();
        Channel channel;
        
        switch (type.toLowerCase()) {
            case "telegram":
                String botToken = (String) config.get("botToken");
                channel = new TelegramChannel(channelId, name, botToken);
                break;
            default:
                throw new IllegalArgumentException("Unsupported channel type: " + type);
        }
        
        channels.put(channelId, channel);
        log.info("Created channel: {} (type: {})", name, type);
        return channel;
    }
    
    public Channel getChannel(String channelId) {
        return channels.get(channelId);
    }
    
    public void removeChannel(String channelId) {
        Channel channel = channels.remove(channelId);
        if (channel != null) {
            channel.stop();
            log.info("Removed channel: {}", channel.getName());
        }
    }
    
    public Map<String, Channel> getAllChannels() {
        return channels;
    }
    
    public void startAllChannels() {
        channels.values().forEach(Channel::start);
    }
    
    public void stopAllChannels() {
        channels.values().forEach(Channel::stop);
    }
}