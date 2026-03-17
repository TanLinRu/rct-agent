package com.tlq.openclaw.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlq.openclaw.agent.AgentRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GatewayWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private Gateway gateway;
    
    @Autowired
    private AgentRouter agentRouter;
    
    private final Map<WebSocketSession, String> sessionMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received message: {}", message.getPayload());
        
        // 解析消息
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String action = (String) payload.get("action");
        
        switch (action) {
            case "createSession":
                handleCreateSession(session);
                break;
            case "getSession":
                handleGetSession(session, (String) payload.get("sessionId"));
                break;
            case "removeSession":
                handleRemoveSession(session, (String) payload.get("sessionId"));
                break;
            case "sendMessage":
                handleSendMessage(session, (String) payload.get("message"));
                break;
            default:
                session.sendMessage(new TextMessage("Unknown action: " + action));
        }
    }
    
    private void handleCreateSession(WebSocketSession session) throws Exception {
        SessionManager.Session newSession = gateway.createSession();
        sessionMap.put(session, newSession.getId());
        
        Map<String, Object> response = Map.of(
            "action", "sessionCreated",
            "sessionId", newSession.getId(),
            "status", newSession.getStatus()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    private void handleGetSession(WebSocketSession session, String sessionId) throws Exception {
        SessionManager.Session existingSession = gateway.getSession(sessionId);
        if (existingSession != null) {
            Map<String, Object> response = Map.of(
                "action", "sessionRetrieved",
                "sessionId", existingSession.getId(),
                "name", existingSession.getName(),
                "status", existingSession.getStatus()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } else {
            session.sendMessage(new TextMessage("Session not found: " + sessionId));
        }
    }
    
    private void handleRemoveSession(WebSocketSession session, String sessionId) throws Exception {
        gateway.removeSession(sessionId);
        sessionMap.remove(session);
        session.sendMessage(new TextMessage("Session removed: " + sessionId));
    }
    
    private void handleSendMessage(WebSocketSession session, String message) throws Exception {
        String sessionId = sessionMap.get(session);
        if (sessionId == null) {
            session.sendMessage(new TextMessage("No session found for this connection"));
            return;
        }
        
        String response = agentRouter.routeMessage(sessionId, message);
        Map<String, Object> responseMap = Map.of(
            "action", "messageResponse",
            "response", response
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(responseMap)));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        String sessionId = sessionMap.remove(session);
        if (sessionId != null) {
            gateway.removeSession(sessionId);
            log.info("WebSocket connection closed, removed session: {}", sessionId);
        }
    }
}