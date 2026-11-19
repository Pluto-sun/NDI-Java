ALTER TABLE project_responsible_person DROP CHECK ck_project_person_role,
    ADD CONSTRAINT ck_project_person_role CHECK (
        responsibility IS NULL OR responsibility IN ('PROVINCE_LEADER','CITY_MANAGER','SUPPORT_EXPERT','SOLUTION_SUPPORT')
    );

ALTER TABLE project_approval_request ADD COLUMN proposed_planned_bid_month DATE NULL;
