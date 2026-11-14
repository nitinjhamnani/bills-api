CREATE TABLE corp_details (
    corp_id            varchar(8) PRIMARY KEY,
    corp_name          varchar(200) NOT NULL,
    corp_phone         varchar(15),
    corp_email         varchar(150),
    corp_type          varchar(20) NOT NULL,
    corp_address       varchar(500),
    corp_state         varchar(100),
    corp_location      varchar(150),
    is_agreement_signed boolean NOT NULL DEFAULT false,
    onboarding_status  varchar(20) NOT NULL,
    agreement_status   varchar(20) NOT NULL,
    onboarded_by       varchar(32),
    onboarded_by_id    varchar(6),
    is_active          boolean NOT NULL DEFAULT false,
    CONSTRAINT corp_details_id_check CHECK (corp_id ~ '^CO[A-Z0-9]{6}$'),
    CONSTRAINT corp_details_name_check CHECK (char_length(btrim(corp_name)) > 0)
);

CREATE INDEX corp_details_onboarded_by_idx ON corp_details (onboarded_by, onboarded_by_id);
