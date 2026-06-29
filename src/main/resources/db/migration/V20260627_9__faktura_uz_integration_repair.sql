ALTER TABLE integration_endpoints
    ADD COLUMN IF NOT EXISTS client_id varchar(255),
    ADD COLUMN IF NOT EXISTS client_secret varchar(255),
    ADD COLUMN IF NOT EXISTS company_inn varchar(32);

CREATE TABLE IF NOT EXISTS faktura_uz_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    endpoint_id uuid NOT NULL,
    unique_id varchar(255) NOT NULL,
    roaming_uid varchar(255),
    type integer,
    title varchar(255),
    file_name varchar(255),
    total_price numeric(19, 2),
    contract varchar(255),
    created_date_time bigint,
    updated_date_time bigint,
    is_new boolean,
    status integer,
    organization_inn varchar(32),
    contractor_inn varchar(32),
    contractor_name varchar(255),
    owner_inn varchar(32),
    owner_name varchar(255),
    contractor_member_inn varchar(32),
    contractor_member_name varchar(255)
);

ALTER TABLE faktura_uz_documents
    ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS endpoint_id uuid,
    ADD COLUMN IF NOT EXISTS unique_id varchar(255),
    ADD COLUMN IF NOT EXISTS roaming_uid varchar(255),
    ADD COLUMN IF NOT EXISTS type integer,
    ADD COLUMN IF NOT EXISTS title varchar(255),
    ADD COLUMN IF NOT EXISTS file_name varchar(255),
    ADD COLUMN IF NOT EXISTS total_price numeric(19, 2),
    ADD COLUMN IF NOT EXISTS contract varchar(255),
    ADD COLUMN IF NOT EXISTS created_date_time bigint,
    ADD COLUMN IF NOT EXISTS updated_date_time bigint,
    ADD COLUMN IF NOT EXISTS is_new boolean,
    ADD COLUMN IF NOT EXISTS status integer,
    ADD COLUMN IF NOT EXISTS organization_inn varchar(32),
    ADD COLUMN IF NOT EXISTS contractor_inn varchar(32),
    ADD COLUMN IF NOT EXISTS contractor_name varchar(255),
    ADD COLUMN IF NOT EXISTS owner_inn varchar(32),
    ADD COLUMN IF NOT EXISTS owner_name varchar(255),
    ADD COLUMN IF NOT EXISTS contractor_member_inn varchar(32),
    ADD COLUMN IF NOT EXISTS contractor_member_name varchar(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_faktura_uz_documents_endpoint'
          AND conrelid = 'faktura_uz_documents'::regclass
    ) THEN
        ALTER TABLE faktura_uz_documents
            ADD CONSTRAINT fk_faktura_uz_documents_endpoint
            FOREIGN KEY (endpoint_id) REFERENCES integration_endpoints(id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_faktura_uz_documents_unique_id_active
    ON faktura_uz_documents (unique_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_faktura_uz_documents_endpoint_type_date
    ON faktura_uz_documents (endpoint_id, type, created_date_time DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_faktura_uz_documents_search
    ON faktura_uz_documents (lower(title), lower(contractor_name), lower(contractor_inn), lower(owner_name))
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS faktura_uz_document_contents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    document_unique_id varchar(255) NOT NULL,
    roaming_uid varchar(255),
    type integer,
    raw_content_json text
);

ALTER TABLE faktura_uz_document_contents
    ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS document_unique_id varchar(255),
    ADD COLUMN IF NOT EXISTS roaming_uid varchar(255),
    ADD COLUMN IF NOT EXISTS type integer,
    ADD COLUMN IF NOT EXISTS raw_content_json text;

CREATE UNIQUE INDEX IF NOT EXISTS ux_faktura_uz_document_contents_unique_id_active
    ON faktura_uz_document_contents (document_unique_id)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS faktura_uz_doc_type32 (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    document_unique_id varchar(255) NOT NULL,
    roaming_uid varchar(255),
    has_vat varchar(255),
    owner_inn varchar(32),
    owner_name varchar(255),
    owner_account varchar(255),
    owner_mfo varchar(255),
    owner_bank varchar(255),
    owner_address varchar(512),
    owner_phone varchar(255),
    client_inn varchar(32),
    client_name varchar(255),
    client_account varchar(255),
    client_mfo varchar(255),
    client_bank varchar(255),
    client_address varchar(512),
    client_phone varchar(255),
    contract_name varchar(255),
    contractor_inn varchar(32),
    contract_number varchar(255),
    contract_date date,
    contract_expire_date date,
    contract_place varchar(255),
    invoice_services_delivery_cost_total numeric(19, 2),
    invoice_services_vat_amount_total numeric(19, 2),
    invoice_services_total_price numeric(19, 2),
    invoice_services_total_price_in_words text,
    is_new_identity boolean
);

ALTER TABLE faktura_uz_doc_type32
    ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS document_unique_id varchar(255),
    ADD COLUMN IF NOT EXISTS roaming_uid varchar(255),
    ADD COLUMN IF NOT EXISTS has_vat varchar(255),
    ADD COLUMN IF NOT EXISTS owner_inn varchar(32),
    ADD COLUMN IF NOT EXISTS owner_name varchar(255),
    ADD COLUMN IF NOT EXISTS owner_account varchar(255),
    ADD COLUMN IF NOT EXISTS owner_mfo varchar(255),
    ADD COLUMN IF NOT EXISTS owner_bank varchar(255),
    ADD COLUMN IF NOT EXISTS owner_address varchar(512),
    ADD COLUMN IF NOT EXISTS owner_phone varchar(255),
    ADD COLUMN IF NOT EXISTS client_inn varchar(32),
    ADD COLUMN IF NOT EXISTS client_name varchar(255),
    ADD COLUMN IF NOT EXISTS client_account varchar(255),
    ADD COLUMN IF NOT EXISTS client_mfo varchar(255),
    ADD COLUMN IF NOT EXISTS client_bank varchar(255),
    ADD COLUMN IF NOT EXISTS client_address varchar(512),
    ADD COLUMN IF NOT EXISTS client_phone varchar(255),
    ADD COLUMN IF NOT EXISTS contract_name varchar(255),
    ADD COLUMN IF NOT EXISTS contractor_inn varchar(32),
    ADD COLUMN IF NOT EXISTS contract_number varchar(255),
    ADD COLUMN IF NOT EXISTS contract_date date,
    ADD COLUMN IF NOT EXISTS contract_expire_date date,
    ADD COLUMN IF NOT EXISTS contract_place varchar(255),
    ADD COLUMN IF NOT EXISTS invoice_services_delivery_cost_total numeric(19, 2),
    ADD COLUMN IF NOT EXISTS invoice_services_vat_amount_total numeric(19, 2),
    ADD COLUMN IF NOT EXISTS invoice_services_total_price numeric(19, 2),
    ADD COLUMN IF NOT EXISTS invoice_services_total_price_in_words text,
    ADD COLUMN IF NOT EXISTS is_new_identity boolean;

CREATE UNIQUE INDEX IF NOT EXISTS ux_faktura_uz_doc_type32_unique_id_active
    ON faktura_uz_doc_type32 (document_unique_id)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS faktura_uz_doc32_services (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    document_unique_id varchar(255) NOT NULL,
    price_per_item numeric(19, 2),
    price numeric(19, 2),
    summa numeric(19, 2),
    delivery_cost numeric(19, 2),
    vat_rate double precision,
    vat_amount numeric(19, 2),
    vat_rate_display varchar(255),
    vat_amount_display varchar(255),
    delivery_cost_with_vat numeric(19, 2),
    delivery_cost_with_vat_display varchar(255),
    tax_rate varchar(255),
    tax_amount varchar(255),
    delivery_cost_with_taxes numeric(19, 2),
    delivery_cost_with_taxes_display varchar(255),
    catalog_code varchar(255),
    catalog_name varchar(255),
    catalog_package_names varchar(255),
    catalog_title text,
    barcode varchar(255),
    number varchar(255),
    title text,
    measurement varchar(255),
    measurement_code varchar(255),
    quantity double precision
);

ALTER TABLE faktura_uz_doc32_services
    ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS document_unique_id varchar(255),
    ADD COLUMN IF NOT EXISTS price_per_item numeric(19, 2),
    ADD COLUMN IF NOT EXISTS price numeric(19, 2),
    ADD COLUMN IF NOT EXISTS summa numeric(19, 2),
    ADD COLUMN IF NOT EXISTS delivery_cost numeric(19, 2),
    ADD COLUMN IF NOT EXISTS vat_rate double precision,
    ADD COLUMN IF NOT EXISTS vat_amount numeric(19, 2),
    ADD COLUMN IF NOT EXISTS vat_rate_display varchar(255),
    ADD COLUMN IF NOT EXISTS vat_amount_display varchar(255),
    ADD COLUMN IF NOT EXISTS delivery_cost_with_vat numeric(19, 2),
    ADD COLUMN IF NOT EXISTS delivery_cost_with_vat_display varchar(255),
    ADD COLUMN IF NOT EXISTS tax_rate varchar(255),
    ADD COLUMN IF NOT EXISTS tax_amount varchar(255),
    ADD COLUMN IF NOT EXISTS delivery_cost_with_taxes numeric(19, 2),
    ADD COLUMN IF NOT EXISTS delivery_cost_with_taxes_display varchar(255),
    ADD COLUMN IF NOT EXISTS catalog_code varchar(255),
    ADD COLUMN IF NOT EXISTS catalog_name varchar(255),
    ADD COLUMN IF NOT EXISTS catalog_package_names varchar(255),
    ADD COLUMN IF NOT EXISTS catalog_title text,
    ADD COLUMN IF NOT EXISTS barcode varchar(255),
    ADD COLUMN IF NOT EXISTS number varchar(255),
    ADD COLUMN IF NOT EXISTS title text,
    ADD COLUMN IF NOT EXISTS measurement varchar(255),
    ADD COLUMN IF NOT EXISTS measurement_code varchar(255),
    ADD COLUMN IF NOT EXISTS quantity double precision;

CREATE INDEX IF NOT EXISTS idx_faktura_uz_doc32_services_unique_id
    ON faktura_uz_doc32_services (document_unique_id);

CREATE TABLE IF NOT EXISTS faktura_uz_doc32_parts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    document_unique_id varchar(255) NOT NULL,
    number varchar(255),
    title text,
    body text
);

ALTER TABLE faktura_uz_doc32_parts
    ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS document_unique_id varchar(255),
    ADD COLUMN IF NOT EXISTS number varchar(255),
    ADD COLUMN IF NOT EXISTS title text,
    ADD COLUMN IF NOT EXISTS body text;

CREATE INDEX IF NOT EXISTS idx_faktura_uz_doc32_parts_unique_id
    ON faktura_uz_doc32_parts (document_unique_id);

CREATE TABLE IF NOT EXISTS faktura_uz_import_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    endpoint_id uuid NOT NULL,
    document_type varchar(64) NOT NULL,
    imported_at timestamp NOT NULL,
    description text,
    import_request_date_from date,
    import_request_date_to date,
    total_data_count_in_request bigint,
    total_saved_data_count bigint,
    total_updated_data_count bigint,
    total_failed_data_count bigint
);

ALTER TABLE faktura_uz_import_history
    ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS endpoint_id uuid,
    ADD COLUMN IF NOT EXISTS document_type varchar(64),
    ADD COLUMN IF NOT EXISTS imported_at timestamp,
    ADD COLUMN IF NOT EXISTS description text,
    ADD COLUMN IF NOT EXISTS import_request_date_from date,
    ADD COLUMN IF NOT EXISTS import_request_date_to date,
    ADD COLUMN IF NOT EXISTS total_data_count_in_request bigint,
    ADD COLUMN IF NOT EXISTS total_saved_data_count bigint,
    ADD COLUMN IF NOT EXISTS total_updated_data_count bigint,
    ADD COLUMN IF NOT EXISTS total_failed_data_count bigint;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_faktura_uz_import_history_endpoint'
          AND conrelid = 'faktura_uz_import_history'::regclass
    ) THEN
        ALTER TABLE faktura_uz_import_history
            ADD CONSTRAINT fk_faktura_uz_import_history_endpoint
            FOREIGN KEY (endpoint_id) REFERENCES integration_endpoints(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_faktura_uz_import_history_endpoint
    ON faktura_uz_import_history (endpoint_id, imported_at DESC)
    WHERE is_deleted = false;
