package com.tlq.openclaw.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Component
public class SessionManager {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    public Session createSession() {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId);
        sessions.put(sessionId, session);
        log.info("Created session: {}", sessionId);
        return session;
    }
    
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Removed session: {}", sessionId);
    }
    
    public Map<String, Session> getAllSessions() {
        return sessions;
    }
    
    public static class Session {
        private final String id;
        private String name;
        private String status;
        
        public Session(String id) {
            this.id = id;
            this.status = "active";
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
}