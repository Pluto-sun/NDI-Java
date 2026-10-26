package com.pluto.ndi.projects.domain.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** 供按负责人筛选的关联查询使用；写入统一经项目行锁下的人员存储服务。 */
@Entity
@Table(name = "project_responsible_person")
public class ProjectResponsiblePersonEntity {
    @Id private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "person_name")
    private String name;

    protected ProjectResponsiblePersonEntity() {}
}
