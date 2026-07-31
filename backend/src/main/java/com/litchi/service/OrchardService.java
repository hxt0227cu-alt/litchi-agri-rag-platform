package com.litchi.service;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.OrchardRequest;
import com.litchi.dto.OrchardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrchardService {
    private final MysqlStateStoreService mysqlStateStoreService;
    private final Map<String, CopyOnWriteArrayList<OrchardResponse>> fallback = new ConcurrentHashMap<>();

    public OrchardResponse create(AuthenticatedUser user, OrchardRequest request) {
        String now = Instant.now().toString();
        OrchardResponse response = OrchardResponse.builder()
                .id("orchard-" + UUID.randomUUID())
                .tenantId("tenant-default")
                .ownerId(user.id())
                .name(request.getName().trim())
                .location(clean(request.getLocation()))
                .variety(clean(request.getVariety()))
                .growthStage(clean(request.getGrowthStage()))
                .areaMu(request.getAreaMu())
                .createdAt(now)
                .updatedAt(now)
                .build();
        mysqlStateStoreService.saveOrchard(response);
        fallback.computeIfAbsent(user.id(), ignored -> new CopyOnWriteArrayList<>()).add(response);
        return response;
    }

    public List<OrchardResponse> list(AuthenticatedUser user) {
        if (mysqlStateStoreService.isActive()) {
            return mysqlStateStoreService.loadOrchards(user.id()).stream().map(this::toResponse).toList();
        }
        return List.copyOf(fallback.getOrDefault(user.id(), new CopyOnWriteArrayList<>()));
    }

    private OrchardResponse toResponse(MysqlStateStoreService.OrchardData data) {
        return OrchardResponse.builder()
                .id(data.getId())
                .tenantId(data.getTenantId())
                .ownerId(data.getOwnerId())
                .name(data.getName())
                .location(data.getLocation())
                .variety(data.getVariety())
                .growthStage(data.getGrowthStage())
                .areaMu(data.getAreaMu())
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .build();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
