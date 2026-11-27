package com.pluto.ndi.shared.filter;

import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.CommonErrorCode;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class ListFilters {
    private ListFilters() {}

    public static List<String> one(String value) {
        return value == null ? List.of() : List.of(value);
    }

    public static List<UUID> oneId(UUID value) {
        return value == null ? List.of() : List.of(value);
    }

    public static List<String> values(Collection<String> source) {
        if (source == null) return List.of();
        if (source.size() > 500 || source.stream().filter(Objects::nonNull).anyMatch(value -> value.length() > 100))
            throw invalid();
        return source.stream().filter(Objects::nonNull).map(String::strip)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    public static List<String> choices(Collection<String> source, Collection<String> allowed) {
        var result = values(source);
        if (!allowed.containsAll(result)) throw invalid();
        return result;
    }

    public static List<String> months(Collection<String> source) {
        return values(source).stream().map(value -> {
            if (value.matches("[0-9]{6}")) value = value.substring(0, 4) + "-" + value.substring(4);
            if (!value.matches("[0-9]{4}-(0[1-9]|1[0-2])") || YearMonth.parse(value).getYear() < 1900)
                throw invalid();
            return value;
        }).distinct().toList();
    }

    public static List<UUID> ids(Collection<String> source) {
        return values(source).stream().map(ListFilters::id).distinct().toList();
    }

    public static UUID id(String value) {
        try {
            var id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw invalid();
            return id;
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
    }

    public static void location(List<UUID> provinces, List<UUID> cities, Function<UUID, UUID> cityProvince) {
        if (cities.isEmpty()) return;
        if (provinces.size() > 1 || provinces.isEmpty() && cities.size() > 1) throw invalid();
        for (var city : cities) {
            var parent = cityProvince.apply(city);
            if (parent == null || !provinces.isEmpty() && !provinces.getFirst().equals(parent))
                throw invalid();
        }
    }

    public static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }
}
