package com.litchi.controller;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.auth.RoleAllowed;
import com.litchi.dto.ConsultationRecordDto;
import com.litchi.dto.CreateConsultationRequest;
import com.litchi.dto.RecommendedPlanDto;
import com.litchi.dto.RemedyPlanDto;
import com.litchi.dto.SaveRemedyPlanRequest;
import com.litchi.dto.ShopTrendDto;
import com.litchi.dto.StoreProfileDto;
import com.litchi.dto.UpdateConsultationStatusRequest;
import com.litchi.dto.UpsertStoreProfileRequest;
import com.litchi.service.CollaborationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.litchi.dto.PageResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@AuthRequired
public class CollaborationController {

    private final CollaborationService collaborationService;

    @GetMapping("/shop/profile")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<StoreProfileDto> getProfile(HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.getProfile(user));
    }

    @PutMapping("/shop/profile")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<StoreProfileDto> saveProfile(
            @Valid @RequestBody UpsertStoreProfileRequest profileRequest,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.upsertProfile(user, profileRequest));
    }

    @GetMapping("/shop/plans")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<PageResponse<RemedyPlanDto>> listPlans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.listPlans(user, page, size));
    }

    @PostMapping("/shop/plans")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<RemedyPlanDto> createPlan(
            @Valid @RequestBody SaveRemedyPlanRequest planRequest,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collaborationService.createPlan(user, planRequest));
    }

    @PutMapping("/shop/plans/{id}")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<RemedyPlanDto> updatePlan(
            @PathVariable String id,
            @Valid @RequestBody SaveRemedyPlanRequest planRequest,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.updatePlan(user, id, planRequest));
    }

    @DeleteMapping("/shop/plans/{id}")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<Map<String, Object>> deletePlan(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.deletePlan(user, id));
    }

    @GetMapping("/shop/trends")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<List<ShopTrendDto>> getTrends() {
        return ResponseEntity.ok(collaborationService.getTrends());
    }

    @GetMapping("/plans/recommendations")
    @RoleAllowed({"farmer", "technician"})
    public ResponseEntity<List<RecommendedPlanDto>> getRecommendations(
            @RequestParam(required = false) String diseaseTag,
            @RequestParam(required = false) String stageTag,
            @RequestParam(required = false) String query,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.getRecommendations(diseaseTag, stageTag, query, user));
    }

    @PostMapping("/consultations")
    @RoleAllowed("farmer")
    public ResponseEntity<ConsultationRecordDto> createConsultation(
            @Valid @RequestBody CreateConsultationRequest consultationRequest,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collaborationService.createConsultation(user, consultationRequest));
    }

    @GetMapping("/consultations/my")
    @RoleAllowed("farmer")
    public ResponseEntity<PageResponse<ConsultationRecordDto>> listMyConsultations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.listMyConsultations(user, page, size));
    }

    @GetMapping("/consultations/inbox")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<PageResponse<ConsultationRecordDto>> listInbox(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(collaborationService.listInbox(user, page, size));
    }

    @PostMapping("/consultations/{id}/status")
    @RoleAllowed("shopkeeper")
    public ResponseEntity<ConsultationRecordDto> updateConsultationStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateConsultationStatusRequest statusRequest,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(
                collaborationService.updateConsultationStatus(user, id, statusRequest.getStatus())
        );
    }
}
