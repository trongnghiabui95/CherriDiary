CREATE TABLE live_connector_source (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    live_session_id BIGINT NOT NULL REFERENCES live_sessions(id),
    username VARCHAR(100) NOT NULL
);
