CREATE TABLE expert_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(200),
    job_title       VARCHAR(200),
    bio             VARCHAR(2000),
    photo_uri       TEXT,
    skills_json     TEXT,
    status          VARCHAR(32)  NOT NULL DEFAULT 'NOT_STARTED',
    review_note     VARCHAR(2000),
    submitted_at    TIMESTAMP WITH TIME ZONE,
    reviewed_at     TIMESTAMP WITH TIME ZONE,
    reviewed_by     VARCHAR(128),
    created_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expert_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_expert_profiles_status CHECK (
        status IN ('NOT_STARTED', 'DRAFT', 'PENDING', 'APPROVED', 'REJECTED')
    )
);

CREATE INDEX idx_expert_profiles_status ON expert_profiles (status);
CREATE INDEX idx_expert_profiles_submitted_at ON expert_profiles (submitted_at DESC);

CREATE TABLE expert_portfolio_items (
    id              BIGSERIAL PRIMARY KEY,
    profile_id      BIGINT       NOT NULL REFERENCES expert_profiles (id) ON DELETE CASCADE,
    media_uri       TEXT         NOT NULL,
    caption         VARCHAR(500),
    sort_order      INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_expert_portfolio_profile ON expert_portfolio_items (profile_id);

CREATE TABLE expert_services (
    id              BIGSERIAL PRIMARY KEY,
    profile_id      BIGINT       NOT NULL REFERENCES expert_profiles (id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    price           VARCHAR(64),
    description     VARCHAR(1000),
    sort_order      INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_expert_services_profile ON expert_services (profile_id);
