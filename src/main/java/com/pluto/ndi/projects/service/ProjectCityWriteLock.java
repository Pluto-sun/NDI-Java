package com.pluto.ndi.projects.service;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 串行化同一地市的在线创建与 Excel 导入重名检查。 */
@Component
public class ProjectCityWriteLock {
    private final JdbcTemplate jdbc;

    public ProjectCityWriteLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(UUID cityId) {
        jdbc.queryForObject("SELECT id FROM organization_city WHERE id = ? FOR UPDATE",
                byte[].class, binary(cityId));
    }

    public byte[] binary(UUID value) {
        var buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
