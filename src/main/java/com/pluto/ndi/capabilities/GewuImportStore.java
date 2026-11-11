package com.pluto.ndi.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.shared.imports.ImportTaskStore;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GewuImportStore extends ImportTaskStore {
    public GewuImportStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        super(jdbc, json, "capability");
    }
}
