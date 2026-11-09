CREATE TABLE document (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type      varchar(40) NOT NULL,
    owner_id        varchar(100),
    file_name       varchar(255) NOT NULL,
    content_type    varchar(100),
    storage_path    varchar(500) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    uploaded_by     varchar(100),
    reviewed_by     varchar(100),
    review_notes    varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_owner ON document (owner_type, owner_id);
CREATE INDEX idx_document_status ON document (status);
