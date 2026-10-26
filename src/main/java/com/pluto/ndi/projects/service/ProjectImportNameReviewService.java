package com.pluto.ndi.projects.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.organization.service.OrganizationLocation;
import com.pluto.ndi.projects.domain.ProjectImportNameReview;
import com.pluto.ndi.projects.domain.ProjectImportNameReview.Match;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.util.ProjectNameMatcher;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ProjectImportNameReviewService {
    private final ProjectRepository projects;
    private final OrganizationDirectory organizations;
    private final ObjectMapper json;
    private final IdentityDirectory identity;

    public ProjectImportNameReviewService(
            ProjectRepository projects,
            OrganizationDirectory organizations,
            ObjectMapper json,
            IdentityDirectory identity) {
        this.projects = projects;
        this.organizations = organizations;
        this.json = json;
        this.identity = identity;
    }

    public ProjectImportNameReview read(ProjectImportRowEntity row) {
        if (row.nameReview() == null) return null;
        try {
            return json.readValue(row.nameReview(), ProjectImportNameReview.class);
        } catch (Exception error) {
            throw new IllegalStateException("查重结果解析失败", error);
        }
    }

    public Map<String, String> source(ProjectImportRowEntity row) {
        try {
            return json.readValue(row.sourcePayload(), new TypeReference<>() {});
        } catch (Exception error) {
            throw new IllegalStateException("导入原始行解析失败", error);
        }
    }

    public void refresh(UUID actorId, List<ProjectImportRowEntity> rows) {
        var scope = scope(actorId);
        var cores = new HashMap<String, String>();
        var prior = new HashMap<UUID, List<Match>>();
        var database = new HashMap<UUID, List<Match>>();
        var locations = new HashMap<String, Optional<OrganizationLocation>>();
        for (var row : rows) {
            if (!Set.of("READY", "FAILED").contains(row.status()) || row.sourcePayload() == null)
                continue;
            var cells = source(row);
            String name = cells.getOrDefault("项目名称", "");
            String province = cells.getOrDefault("省分", ""), city = cells.getOrDefault("地市", "");
            var location =
                    locations.computeIfAbsent(
                            province + "\n" + city,
                            key -> organizations.findEnabledLocationByNames(province, city));
            if (location.isEmpty()
                    || name.isBlank()
                    || !readable(scope, location.get().provinceId(), location.get().cityId())) {
                row.nameReview(null);
                continue;
            }
            UUID cityId = location.get().cityId();
            var existing = database.computeIfAbsent(cityId, key -> database(key));
            var matches = new ArrayList<Match>(existing);
            matches.addAll(prior.getOrDefault(cityId, List.of()));
            var review =
                    build(location.get().provinceId(), cityId, name, matches, read(row), cores);
            row.nameReview(write(review));
            prior.computeIfAbsent(cityId, key -> new ArrayList<>())
                    .add(new Match(null, null, name, row.rowNumber(), 0));
        }
    }

    private List<Match> database(UUID city) {
        return projects.findNamesByCityId(city).stream()
                .map(p -> new Match(p.projectId(), p.projectCode(), p.projectName(), null, 0))
                .toList();
    }

    private ProjectImportNameReview build(
            UUID province,
            UUID city,
            String name,
            List<Match> options,
            ProjectImportNameReview previous,
            Map<String, String> cores) {
        var matches =
                options.stream()
                        .map(
                                p ->
                                        new Match(
                                                p.projectId(),
                                                p.projectCode(),
                                                p.projectName(),
                                                p.sourceRowNumber(),
                                                score(name, p.projectName(), cores)))
                        .filter(p -> p.score() >= 0.8)
                        .sorted(
                                Comparator.comparingDouble(Match::score)
                                        .reversed()
                                        .thenComparing(Match::projectName)
                                        .thenComparing(
                                                p -> p.projectCode() == null ? "" : p.projectCode())
                                        .thenComparing(
                                                p ->
                                                        p.sourceRowNumber() == null
                                                                ? 0
                                                                : p.sourceRowNumber()))
                        .toList();
        var evidence =
                matches.stream()
                        .map(p -> ProjectNameMatcher.standard(p.projectName()))
                        .distinct()
                        .sorted()
                        .toList();
        String fingerprint =
                hash(city + "|" + ProjectNameMatcher.standard(name) + "|v1|" + write(evidence));
        double score = matches.isEmpty() ? 0 : matches.getFirst().score();
        boolean retained = previous != null && fingerprint.equals(previous.fingerprint());
        return new ProjectImportNameReview(
                province,
                city,
                score >= 0.92 ? "STRONG" : score >= 0.8 ? "WEAK" : "NONE",
                score,
                fingerprint,
                evidence,
                matches.stream().limit(5).toList(),
                retained && previous.confirmed(),
                retained ? previous.reason() : null);
    }

    private double score(String left, String right, Map<String, String> cores) {
        String a = cores.computeIfAbsent(left, ProjectNameMatcher::core),
                b = cores.computeIfAbsent(right, ProjectNameMatcher::core);
        int maximum = Math.max(a.length(), b.length());
        // 两种分数的平均值上界为 0.4+0.6×长度比；不可能达阈值的组合无需做编辑距离。
        if (maximum > 0 && 0.4 + 0.6 * Math.min(a.length(), b.length()) / maximum < 0.8) return 0;
        return ProjectNameMatcher.scoreCore(a, b);
    }

    public void verifyLive(ProjectImportRowEntity row, UUID city, String name) {
        var review = read(row);
        if (review == null) return; // 兼容升级前已经预检、正在恢复的批次。
        if (!review.accepted()) throw new IllegalStateException("相似名称尚未确认或缺少差异说明");
        var latest = build(review.provinceId(), city, name, database(city), null, new HashMap<>());
        if (!review.evidence().containsAll(latest.evidence()))
            throw new IllegalStateException("确认前目标库出现新的相似项目，请用新文件重新预检");
    }

    private ProjectDataScope scope(UUID actor) {
        return identity.findProjectActor(actor)
                .flatMap(identity::findReadableProjectScope)
                .orElse(null);
    }

    private boolean readable(ProjectDataScope scope, UUID province, UUID city) {
        if (scope == null) return false;
        return switch (scope.level()) {
            case NATIONAL -> true;
            case PROVINCE -> scope.provinceIds().contains(province);
            case CITY -> scope.organizationId().equals(city);
        };
    }

    public Map<UUID, ProjectImportNameReview> visibleReports(
            UUID actor, List<ProjectImportRowEntity> rows) {
        var scope = scope(actor);
        var result = new HashMap<UUID, ProjectImportNameReview>();
        for (var row : rows) {
            var review = read(row);
            if (review != null && readable(scope, review.provinceId(), review.cityId()))
                result.put(row.id(), review);
        }
        return result;
    }

    public String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("导入查重结果序列化失败", error);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
