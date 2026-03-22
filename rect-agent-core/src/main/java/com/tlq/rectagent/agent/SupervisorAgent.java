package com.tlq.rectagent.agent;

import com.tlq.rectagent.model.ModelRouter;
import com.tlq.rectagent.agent.tools.AgentTool;
import java.util.List;

public class SupervisorAgent {
    private final ModelRouter router;
    private final List<AgentTool> tools;

    public SupervisorAgent(ModelRouter router, List<AgentTool> tools) {
        this.router = router;
        this.tools = tools;
    }

    // Orchestrate a simple pipeline: run tools in sequence, then route the result
    public String invoke(String input) {
        String acc = input;
        if (tools != null) {
            for (AgentTool t : tools) {
                acc = t.apply(acc);
            }
        }
        if (router == null) throw new IllegalStateException("Router not configured");
        return router.route(acc);
    }
}
