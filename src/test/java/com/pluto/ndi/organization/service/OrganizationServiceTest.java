package com.pluto.ndi.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import com.pluto.ndi.organization.domain.entity.CityEntity;
import com.pluto.ndi.organization.domain.entity.ProvinceEntity;
import com.pluto.ndi.organization.repository.CityRepository;
import com.pluto.ndi.organization.repository.ProvinceRepository;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {
    private final CityRepository cities = mock(CityRepository.class);
    private final ProvinceRepository provinces = mock(ProvinceRepository.class);
    private final OrganizationService service = new OrganizationService(cities, provinces);

    @Test
    void matchesShortAndFullNamesSymmetricallyAndWithinProvince() {
        var province = ProvinceEntity.create("广东省");
        var city = CityEntity.create(province.id(), "广州市");
        when(provinces.findByEnabledTrueOrderByProvinceNameAsc()).thenReturn(List.of(province));
        when(cities.findByProvinceIdAndEnabledTrueOrderByCityNameAsc(province.id())).thenReturn(List.of(city));
        assertThat(service.findEnabledLocationByNames("广东", "广州")).isPresent();
        assertThat(service.findEnabledLocationByNames(" 广东省 ", "广州市")).isPresent();
        assertThat(service.findEnabledLocationByNames("广东", "江西")).isEmpty();
    }

    @Test
    void rejectsAmbiguousCanonicalNamesAndMissingInput() {
        when(provinces.findByEnabledTrueOrderByProvinceNameAsc())
                .thenReturn(List.of(ProvinceEntity.create("广东省"), ProvinceEntity.create("广东")));
        assertThat(service.findEnabledLocationByNames("广东", "广州")).isEmpty();
        assertThat(service.findEnabledLocationByNames(null, "广州")).isEmpty();
        verifyNoInteractions(cities);
    }
}
