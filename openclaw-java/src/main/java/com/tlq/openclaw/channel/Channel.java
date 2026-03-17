package com.tlq.openclaw.channel;

public interface Channel {
    String getId();
    String getName();
    String getType();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    void start();
    void stop();
    void sendMessage(String recipient, String message);
    void receiveMessage(String sender, String message);
}