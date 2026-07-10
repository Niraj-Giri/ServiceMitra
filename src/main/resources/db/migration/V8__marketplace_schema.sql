-- =============================================================================
-- V8: Airtasker-Style Marketplace Schema
-- Introduces task_requests (replaces bookings) and quotes (bidding system)
-- =============================================================================

-- Task Requests: customer posts a job with description + budget
CREATE TABLE IF NOT EXISTS task_requests (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    user_id          BIGINT          NOT NULL,
    service_id       BIGINT          NOT NULL,
    service_name     VARCHAR(255),
    category         VARCHAR(100),
    title            VARCHAR(300)    NOT NULL,
    description      TEXT            NOT NULL,
    budget_min_npr   DECIMAL(12, 2)  NOT NULL,
    budget_max_npr   DECIMAL(12, 2)  NOT NULL,
    address          TEXT            NOT NULL,
    latitude         DOUBLE,
    longitude        DOUBLE,
    preferred_date   DATE,
    status           VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    accepted_quote_id BIGINT,
    points_redeemed  INT             DEFAULT 0,
    points_discount_npr DECIMAL(12, 2) DEFAULT 0.00,
    start_otp        VARCHAR(6),
    otp_generated_at DATETIME,
    started_at       DATETIME,
    completed_at     DATETIME,
    platform_fee     DECIMAL(12, 2),
    final_amount_npr DECIMAL(12, 2),
    cancelled_by     VARCHAR(30),
    cancellation_reason TEXT,
    cancelled_at     DATETIME,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tr_user   FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_tr_service FOREIGN KEY (service_id) REFERENCES services(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quotes: providers submit price offers against a task request
CREATE TABLE IF NOT EXISTS quotes (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    task_request_id     BIGINT         NOT NULL,
    provider_id         BIGINT         NOT NULL,
    quoted_price_npr    DECIMAL(12, 2) NOT NULL,
    counter_price_npr   DECIMAL(12, 2),
    final_price_npr     DECIMAL(12, 2),
    message             TEXT,
    status              VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    expires_at          DATETIME,
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_q_task     FOREIGN KEY (task_request_id) REFERENCES task_requests(id),
    CONSTRAINT fk_q_provider FOREIGN KEY (provider_id)     REFERENCES providers(id),
    -- One provider can only have one active quote per task
    UNIQUE KEY uq_provider_task (task_request_id, provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Performance indexes
CREATE INDEX idx_tr_user_status    ON task_requests(user_id, status);
CREATE INDEX idx_tr_service_status ON task_requests(service_id, status);
CREATE INDEX idx_tr_location       ON task_requests(latitude, longitude);
CREATE INDEX idx_tr_status         ON task_requests(status);
CREATE INDEX idx_q_task            ON quotes(task_request_id, status);
CREATE INDEX idx_q_provider        ON quotes(provider_id, status);
