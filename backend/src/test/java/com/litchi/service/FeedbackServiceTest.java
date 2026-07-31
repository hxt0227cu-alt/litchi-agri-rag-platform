package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.SubmitFeedbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

    @TempDir
    Path tempDir;

    private Path stateFile;
    private MysqlStateStoreService mysqlStateStoreService;
    private FeedbackService feedbackService;
    private AuthenticatedUser farmerUser;

    @BeforeEach
    void setUp() {
        stateFile = tempDir.resolve("feedback-state.json");
        mysqlStateStoreService = mock(MysqlStateStoreService.class);
        when(mysqlStateStoreService.isActive()).thenReturn(false);

        feedbackService = new FeedbackService(new ObjectMapper(), mysqlStateStoreService);
        ReflectionTestUtils.setField(feedbackService, "stateFile", stateFile.toString());
        feedbackService.init();

        farmerUser = new AuthenticatedUser("farmer-1", "farmer", "farmer", "2026-03-16T00:00:00Z");
    }

    @Test
    void submitPersistsAndUpdatesStats() {
        SubmitFeedbackRequest request = new SubmitFeedbackRequest();
        request.setModule("chat");
        request.setOverallScore(5);
        request.setAccuracyScore(4);
        request.setPracticalityScore(5);
        request.setFluencyScore(4);
        request.setComment("回答可追溯，建议明确。");

        var record = feedbackService.submit(farmerUser, request);

        assertEquals("chat", record.getModule());
        assertEquals(1, feedbackService.getStats().getTotal());
        assertEquals(5.0, feedbackService.getStats().getAvgOverallScore());
    }

    @Test
    void localFeedbackStateIsMigratedToMysqlWhenStructuredStorageIsActive() throws Exception {
        when(mysqlStateStoreService.isActive()).thenReturn(true);
        when(mysqlStateStoreService.loadFeedbackState()).thenReturn(java.util.Optional.empty());

        String localState = """
                {
                  "records": [
                    {
                      "id": "feedback-local-1",
                      "userId": "farmer-1",
                      "username": "farmer",
                      "role": "farmer",
                      "module": "solutions",
                      "overallScore": 5,
                      "accuracyScore": 4,
                      "practicalityScore": 5,
                      "fluencyScore": 4,
                      "comment": "本地反馈需要迁移。",
                      "createdAt": "2026-03-26T00:00:00Z"
                    }
                  ]
                }
                """;
        Files.writeString(stateFile, localState, StandardCharsets.UTF_8);

        FeedbackService reloaded = new FeedbackService(new ObjectMapper(), mysqlStateStoreService);
        ReflectionTestUtils.setField(reloaded, "stateFile", stateFile.toString());
        reloaded.init();

        verify(mysqlStateStoreService).saveFeedbackState(any(MysqlStateStoreService.FeedbackStateData.class));
        assertEquals(1, reloaded.getStats().getTotal());
        assertEquals("solutions", reloaded.getStats().getRecent().get(0).getModule());
    }
}
