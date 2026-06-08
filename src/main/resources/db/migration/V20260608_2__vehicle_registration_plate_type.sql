ALTER TABLE vehicle_details
    ADD COLUMN IF NOT EXISTS plate_type varchar(64) NOT NULL DEFAULT 'UNKNOWN';

UPDATE vehicle_details
SET plate_type = 'INDIVIDUAL'
WHERE plate_type = 'UNKNOWN'
  AND regexp_replace(upper(plate_number), '\s+', '', 'g') ~ '^[0-9]{2}[A-Z][0-9]{3}[A-Z]{2}$';

UPDATE vehicle_details
SET plate_type = 'LEGAL_ENTITY'
WHERE plate_type = 'UNKNOWN'
  AND regexp_replace(upper(plate_number), '\s+', '', 'g') ~ '^[0-9]{2}[0-9]{3}[A-Z]{3}$';

ALTER TABLE vehicle_details
    ADD CONSTRAINT chk_vehicle_details_plate_type
        CHECK (plate_type IN (
            'LEGAL_ENTITY',
            'LEGAL_ENTITY_TWO_LINE',
            'INDIVIDUAL',
            'INDIVIDUAL_TWO_LINE',
            'LEGAL_ENTITY_ECO',
            'LEGAL_ENTITY_ECO_TWO_LINE',
            'INDIVIDUAL_ECO',
            'INDIVIDUAL_ECO_TWO_LINE',
            'HIGH_GOVERNMENT',
            'HIGH_GOVERNMENT_TWO_LINE',
            'GOVERNMENT_DAV',
            'GOVERNMENT_DAV_TWO_LINE',
            'AMBULANCE_STY',
            'AMBULANCE_ECO_STY',
            'DIPLOMATIC_CMD',
            'DIPLOMATIC_CMD_TWO_LINE',
            'DIPLOMATIC_D',
            'DIPLOMATIC_D_TWO_LINE',
            'UN',
            'UN_TWO_LINE',
            'DIPLOMATIC_TECHNICAL_T',
            'DIPLOMATIC_TECHNICAL_T_TWO_LINE',
            'INTERNATIONAL_ORG_X',
            'INTERNATIONAL_ORG_X_TWO_LINE',
            'FOREIGN_ORG_M',
            'FOREIGN_ORG_M_TWO_LINE',
            'FOREIGN_INDIVIDUAL_H',
            'FOREIGN_INDIVIDUAL_H_TWO_LINE',
            'MOTORCYCLE',
            'ELECTRIC_MOTORCYCLE',
            'SCOOTER',
            'MOPED',
            'FOREIGN_MOTORCYCLE',
            'FOREIGN_MOPED_SCOOTER',
            'TRAILER',
            'TEMPORARY_TRANSIT',
            'TEMPORARY_TRANSIT_TWO_LINE',
            'UNKNOWN'
        ));

CREATE INDEX IF NOT EXISTS idx_vehicle_details_plate_type
    ON vehicle_details (plate_type);
