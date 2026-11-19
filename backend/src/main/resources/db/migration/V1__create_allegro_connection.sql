CREATE TABLE allegro_connection (
    id BIGINT PRIMARY KEY,
    account_id VARCHAR(255),
    login VARCHAR(255),
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(255)
);

INSERT INTO allegro_connection (id) VALUES (1);
