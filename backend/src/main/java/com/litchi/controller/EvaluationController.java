package com.litchi.controller;

import com.litchi.auth.AuthRequired;
import com.litchi.auth.RoleAllowed;
import com.litchi.dto.EvaluationRecordDto;
import com.litchi.dto.EvaluationStatsResponse;
import com.litchi.dto.PageResponse;
import com.litchi.dto.SubmitEvaluationAnswerRequest;
import com.litchi.dto.SubmitHumanScoreRequest;
import com.litchi.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/evaluation")
@RequiredArgsConstructor
@AuthRequired
@RoleAllowed("technician")
public class EvaluationController {
    private final EvaluationService evaluationService;

    @GetMapping("/questions")
    public ResponseEntity<PageResponse<EvaluationRecordDto>> questions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean evaluated,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(evaluationService.listQuestions(type, evaluated, page, size));
    }

    @PostMapping("/answer")
    public ResponseEntity<EvaluationRecordDto> submitAnswer(@RequestBody SubmitEvaluationAnswerRequest request) {
        return ResponseEntity.ok(evaluationService.submitSystemAnswer(request.getId(), request.getSystemAnswer()));
    }

    @PostMapping("/score")
    public ResponseEntity<EvaluationRecordDto> submitScore(@RequestBody SubmitHumanScoreRequest request) {
        return ResponseEntity.ok(evaluationService.submitHumanScore(request.getId(), request.getHumanScore()));
    }

    @GetMapping("/stats")
    public ResponseEntity<EvaluationStatsResponse> stats() {
        return ResponseEntity.ok(evaluationService.getStats());
    }
}
