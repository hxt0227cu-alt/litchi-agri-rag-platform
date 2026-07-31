package com.litchi.controller;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.auth.RoleAllowed;
import com.litchi.dto.DocumentRecord;
import com.litchi.dto.PageResponse;
import com.litchi.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@AuthRequired
@RoleAllowed("technician")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentRecord> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(file, title, user.id(), user.username()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<DocumentRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(documentService.list(keyword, page, size));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean deleted = documentService.delete(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "deleted", false,
                    "message", "文档不存在。"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "deleted", true,
                "message", "文档已删除。"
        ));
    }
}
