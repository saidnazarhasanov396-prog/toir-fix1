alter table ppr_tasks
    add column if not exists source_type varchar(32),
    add column if not exists source_key varchar(160);

alter table ppr_tasks
    add constraint ck_ppr_tasks_source_pair
        check ((source_type is null and source_key is null)
            or (source_type is not null and source_key is not null));

create unique index if not exists uq_ppr_tasks_active_rcm_source_key
    on ppr_tasks (source_key)
    where is_deleted = false and source_type = 'RCM_AUTO_PLAN';
