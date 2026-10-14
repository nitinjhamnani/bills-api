CREATE TABLE wallet_topup_request (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           uuid NOT NULL,
    amount              numeric(14,2) NOT NULL,
    payment_mode        varchar(10) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'PENDING',
    document_id         uuid,
    idempotency_key     varchar(150) NOT NULL,
    requested_by        varchar(100),
    reviewed_by         varchar(100),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    payment_reference   varchar(150),
    CONSTRAINT wallet_topup_request_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT wallet_topup_request_payment_mode_check
        CHECK (payment_mode IN ('RTGS', 'NEFT', 'UPI', 'CASH', 'OTHER')),
    CONSTRAINT wallet_topup_request_client_id_fkey FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE CASCADE,
    CONSTRAINT wallet_topup_request_document_id_fkey FOREIGN KEY (document_id)
        REFERENCES document (id)
);

CREATE INDEX idx_topup_client_created ON wallet_topup_request (client_id, created_at DESC);
CREATE INDEX idx_topup_status_created ON wallet_topup_request (status, created_at);
CREATE INDEX idx_topup_created ON wallet_topup_request (created_at DESC);
