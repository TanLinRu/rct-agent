-- H2 Database Schema for Agent Message Flow & Data Governance
-- Chat Sessions Table
CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    total_tokens INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    summary_snapshot TEXT,
    trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_user_id ON chat_sessions(user_id);
CREATE INDEX idx_sessions_status ON chat_sessions(status);
CREATE INDEX idx_sessions_created_at ON chat_sessions(created_at);

-- Chat Messages Table
CREATE TABLE IF NOT EXISTS chat_messages (
    message_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    turn_index INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content_raw TEXT NOT NULL,
    content_processed TEXT,
    sys_prompt_ver VARCHAR(50),
    token_usage JSON,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_session_id ON chat_messages(session_id);
CREATE INDEX idx_messages_turn_index ON chat_messages(session_id, turn_index);
CREATE INDEX idx_messages_role ON chat_messages(role);

-- Tool Executions Table
CREATE TABLE IF NOT EXISTS tool_executions (
    execution_id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    mcp_server_name VARCHAR(100),
    tool_name VARCHAR(100) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    duration_ms BIGINT,
    nacos_instance VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES chat_messages(message_id) ON DELETE CASCADE
);

CREATE INDEX idx_tool_executions_message_id ON tool_executions(message_id);
CREATE INDEX idx_tool_executions_mcp_server ON tool_executions(mcp_server_name);
CREATE INDEX idx_tool_executions_status ON tool_executions(status);

-- Profile Changes Table
CREATE TABLE IF NOT EXISTS profile_changes (
    change_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    reasoning TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_profile_changes_user_id ON profile_changes(user_id);
CREATE INDEX idx_profile_changes_field ON profile_changes(field_name);

-- Conversation State Checkpoint Table (for断点恢复)
CREATE TABLE IF NOT EXISTS conversation_checkpoints (
    checkpoint_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    state_data TEXT NOT NULL,
    step_index INT NOT NULL,
    is_resumed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE
);

CREATE INDEX idx_checkpoints_session_id ON conversation_checkpoints(session_id);
CREATE INDEX idx_checkpoints_message_id ON conversation_checkpoints(message_id);
