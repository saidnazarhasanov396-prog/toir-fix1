ALTER TABLE integration_endpoints
    ADD COLUMN IF NOT EXISTS client_id varchar(255),
    ADD COLUMN IF NOT EXISTS client_secret varchar(255),
    ADD COLUMN IF NOT EXISTS company_inn varchar(32);

CREATE TABLE IF NOT EXISTS faktura_uz_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    endpoint_id uuid NOT NULL REFERENCES integration_endpoints(id),
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

CREATE INDEX IF NOT EXISTS idx_faktura_uz_doc32_parts_unique_id
    ON faktura_uz_doc32_parts (document_unique_id);

CREATE TABLE IF NOT EXISTS faktura_uz_import_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    endpoint_id uuid NOT NULL REFERENCES integration_endpoints(id),
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

CREATE INDEX IF NOT EXISTS idx_faktura_uz_import_history_endpoint
    ON faktura_uz_import_history (endpoint_id, imported_at DESC)
    WHERE is_deleted = false;
