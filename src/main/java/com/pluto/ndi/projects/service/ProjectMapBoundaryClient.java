package com.pluto.ndi.projects.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 按固定地址获取地图边界，并以有限内存缓存最近访问的层级。 */
@Component
public class ProjectMapBoundaryClient {

    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 8;
        }
    });
    private final ObjectMapper objectMapper;
    private final String boundaryBaseUrl;

    public ProjectMapBoundaryClient(ObjectMapper objectMapper,
            @Value("${ndi.projects.map.boundary-base-url:https://geo.datav.aliyun.com/areas_v3/bound}")
            String boundaryBaseUrl) {
        this.objectMapper = objectMapper;
        this.boundaryBaseUrl = boundaryBaseUrl.replaceAll("/+$", "");
    }

    public byte[] fetch(String adcode) {
        var cached = cache.get(adcode);
        if (cached != null) return cached;
        var request = HttpRequest.newBuilder(URI.create(boundaryBaseUrl + "/" + adcode + "_full.json"))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new BusinessException(ProjectErrorCode.MAP_BOUNDARY_UNAVAILABLE);
                }
                var content = body.readNBytes(MAX_BYTES + 1);
                if (content.length > MAX_BYTES
                        || !"FeatureCollection".equals(objectMapper.readTree(content).path("type").asText())) {
                    throw new BusinessException(ProjectErrorCode.MAP_BOUNDARY_UNAVAILABLE);
                }
                cache.put(adcode, content);
                return content;
            }
        } catch (IOException exception) {
            throw new BusinessException(ProjectErrorCode.MAP_BOUNDARY_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ProjectErrorCode.MAP_BOUNDARY_UNAVAILABLE);
        }
    }
}
