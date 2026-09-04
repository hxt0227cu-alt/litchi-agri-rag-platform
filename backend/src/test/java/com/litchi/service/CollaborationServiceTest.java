package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.CreateConsultationRequest;
import com.litchi.dto.RemedyPlanDto;
import com.litchi.dto.SaveRemedyPlanRequest;
import com.litchi.dto.UpsertStoreProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationServiceTest {

    @TempDir
    Path tempDir;

    private CollaborationService collaborationService;
    private MysqlStateStoreService mysqlStateStoreService;
    private Path stateFile;
    private AuthenticatedUser farmerUser;
    private AuthenticatedUser shopkeeperUser;
    private AuthenticatedUser outsiderShopkeeper;

    @BeforeEach
    void setUp() {
        stateFile = tempDir.resolve("collaboration-state.json");
        mysqlStateStoreService = mock(MysqlStateStoreService.class);
        when(mysqlStateStoreService.isActive()).thenReturn(false);
        collaborationService = new CollaborationService(new ObjectMapper(), mysqlStateStoreService);
        ReflectionTestUtils.setField(collaborationService, "stateFile", stateFile.toString());
        collaborationService.init();

        farmerUser = new AuthenticatedUser("farmer-1", "farmer", "farmer", "2026-03-16T00:00:00Z");
        shopkeeperUser = new AuthenticatedUser("shop-1", "shopkeeper", "shopkeeper", "2026-03-16T00:00:00Z");
        outsiderShopkeeper = new AuthenticatedUser("shop-2", "outsider", "shopkeeper", "2026-03-16T00:00:00Z");
    }

    @Test
    void recommendationsSupportConsultationInboxAndTrendFlow() {
        var recommendations = collaborationService.getRecommendations("炭疽病", "雨季高湿", "", farmerUser);

        assertFalse(recommendations.isEmpty());
        assertEquals("炭疽病", recommendations.get(0).getDiseaseTag());
        var selectedPlan = recommendations.stream()
                .filter(item -> "炭疽病".equals(item.getDiseaseTag()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least one anthracnose recommendation"));

        CreateConsultationRequest request = new CreateConsultationRequest();
        request.setPlanId(selectedPlan.getPlanId());
        request.setDiseaseTag("炭疽病");
        request.setStageTag("雨季高湿");
        request.setQuestion("果面褐斑持续扩大怎么办？");
        request.setReasonTags(List.of("病症高度匹配", "当前可承接"));

        var created = collaborationService.createConsultation(farmerUser, request);
        assertEquals("pending", created.getStatus());
        assertEquals("荔园农资服务站", created.getShopName());

        var myConsultations = collaborationService.listMyConsultations(farmerUser, 1, 10).getItems();
        assertEquals(1, myConsultations.size());
        assertEquals(created.getId(), myConsultations.get(0).getId());

        var inbox = collaborationService.listInbox(shopkeeperUser, 1, 10).getItems();
        assertEquals(1, inbox.size());
        assertEquals(created.getId(), inbox.get(0).getId());

        var updated = collaborationService.updateConsultationStatus(shopkeeperUser, created.getId(), "contacted");
        assertEquals("contacted", updated.getStatus());

        var trends = collaborationService.getTrends();
        assertEquals("炭疽病", trends.get(0).getDiseaseTag());
        assertTrue(trends.stream().anyMatch(item ->
                "炭疽病".equals(item.getDiseaseTag()) && item.getTotalConsultations() >= 1
        ));
    }

    @Test
    void createdPlansPersistAcrossReload() {
        UpsertStoreProfileRequest profileRequest = new UpsertStoreProfileRequest();
        profileRequest.setShopName("演示门店");
        profileRequest.setContactName("张店长");
        profileRequest.setPhone("13800009999");
        profileRequest.setWechat("demo-shop");
        profileRequest.setAddress("广西示范果园");
        profileRequest.setServiceArea("桂味主产区");
        profileRequest.setSpecialties("病害协同");
        profileRequest.setRating(4.9);
        collaborationService.upsertProfile(shopkeeperUser, profileRequest);

        SaveRemedyPlanRequest planRequest = new SaveRemedyPlanRequest();
        planRequest.setTitle("花果期综合管理方案");
        planRequest.setDiseaseTag("雨季综合管理");
        planRequest.setStageTag("花果期");
        planRequest.setSummary("用于答辩演示的门店方案。");
        planRequest.setProducts(List.of("营养补充包", "巡园检查表"));
        planRequest.setUsageTips(List.of("雨后复查", "先排水后处理"));
        planRequest.setRiskNotes(List.of("不能脱离标签自行加量"));
        planRequest.setInventoryStatus("有现货");
        planRequest.setActive(true);

        RemedyPlanDto created = collaborationService.createPlan(shopkeeperUser, planRequest);
        assertNotNull(created.getId());

        CollaborationService reloaded = new CollaborationService(new ObjectMapper(), mysqlStateStoreService);
        ReflectionTestUtils.setField(reloaded, "stateFile", stateFile.toString());
        reloaded.init();

        var plans = reloaded.listPlans(shopkeeperUser, 1, 10).getItems();
        assertTrue(plans.stream().anyMatch(plan ->
                created.getId().equals(plan.getId()) && "花果期综合管理方案".equals(plan.getTitle())
        ));
    }

    @Test
    void outsiderCannotHandleForeignConsultation() {
        var plan = collaborationService.getRecommendations("炭疽病", "雨季高湿", "", farmerUser).get(0);

        CreateConsultationRequest request = new CreateConsultationRequest();
        request.setPlanId(plan.getPlanId());
        request.setDiseaseTag("炭疽病");
        request.setStageTag("雨季高湿");
        request.setQuestion("需要先处理病枝还是先联系门店？");

        var created = collaborationService.createConsultation(farmerUser, request);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> collaborationService.updateConsultationStatus(outsiderShopkeeper, created.getId(), "completed")
        );

        assertEquals("当前账号不能处理这条求助。", exception.getMessage());
    }

    @Test
    void reloadSanitizesPlaceholderConsultationFields() throws Exception {
        String brokenState = """
                {
                  "profiles": [
                    {
                      "shopId": "shop-demo-main",
                      "ownerId": null,
                      "ownerUsername": "shopkeeper",
                      "shopName": "荔园农资服务站",
                      "contactName": "门店店主",
                      "phone": "13800001234",
                      "wechat": "litchi-shopkeeper",
                      "address": "广西某示范果园服务点",
                      "serviceArea": "桂味、妃子笑主产区",
                      "specialties": "病害咨询、雨季管理、门店回访",
                      "rating": 4.8,
                      "createdAt": "2026-03-26T00:00:00Z",
                      "updatedAt": "2026-03-26T00:00:00Z"
                    }
                  ],
                  "plans": [
                    {
                      "id": "plan-demo-anthracnose",
                      "shopId": "shop-demo-main",
                      "ownerId": null,
                      "ownerUsername": "shopkeeper",
                      "shopName": "荔园农资服务站",
                      "title": "炭疽病雨季处理方案",
                      "diseaseTag": "炭疽病",
                      "stageTag": "雨季高湿",
                      "summary": "用于校验脏数据自愈。",
                      "products": ["吡唑醚菌酯"],
                      "usageTips": ["及时清园"],
                      "riskNotes": ["遵循标签用量"],
                      "inventoryStatus": "有现货",
                      "active": true,
                      "createdAt": "2026-03-26T00:00:00Z",
                      "updatedAt": "2026-03-26T00:00:00Z"
                    }
                  ],
                  "consultations": [
                    {
                      "id": "consult-broken",
                      "farmerUserId": "farmer-1",
                      "farmerUsername": "farmer",
                      "diseaseTag": "???",
                      "stageTag": "???",
                      "question": "???",
                      "planId": "plan-demo-anthracnose",
                      "planTitle": "炭疽病雨季处理方案",
                      "shopId": "shop-demo-main",
                      "shopName": "???",
                      "status": "pending",
                      "reasonTags": ["???", "病症高度匹配"],
                      "createdAt": "2026-03-26T00:00:00Z",
                      "updatedAt": "2026-03-26T00:00:00Z"
                    }
                  ]
                }
                """;
        Files.writeString(stateFile, brokenState, StandardCharsets.UTF_8);

        CollaborationService reloaded = new CollaborationService(new ObjectMapper(), mysqlStateStoreService);
        ReflectionTestUtils.setField(reloaded, "stateFile", stateFile.toString());
        reloaded.init();

        var consultation = reloaded.listMyConsultations(farmerUser, 1, 10).getItems().get(0);
        assertEquals("炭疽病", consultation.getDiseaseTag());
        assertEquals("雨季高湿", consultation.getStageTag());
        assertEquals("荔园农资服务站", consultation.getShopName());
        assertEquals(List.of("病症高度匹配"), consultation.getReasonTags());
        assertTrue(consultation.getQuestion().contains("炭疽病雨季处理方案"));
    }

    @Test
    void createPlanIsIdempotentByKey() {
        collaborationService.upsertProfile(shopkeeperUser, new UpsertStoreProfileRequest() {{
            setShopName("幂等演示门店");
            setContactName("店长");
            setPhone("13800008888");
        }});

        SaveRemedyPlanRequest first = new SaveRemedyPlanRequest();
        first.setTitle("幂等测试方案");
        first.setDiseaseTag("炭疽病");
        first.setStageTag("雨季高湿");
        first.setSummary("同一幂等键重复提交只落一条。");
        first.setActive(false);
        first.setIdempotencyKey("run-demo-idem-001");

        RemedyPlanDto created = collaborationService.createPlan(shopkeeperUser, first);
        RemedyPlanDto repeated = collaborationService.createPlan(shopkeeperUser, first);

        assertEquals(created.getId(), repeated.getId(), "同一幂等键重复创建必须返回既有方案");
        assertEquals("run-demo-idem-001", repeated.getIdempotencyKey());

        var all = collaborationService.listPlans(shopkeeperUser, 1, 50).getItems();
        long matched = all.stream()
                .filter(plan -> "run-demo-idem-001".equals(plan.getIdempotencyKey()))
                .count();
        assertEquals(1, matched, "幂等键相同的方案在列表中只能出现一次");

        // 不同幂等键不受影响，可正常创建新方案
        SaveRemedyPlanRequest other = new SaveRemedyPlanRequest();
        other.setTitle("另一个方案");
        other.setDiseaseTag("霜霉病");
        other.setStageTag("发病初期");
        other.setSummary("不同键应新建。");
        other.setActive(false);
        other.setIdempotencyKey("run-demo-idem-002");
        RemedyPlanDto second = collaborationService.createPlan(shopkeeperUser, other);
        assertFalse(second.getId().equals(created.getId()), "不同幂等键必须新建方案");
    }

    @Test
    void createPlanWithoutKeyAlwaysCreatesNew() {
        collaborationService.upsertProfile(shopkeeperUser, new UpsertStoreProfileRequest() {{
            setShopName("无键门店");
            setContactName("店长");
            setPhone("13800007777");
        }});

        SaveRemedyPlanRequest request = new SaveRemedyPlanRequest();
        request.setTitle("无幂等键方案");
        request.setDiseaseTag("炭疽病");
        request.setStageTag("雨季高湿");
        request.setSummary("未设置幂等键时不查重，保持原行为。");
        request.setActive(false);

        RemedyPlanDto a = collaborationService.createPlan(shopkeeperUser, request);
        RemedyPlanDto b = collaborationService.createPlan(shopkeeperUser, request);
        assertFalse(a.getId().equals(b.getId()), "无幂等键时重复提交应各自新建（兼容非 Agent 调用）");
    }

    @Test
    void localStateIsMigratedToMysqlWhenStructuredStorageIsActive() throws Exception {
        when(mysqlStateStoreService.isActive()).thenReturn(true);
        when(mysqlStateStoreService.loadCollaborationState()).thenReturn(java.util.Optional.empty());

        String localState = """
                {
                  "profiles": [
                    {
                      "shopId": "shop-local",
                      "ownerId": "shop-1",
                      "ownerUsername": "shopkeeper",
                      "shopName": "本地门店",
                      "contactName": "店长",
                      "phone": "13800001111",
                      "wechat": "local-shop",
                      "address": "广西本地示范点",
                      "serviceArea": "桂味产区",
                      "specialties": "协同演示",
                      "rating": 4.6,
                      "createdAt": "2026-03-26T00:00:00Z",
                      "updatedAt": "2026-03-26T00:00:00Z"
                    }
                  ],
                  "plans": [],
                  "consultations": []
                }
                """;
        Files.writeString(stateFile, localState, StandardCharsets.UTF_8);

        CollaborationService reloaded = new CollaborationService(new ObjectMapper(), mysqlStateStoreService);
        ReflectionTestUtils.setField(reloaded, "stateFile", stateFile.toString());
        reloaded.init();

        verify(mysqlStateStoreService, atLeastOnce())
                .saveCollaborationState(any(MysqlStateStoreService.CollaborationStateData.class));
        assertEquals("本地门店", reloaded.getProfile(shopkeeperUser).getShopName());
    }
}
