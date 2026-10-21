ALTER TABLE project_project
    ADD COLUMN construction_content_raw TEXT NULL,
    ADD COLUMN product_promotion_content TEXT NULL,
    ADD COLUMN remark TEXT NULL;

UPDATE project_project
SET construction_content_raw = customer_need
WHERE construction_content_raw IS NULL;

ALTER TABLE project_project
    MODIFY COLUMN construction_content_raw TEXT NOT NULL;
