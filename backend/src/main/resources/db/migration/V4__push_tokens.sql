-- FCM device tokens for push notifications (ADR-0008: push wakes the device; not a data channel).
CREATE TABLE push_tokens (
    user_id    bigint NOT NULL REFERENCES users(id),
    token      text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, token)
);
