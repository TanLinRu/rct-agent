package com.tlq.openclaw.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Gateway {
    @Autowired
    private SessionManager sessionManager;
    
    public void start() {
        log.info("Gateway started");
    }
    
    public void stop() {
        log.info("Gateway stopped");
    }
    
    public SessionManager.Session createSession() {
        return sessionManager.createSession();
    }
    
    public SessionManager.Session getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }
    
    public void removeSession(String sessionId) {
        sessionManager.removeSession(sessionId);
    }
}