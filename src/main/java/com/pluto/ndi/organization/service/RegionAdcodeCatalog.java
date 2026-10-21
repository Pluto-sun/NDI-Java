package com.pluto.ndi.organization.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 将项目组织名称映射为地图边界使用的六位行政区划代码。 */
@Component
public class RegionAdcodeCatalog {

    private final Map<String, String> provinces;
    private final Map<CityKey, String> cities;

    public RegionAdcodeCatalog(ObjectMapper objectMapper) {
        var provinceCodes = new HashMap<String, String>();
        var cityCodes = new HashMap<CityKey, String>();
        try (var input = new ClassPathResource("regions/region_catalog.json").getInputStream()) {
            var catalog = objectMapper.readTree(input);
            for (var province : catalog.path("provinces")) {
                var provinceName = normalize(province.path("name").asText());
                provinceCodes.put(provinceName, adcode(province.path("sourceCode").asText()));
                for (var city : province.path("cities")) {
                    cityCodes.put(new CityKey(provinceName, normalize(city.path("name").asText())),
                            adcode(city.path("sourceCode").asText()));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("行政区划代码目录加载失败", exception);
        }
        provinces = Map.copyOf(provinceCodes);
        cities = Map.copyOf(cityCodes);
    }

    public String province(String name) { return provinces.get(normalize(name)); }
    public String city(String provinceName, String cityName) {
        return cities.get(new CityKey(normalize(provinceName), normalize(cityName)));
    }

    private String normalize(String name) {
        if (name == null) return "";
        for (var suffix : new String[] {"维吾尔自治区", "壮族自治区", "回族自治区",
                "特别行政区", "自治区", "自治州", "地区", "省", "市"}) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }

    private String adcode(String sourceCode) {
        return sourceCode.length() == 4 ? sourceCode + "00" : sourceCode + "0000";
    }

    private record CityKey(String provinceName, String cityName) { }
}
