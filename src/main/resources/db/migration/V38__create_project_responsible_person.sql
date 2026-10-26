-- 独立保存项目职责与账号关联；职责不是系统权限。
ALTER TABLE project_project MODIFY province_leader_user_id BINARY(16) NULL;
CREATE TABLE project_responsible_person (
    id BINARY(16) NOT NULL PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    person_name VARCHAR(100) NOT NULL,
    responsibility VARCHAR(30) NULL,
    user_id BINARY(16) NULL,
    sort_order INT NOT NULL,
    province_slot BINARY(16) GENERATED ALWAYS AS
      (CASE WHEN responsibility = 'PROVINCE_LEADER' THEN project_id ELSE NULL END) STORED,
    CONSTRAINT fk_project_person_project FOREIGN KEY (project_id) REFERENCES project_project(id),
    CONSTRAINT fk_project_person_user FOREIGN KEY (user_id) REFERENCES identity_user_account(id),
    CONSTRAINT ck_project_person_role CHECK (responsibility IS NULL OR responsibility IN ('PROVINCE_LEADER','CITY_MANAGER','SUPPORT_EXPERT')),
    UNIQUE KEY uk_project_person_province (province_slot),
    UNIQUE KEY uk_project_person_account (project_id,user_id),
    KEY idx_project_person_name (person_name,project_id),
    KEY idx_project_person_order (project_id,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 保留旧三类职责；相同姓名不能证明是同一人，不擅自覆盖合并。
INSERT INTO project_responsible_person (id,project_id,person_name,responsibility,user_id,sort_order)
SELECT UUID_TO_BIN(UUID()),p.id,u.display_name,'PROVINCE_LEADER',u.id,0
FROM project_project p JOIN identity_user_account u ON u.id=p.province_leader_user_id;
INSERT INTO project_responsible_person (id,project_id,person_name,responsibility,sort_order)
SELECT UUID_TO_BIN(UUID()),id,TRIM(city_account_manager_name),'CITY_MANAGER',1
FROM project_project WHERE TRIM(city_account_manager_name) <> '';
INSERT INTO project_responsible_person (id,project_id,person_name,responsibility,sort_order)
SELECT UUID_TO_BIN(UUID()),project_id,expert_name,'SUPPORT_EXPERT',2+sort_order
FROM project_support_expert;
