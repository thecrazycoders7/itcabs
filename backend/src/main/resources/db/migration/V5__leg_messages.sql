-- In-app chat (M7): a message thread per leg, between its coordinator and the claiming driver.
-- Numbers stay private — communication goes through the app, not phone numbers (ADR context).
CREATE TABLE leg_messages (
    id         bigserial PRIMARY KEY,
    leg_id     bigint NOT NULL REFERENCES legs(id),
    sender_id  bigint NOT NULL REFERENCES users(id),
    body       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_leg_messages_leg ON leg_messages (leg_id, created_at);
