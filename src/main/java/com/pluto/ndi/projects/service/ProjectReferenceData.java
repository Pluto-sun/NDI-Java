package com.pluto.ndi.projects.service;

import java.util.List;

import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.organization.service.ProvinceOption;

/** 项目页面使用的稳定基础选项。 */
public record ProjectReferenceData(
        List<ProvinceOption> provinces,
        List<CityOption> cities,
        List<CodeLabel> industries,
        List<CodeLabel> categories,
        List<CodeLabel> stages,
        List<CodeLabel> applicationScenarios
) {
    public ProjectReferenceData {
        provinces = List.copyOf(provinces);
        cities = List.copyOf(cities);
        industries = List.copyOf(industries);
        categories = List.copyOf(categories);
        stages = List.copyOf(stages);
        applicationScenarios = List.copyOf(applicationScenarios);
    }

    public record CodeLabel(String code, String label) { }
}
