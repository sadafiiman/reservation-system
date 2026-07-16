CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    cancelled_at DATETIME NULL,

    -- Generated column: only holds slot_id while status = CONFIRMED,
    -- otherwise NULL. This lets us put a UNIQUE constraint on it that
    -- effectively means "a slot can have at most one active reservation",
    -- while still allowing that slot to be booked again later after a
    -- cancellation (MySQL has no native partial/filtered unique index).
    active_slot_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'CONFIRMED' THEN slot_id END
    ) VIRTUAL,

    CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_res_slot FOREIGN KEY (slot_id) REFERENCES available_slots(id),
    CONSTRAINT chk_res_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    UNIQUE KEY uq_active_slot (active_slot_id),
    INDEX idx_user_status (user_id, status)
);
