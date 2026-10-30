package com.pluto.ndi.production.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.shared.imports.ImportTaskStore;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 仅声明模块存储空间，读写逻辑复用公共导入存储。 */
@Repository
public class ProductionImportMapper extends ImportTaskStore {
    public ProductionImportMapper(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        super(jdbc, json, "production");
    }
}
