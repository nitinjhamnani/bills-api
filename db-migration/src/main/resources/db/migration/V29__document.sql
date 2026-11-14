CREATE TABLE document (
    id           uuid PRIMARY KEY,
    owner_type   varchar(255) NOT NULL,
    owner_id     varchar(255),
    file_name    varchar(255) NOT NULL,
    content_type varchar(255),
    storage_path varchar(255) NOT NULL,
    status       varchar(255) NOT NULL,
    uploaded_by  varchar(255),
    reviewed_by  varchar(255),
    review_notes varchar(255),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL
);

CREATE INDEX document_owner_idx ON document (owner_type, owner_id);
CREATE INDEX document_status_idx ON document (status);
