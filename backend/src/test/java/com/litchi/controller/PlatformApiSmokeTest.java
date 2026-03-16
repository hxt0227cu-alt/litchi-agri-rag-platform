package com.litchi.controller;

import com.litchi.auth.AuthInterceptor;
import com.litchi.auth.AuthService;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.config.WebConfig;
import com.litchi.dto.AuthResponse;
import com.litchi.dto.AuthUserView;
import com.litchi.dto.DocumentRecord;
import com.litchi.dto.EvaluationRecordDto;
import com.litchi.dto.FeedbackRecordDto;
import com.litchi.dto.FeedbackStatsResponse;
import com.litchi.dto.PageResponse;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        EvaluationController.class,
        DocumentController.class,
        FeedbackController.class,
        SystemController.class
})
@Import({WebConfig.class, AuthInterceptor.class, GlobalExceptionHandler.class})
class PlatformApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private EvaluationService evaluationService;

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
    private AuthUserView technicianView;

    @BeforeEach
    void setUp() {
        technicianUser = new AuthenticatedUser("tech-1", "technician", "technician", "2026-03-16T00:00:00Z");
        farmerUser = new AuthenticatedUser("farmer-1", "farmer", "farmer", "2026-03-16T00:00:00Z");
        technicianView = AuthUserView.builder()
                .id("tech-1")
                .username("technician")
                .role("technician")
                .createdAt("2026-03-16T00:00:00Z")
                .build();

        when(authService.resolveUser("technician-token")).thenReturn(technicianUser);
        when(authService.resolveUser("farmer-token")).thenReturn(farmerUser);
        when(authService.me("technician-token")).thenReturn(technicianView);

        when(knowledgeGraphService.isNeo4jAvailable()).thenReturn(true);
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
                                .id(1L)
                                .type("disease")
                                .question("How to control anthracnose?")
                                .referenceAnswer("Clean the orchard and rotate fungicides.")
                                .evaluated(false)
                                .createdAt("2026-03-16T00:00:00Z")
                                .build()))
                        .build());

        mockMvc.perform(get("/evaluation/questions")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("disease"));
    }

    @Test
    void evaluationQuestionsRejectFarmerRole() throws Exception {
        mockMvc.perform(get("/evaluation/questions")
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

        mockMvc.perform(multipart("/document")
                        .file(file)
                        .param("title", "Platform Guide")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.ownerUsername").value("technician"));
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

        mockMvc.perform(post("/feedback")
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
        mockMvc.perform(get("/feedback/stats")
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

        mockMvc.perform(get("/feedback/stats")
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
