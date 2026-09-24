CREATE TABLE expert_profile_revisions (
    id              BIGSERIAL PRIMARY KEY,
    profile_id      BIGINT       NOT NULL UNIQUE REFERENCES expert_profiles (id) ON DELETE CASCADE,
    display_name    VARCHAR(200),
    job_title       VARCHAR(200),
    bio             VARCHAR(2000),
    photo_uri       TEXT,
    skills_json     TEXT,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    review_note     VARCHAR(2000),
    submitted_at    TIMESTAMP WITH TIME ZONE,
    reviewed_at     TIMESTAMP WITH TIME ZONE,
    reviewed_by     VARCHAR(128),
    created_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_expert_profile_revisions_status CHECK (
        status IN ('DRAFT', 'PENDING', 'REJECTED')
    )
);

CREATE INDEX idx_expert_profile_revisions_status ON expert_profile_revisions (status);

CREATE TABLE expert_revision_portfolio_items (
    id              BIGSERIAL PRIMARY KEY,
    revision_id     BIGINT       NOT NULL REFERENCES expert_profile_revisions (id) ON DELETE CASCADE,
    media_uri       TEXT         NOT NULL,
    caption         VARCHAR(500),
    sort_order      INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_expert_revision_portfolio_revision ON expert_revision_portfolio_items (revision_id);

CREATE TABLE expert_revision_services (
    id              BIGSERIAL PRIMARY KEY,
    revision_id     BIGINT       NOT NULL REFERENCES expert_profile_revisions (id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    price           VARCHAR(64),
    description     VARCHAR(1000),
    sort_order      INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_expert_revision_services_revision ON expert_revision_services (revision_id);
