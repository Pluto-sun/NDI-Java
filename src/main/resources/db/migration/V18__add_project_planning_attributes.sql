ALTER TABLE project_project
    ADD COLUMN planned_bid_month DATE NULL;

CREATE TABLE project_application_scenario (
    project_id BINARY(16) NOT NULL,
    sort_order INT NOT NULL,
    scenario_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (project_id, sort_order),
    UNIQUE KEY uk_project_application_scenario_code (project_id, scenario_code),
    CONSTRAINT fk_project_application_scenario_project
        FOREIGN KEY (project_id) REFERENCES project_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
