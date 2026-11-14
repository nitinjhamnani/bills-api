CREATE TABLE vendor_details (
    vendor_id           varchar(8) PRIMARY KEY,
    vendor_name         varchar(200) NOT NULL,
    vendor_phone        varchar(15),
    vendor_email        varchar(150),
    vendor_address      varchar(500),
    vendor_state        varchar(100),
    vendor_location     varchar(150),
    is_agreement_signed boolean NOT NULL DEFAULT false,
    agreement_status    varchar(20) NOT NULL,
    is_active           boolean NOT NULL DEFAULT false,
    onboarded_by        varchar(32),
    onboarded_by_id     varchar(6),
    CONSTRAINT vendor_details_id_check CHECK (vendor_id ~ '^VD[A-Z0-9]{6}$'),
    CONSTRAINT vendor_details_name_check CHECK (char_length(btrim(vendor_name)) > 0)
);
