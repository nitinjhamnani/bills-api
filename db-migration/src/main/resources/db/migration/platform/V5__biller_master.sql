-- Master list of individual service providers (the actual BBPS "billers"), one level below
-- biller_service_catalog's categories. The "Create bill" flow asks the agency to pick a category
-- then a biller within it; biller_id is what a real BBPS fetch/pay call would route on.
CREATE TABLE IF NOT EXISTS biller (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    biller_id       VARCHAR(50) NOT NULL UNIQUE,
    biller_name     VARCHAR(200) NOT NULL,
    category        VARCHAR(30) NOT NULL REFERENCES biller_service_catalog(service_code),
    state           VARCHAR(50),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_biller_category ON biller (category);

INSERT INTO biller (biller_id, biller_name, category, state) VALUES
    -- ELECTRICITY
    ('ELEC-TATAPOWER-MUM', 'Tata Power Company Ltd - Mumbai', 'ELECTRICITY', 'Maharashtra'),
    ('ELEC-ADANI-MUM', 'Adani Electricity Mumbai Ltd', 'ELECTRICITY', 'Maharashtra'),
    ('ELEC-BEST-MUM', 'BEST Undertaking - Mumbai', 'ELECTRICITY', 'Maharashtra'),
    ('ELEC-MSEDCL', 'Maharashtra State Electricity Distribution Co Ltd (MSEDCL)', 'ELECTRICITY', 'Maharashtra'),
    ('ELEC-BSES-RAJ', 'BSES Rajdhani Power Ltd - Delhi', 'ELECTRICITY', 'Delhi'),
    ('ELEC-BSES-YAM', 'BSES Yamuna Power Ltd - Delhi', 'ELECTRICITY', 'Delhi'),
    ('ELEC-TPDDL', 'Tata Power Delhi Distribution Ltd (TPDDL)', 'ELECTRICITY', 'Delhi'),
    ('ELEC-CESC', 'CESC Limited - Kolkata', 'ELECTRICITY', 'West Bengal'),
    ('ELEC-BESCOM', 'Bangalore Electricity Supply Co (BESCOM)', 'ELECTRICITY', 'Karnataka'),
    ('ELEC-TSSPDCL', 'Telangana Southern Power Distribution Co (TSSPDCL)', 'ELECTRICITY', 'Telangana'),
    ('ELEC-TANGEDCO', 'Tamil Nadu Generation and Distribution Corp (TANGEDCO)', 'ELECTRICITY', 'Tamil Nadu'),
    ('ELEC-UPPCL', 'Uttar Pradesh Power Corporation Ltd (UPPCL)', 'ELECTRICITY', 'Uttar Pradesh'),
    ('ELEC-TORRENT-AHM', 'Torrent Power Ltd - Ahmedabad', 'ELECTRICITY', 'Gujarat'),
    -- WATER
    ('WATER-DJB', 'Delhi Jal Board', 'WATER', 'Delhi'),
    ('WATER-BWSSB', 'Bangalore Water Supply and Sewerage Board (BWSSB)', 'WATER', 'Karnataka'),
    ('WATER-MCGM', 'Municipal Corporation of Greater Mumbai - Water', 'WATER', 'Maharashtra'),
    ('WATER-CMWSSB', 'Chennai Metro Water Supply (CMWSSB)', 'WATER', 'Tamil Nadu'),
    ('WATER-HMWSSB', 'Hyderabad Metro Water Supply Board (HMWSSB)', 'WATER', 'Telangana'),
    -- GAS
    ('GAS-MGL', 'Mahanagar Gas Limited (MGL)', 'GAS', 'Maharashtra'),
    ('GAS-IGL', 'Indraprastha Gas Limited (IGL)', 'GAS', 'Delhi'),
    ('GAS-ADANI-TOTAL', 'Adani Total Gas Limited', 'GAS', NULL),
    ('GAS-GUJARAT', 'Gujarat Gas Limited', 'GAS', 'Gujarat'),
    -- DTH
    ('DTH-TATAPLAY', 'Tata Play (formerly Tata Sky)', 'DTH', NULL),
    ('DTH-DISHTV', 'Dish TV India Ltd', 'DTH', NULL),
    ('DTH-AIRTEL', 'Airtel Digital TV', 'DTH', NULL),
    ('DTH-SUNDIRECT', 'Sun Direct DTH', 'DTH', NULL),
    ('DTH-D2H', 'd2h (Videocon d2h)', 'DTH', NULL),
    -- BROADBAND
    ('BB-AIRTEL-XSTREAM', 'Airtel Xstream Fiber', 'BROADBAND', NULL),
    ('BB-JIOFIBER', 'JioFiber', 'BROADBAND', NULL),
    ('BB-BSNL', 'BSNL Broadband', 'BROADBAND', NULL),
    ('BB-ACT', 'ACT Fibernet', 'BROADBAND', NULL),
    ('BB-HATHWAY', 'Hathway Broadband', 'BROADBAND', NULL),
    -- MOBILE_POSTPAID
    ('MOB-AIRTEL', 'Airtel Postpaid', 'MOBILE_POSTPAID', NULL),
    ('MOB-JIO', 'Jio Postpaid', 'MOBILE_POSTPAID', NULL),
    ('MOB-VI', 'Vi (Vodafone Idea) Postpaid', 'MOBILE_POSTPAID', NULL),
    ('MOB-BSNL', 'BSNL Postpaid', 'MOBILE_POSTPAID', NULL),
    -- LANDLINE
    ('LAND-BSNL', 'BSNL Landline', 'LANDLINE', NULL),
    ('LAND-MTNL', 'MTNL Landline', 'LANDLINE', NULL),
    ('LAND-AIRTEL', 'Airtel Landline', 'LANDLINE', NULL),
    -- INSURANCE_PREMIUM
    ('INS-LIC', 'Life Insurance Corporation of India (LIC)', 'INSURANCE_PREMIUM', NULL),
    ('INS-HDFCLIFE', 'HDFC Life Insurance', 'INSURANCE_PREMIUM', NULL),
    ('INS-ICICIPRU', 'ICICI Prudential Life Insurance', 'INSURANCE_PREMIUM', NULL),
    ('INS-SBILIFE', 'SBI Life Insurance', 'INSURANCE_PREMIUM', NULL),
    ('INS-MAXLIFE', 'Max Life Insurance', 'INSURANCE_PREMIUM', NULL)
ON CONFLICT (biller_id) DO NOTHING;
