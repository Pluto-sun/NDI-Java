ALTER TABLE project_project
    ADD KEY idx_project_project_province_stage (province_id, current_stage_code),
    ADD KEY idx_project_project_city_stage (city_id, current_stage_code);
