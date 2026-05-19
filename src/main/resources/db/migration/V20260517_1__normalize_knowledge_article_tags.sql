update knowledge_articles
set tags =
        case
            when tags is null then '[]'::jsonb
            when jsonb_typeof(tags) = 'array' then tags
            when jsonb_typeof(tags) = 'string' and nullif(tags #>> '{}', '') is null then '[]'::jsonb
            when jsonb_typeof(tags) = 'string' then jsonb_build_array(tags #>> '{}')
            else '[]'::jsonb
            end
where tags is null
   or jsonb_typeof(tags) <> 'array';

alter table knowledge_articles
    alter column tags set default '[]'::jsonb;

alter table knowledge_articles
    add constraint chk_knowledge_articles_tags_array
        check (tags is null or jsonb_typeof(tags) = 'array');