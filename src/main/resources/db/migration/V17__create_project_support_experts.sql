CREATE TABLE project_support_expert (
    project_id BINARY(16) NOT NULL,
    sort_order INT NOT NULL,
    expert_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (project_id, sort_order),
    CONSTRAINT fk_project_support_expert_project
        FOREIGN KEY (project_id) REFERENCES project_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
