package com.litchi.controller;

import com.litchi.audit.AuditLogInterceptor;
import com.litchi.auth.AuthInterceptor;
import com.litchi.auth.AuthService;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.config.WebConfig;
import com.litchi.dto.AuthResponse;
import com.litchi.dto.AuthUserView;
import com.litchi.dto.ConsultationRecordDto;
import com.litchi.dto.DocumentRecord;
import com.litchi.dto.EvaluationRecordDto;
import com.litchi.dto.FeedbackRecordDto;
import com.litchi.dto.FeedbackStatsResponse;
import com.litchi.dto.PageResponse;
import com.litchi.dto.RecommendedPlanDto;
import com.litchi.dto.RemedyPlanDto;
import com.litchi.dto.StoreProfileDto;
import com.litchi.service.CollaborationService;
import com.litchi.service.DataInitializer;
import com.litchi.service.DemoContentService;
import com.litchi.service.DiagnosisService;
import com.litchi.service.DocumentService;
import com.litchi.service.EvaluationService;
import com.litchi.service.FeedbackService;
import com.litchi.service.KnowledgeGraphService;
import com.litchi.service.LLMService;
import com.litchi.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        CollaborationController.class,
        EvaluationController.class,
        DocumentController.class,
        FeedbackController.class,
        KnowledgeGraphController.class,
        SystemController.class
})
@Import({WebConfig.class, AuditLogInterceptor.class, AuthInterceptor.class, GlobalExceptionHandler.class})
class PlatformApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private EvaluationService evaluationService;

    @MockBean
    private CollaborationService collaborationService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private DataInitializer dataInitializer;

    @MockBean
    private KnowledgeGraphService knowledgeGraphService;

    @MockBean
    private VectorSearchService vectorSearchService;

    @MockBean
    private LLMService llmService;

    @MockBean
    private DiagnosisService diagnosisService;

    @MockBean
    private DemoContentService demoContentService;

    private AuthenticatedUser technicianUser;
    private AuthenticatedUser farmerUser;
    private AuthenticatedUser shopkeeperUser;
    private AuthUserView technicianView;

    @BeforeEach
    void setUp() {
        technicianUser = new AuthenticatedUser("tech-1", "technician", "technician", "2026-03-16T00:00:00Z");
        farmerUser = new AuthenticatedUser("farmer-1", "farmer", "farmer", "2026-03-16T00:00:00Z");
        shopkeeperUser = new AuthenticatedUser("shop-1", "shopkeeper", "shopkeeper", "2026-03-16T00:00:00Z");
        technicianView = AuthUserView.builder()
                .id("tech-1")
                .username("technician")
                .role("technician")
                .createdAt("2026-03-16T00:00:00Z")
                .build();

        when(authService.resolveUser("technician-token")).thenReturn(technicianUser);
        when(authService.resolveUser("farmer-token")).thenReturn(farmerUser);
        when(authService.resolveUser("shopkeeper-token")).thenReturn(shopkeeperUser);
        when(authService.me("technician-token")).thenReturn(technicianView);

        when(knowledgeGraphService.isNeo4jAvailable()).thenReturn(true);
        when(knowledgeGraphService.getVisualizationData(nullable(String.class)))
                .thenReturn(Map.of("nodes", List.of(), "edges", List.of()));
        when(vectorSearchService.isAvailable()).thenReturn(true);
        when(llmService.isAvailable()).thenReturn(true);
        when(diagnosisService.getHealth())
                .thenReturn(new DiagnosisService.HealthStatus(true, false, true, "ultralytics-yolo", "connected"));
        when(documentService.countDocuments()).thenReturn(5);
        when(documentService.countIndexedDocuments()).thenReturn(5);
        when(demoContentService.getSampleDocuments()).thenReturn(List.of(
                Map.of("name", "demo-anthracnose-guide.md", "title", "Anthracnose Guide", "summary", "Platform sample document")
        ));
        when(demoContentService.getSuggestedQuestions()).thenReturn(List.of("How to control anthracnose?"));
        when(demoContentService.getDemoFlow()).thenReturn(List.of("Check status", "Start asking"));
        when(collaborationService.getSummary())
                .thenReturn(new CollaborationService.CollaborationSummary(3, 2, 1, "炭疽病", 4.7));
    }

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("login-token")
                .expiresAt("2026-03-23T00:00:00Z")
                .user(technicianView)
                .build();
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "technician",
                                  "password": "demo123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login-token"))
                .andExpect(jsonPath("$.user.role").value("technician"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void meReturnsCurrentUserWithBearerToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("technician"))
                .andExpect(jsonPath("$.role").value("technician"));
    }

    @Test
    void evaluationQuestionsAllowTechnicianRole() throws Exception {
        when(evaluationService.listQuestions(nullable(String.class), nullable(Boolean.class), anyInt(), anyInt()))
                .thenReturn(PageResponse.<EvaluationRecordDto>builder()
                        .total(1)
                        .page(1)
                        .size(20)
                        .items(List.of(EvaluationRecordDto.builder()
                                .id("chat-1")
                                .type("disease")
                                .question("How to control anthracnose?")
                                .referenceAnswer("Clean the orchard and rotate fungicides.")
                                .evaluated(false)
                                .createdAt("2026-03-16T00:00:00Z")
                                .build()))
                        .build());

        mockMvc.perform(get("/evaluations/questions")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("disease"));
    }

    @Test
    void evaluationQuestionsRejectFarmerRole() throws Exception {
        mockMvc.perform(get("/evaluations/questions")
                        .header("Authorization", "Bearer farmer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.role").value("farmer"));
    }

    @Test
    void documentUploadAllowsTechnicianRole() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.md",
                "text/markdown",
                "# sample".getBytes()
        );
        when(documentService.upload(any(), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(DocumentRecord.builder()
                        .id("doc-1")
                        .name("guide.md")
                        .title("Platform Guide")
                        .contentType("text/markdown")
                        .chunkCount(1)
                        .indexed(true)
                        .ownerId("tech-1")
                        .ownerUsername("technician")
                        .build());

        mockMvc.perform(multipart("/documents")
                        .file(file)
                        .param("title", "Platform Guide")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.ownerUsername").value("technician"));
    }

    @Test
    void documentUploadRejectsShopkeeperRole() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.md",
                "text/markdown",
                "# sample".getBytes()
        );

        mockMvc.perform(multipart("/documents")
                        .file(file)
                        .param("title", "Platform Guide")
                        .header("Authorization", "Bearer shopkeeper-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.role").value("shopkeeper"));
    }

    @Test
    void documentListRejectsFarmerRole() throws Exception {
        mockMvc.perform(get("/documents")
                        .header("Authorization", "Bearer farmer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.role").value("farmer"));
    }

    @Test
    void knowledgeGraphVisualizationAllowsFarmerRole() throws Exception {
        mockMvc.perform(get("/kg/visualize")
                        .header("Authorization", "Bearer farmer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void knowledgeGraphVisualizationAllowsTechnicianRole() throws Exception {
        mockMvc.perform(get("/kg/visualize")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void shopProfileAllowsShopkeeperRole() throws Exception {
        when(collaborationService.getProfile(shopkeeperUser))
                .thenReturn(StoreProfileDto.builder()
                        .shopId("shop-demo-main")
                        .shopName("荔园农资服务站")
                        .contactName("门店店主")
                        .serviceArea("桂味产区")
                        .rating(4.8)
                        .build());

        mockMvc.perform(get("/shop/profile")
                        .header("Authorization", "Bearer shopkeeper-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("荔园农资服务站"))
                .andExpect(jsonPath("$.rating").value(4.8));
    }

    @Test
    void shopPlanCreationAllowsShopkeeperRole() throws Exception {
        when(collaborationService.createPlan(any(), any()))
                .thenReturn(RemedyPlanDto.builder()
                        .id("plan-1")
                        .shopId("shop-demo-main")
                        .shopName("荔园农资服务站")
                        .title("炭疽病雨季处理方案")
                        .diseaseTag("炭疽病")
                        .stageTag("雨季高湿")
                        .summary("先清园，再轮换用药。")
                        .products(List.of("吡唑醚菌酯"))
                        .usageTips(List.of("3 天内复查"))
                        .riskNotes(List.of("遵循标签用量"))
                        .inventoryStatus("有现货")
                        .active(true)
                        .build());

        mockMvc.perform(post("/shop/plans")
                        .header("Authorization", "Bearer shopkeeper-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "炭疽病雨季处理方案",
                                  "diseaseTag": "炭疽病",
                                  "stageTag": "雨季高湿",
                                  "summary": "先清园，再轮换用药。",
                                  "products": ["吡唑醚菌酯"],
                                  "usageTips": ["3 天内复查"],
                                  "riskNotes": ["遵循标签用量"],
                                  "inventoryStatus": "有现货",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("plan-1"))
                .andExpect(jsonPath("$.shopName").value("荔园农资服务站"));
    }

    @Test
    void recommendationsAllowFarmerRole() throws Exception {
        when(collaborationService.getRecommendations(nullable(String.class), nullable(String.class), nullable(String.class), any()))
                .thenReturn(List.of(
                        RecommendedPlanDto.builder()
                                .planId("plan-1")
                                .shopId("shop-demo-main")
                                .shopName("荔园农资服务站")
                                .title("炭疽病雨季处理方案")
                                .diseaseTag("炭疽病")
                                .stageTag("雨季高湿")
                                .summary("先清园，再轮换用药，并注意安全边界。")
                                .inventoryStatus("有现货")
                                .score(88.5)
                                .reasonTags(List.of("病症高度匹配"))
                                .build()
                ));

        mockMvc.perform(get("/plans/recommendations")
                        .param("diseaseTag", "炭疽病")
                        .header("Authorization", "Bearer farmer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shopName").value("荔园农资服务站"))
                .andExpect(jsonPath("$[0].reasonTags[0]").value("病症高度匹配"));
    }

    @Test
    void consultationCreationAllowsFarmerRole() throws Exception {
        when(collaborationService.createConsultation(any(), any()))
                .thenReturn(ConsultationRecordDto.builder()
                        .id("consult-1")
                        .farmerUserId("farmer-1")
                        .farmerUsername("farmer")
                        .diseaseTag("炭疽病")
                        .stageTag("雨季高湿")
                        .question("果面褐斑持续扩大怎么办？")
                        .planId("plan-1")
                        .planTitle("炭疽病雨季处理方案")
                        .shopId("shop-demo-main")
                        .shopName("荔园农资服务站")
                        .contactName("门店店主")
                        .phone("13800001234")
                        .status("pending")
                        .reasonTags(List.of("病症高度匹配"))
                        .createdAt("2026-03-16T00:00:00Z")
                        .updatedAt("2026-03-16T00:00:00Z")
                        .build());

        mockMvc.perform(post("/consultations")
                        .header("Authorization", "Bearer farmer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planId": "plan-1",
                                  "diseaseTag": "炭疽病",
                                  "stageTag": "雨季高湿",
                                  "question": "果面褐斑持续扩大怎么办？",
                                  "reasonTags": ["病症高度匹配"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("consult-1"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void consultationInboxAllowsShopkeeperRole() throws Exception {
        when(collaborationService.listInbox(eq(shopkeeperUser), anyInt(), anyInt()))
                .thenReturn(PageResponse.<ConsultationRecordDto>builder()
                        .total(1)
                        .page(1)
                        .size(10)
                        .items(List.of(
                                ConsultationRecordDto.builder()
                                        .id("consult-1")
                                        .farmerUsername("farmer")
                                        .diseaseTag("炭疽病")
                                        .planTitle("炭疽病雨季处理方案")
                                        .shopName("荔园农资服务站")
                                        .status("pending")
                                        .build()
                        ))
                        .build());

        mockMvc.perform(get("/consultations/inbox")
                        .header("Authorization", "Bearer shopkeeper-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("consult-1"))
                .andExpect(jsonPath("$.items[0].status").value("pending"));
    }

    @Test
    void consultationStatusUpdateAllowsShopkeeperRole() throws Exception {
        when(collaborationService.updateConsultationStatus(any(), any(), any()))
                .thenReturn(ConsultationRecordDto.builder()
                        .id("consult-1")
                        .status("contacted")
                        .shopName("荔园农资服务站")
                        .build());

        mockMvc.perform(post("/consultations/consult-1/status")
                        .header("Authorization", "Bearer shopkeeper-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "contacted"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("consult-1"))
                .andExpect(jsonPath("$.status").value("contacted"));
    }

    @Test
    void consultationInboxRejectsFarmerRole() throws Exception {
        mockMvc.perform(get("/consultations/inbox")
                        .header("Authorization", "Bearer farmer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.role").value("farmer"));
    }

    @Test
    void feedbackSubmitAllowsAuthenticatedUser() throws Exception {
        when(feedbackService.submit(any(), any()))
                .thenReturn(FeedbackRecordDto.builder()
                        .id("feedback-1")
                        .userId("farmer-1")
                        .username("farmer")
                        .role("farmer")
                        .module("整体体验")
                        .overallScore(5)
                        .accuracyScore(5)
                        .practicalityScore(4)
                        .fluencyScore(5)
                        .comment("很好用")
                        .createdAt("2026-03-16T00:00:00Z")
                        .build());

        mockMvc.perform(post("/feedbacks")
                        .header("Authorization", "Bearer farmer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "module": "整体体验",
                                  "overallScore": 5,
                                  "accuracyScore": 5,
                                  "practicalityScore": 4,
                                  "fluencyScore": 5,
                                  "comment": "很好用"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.module").value("整体体验"))
                .andExpect(jsonPath("$.username").value("farmer"));
    }

    @Test
    void feedbackStatsRequireTechnicianRole() throws Exception {
        mockMvc.perform(get("/feedbacks/stats")
                        .header("Authorization", "Bearer farmer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.role").value("farmer"));
    }

    @Test
    void feedbackStatsAllowTechnicianRole() throws Exception {
        when(feedbackService.getStats())
                .thenReturn(FeedbackStatsResponse.builder()
                        .total(1)
                        .avgOverallScore(5.0)
                        .avgAccuracyScore(5.0)
                        .avgPracticalityScore(4.0)
                        .avgFluencyScore(5.0)
                        .byModule(List.of(
                                FeedbackStatsResponse.ModuleStat.builder()
                                        .module("整体体验")
                                        .count(1)
                                        .avgOverallScore(5.0)
                                        .build()
                        ))
                        .recent(List.of())
                        .build());

        mockMvc.perform(get("/feedbacks/stats")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.avgOverallScore").value(5.0));
    }

    @Test
    void healthEndpointReturnsHealthyPayload() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.services.neo4j").value("connected"))
                .andExpect(jsonPath("$.services.milvus").value("connected"))
                .andExpect(jsonPath("$.services.ollama").value("connected"))
                .andExpect(jsonPath("$.services.diagnosis").value("connected"))
                .andExpect(jsonPath("$.documents.total").value(5))
                .andExpect(jsonPath("$.documents.indexed").value(5));
    }
}
