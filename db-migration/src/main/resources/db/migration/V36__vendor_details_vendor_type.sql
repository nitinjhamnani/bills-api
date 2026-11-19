ALTER TABLE vendor_details
    ADD COLUMN IF NOT EXISTS vendor_type varchar(20) NOT NULL DEFAULT 'COMPANY';
