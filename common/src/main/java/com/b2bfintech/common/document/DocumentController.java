package com.b2bfintech.common.document;

import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generic document upload/review, reused for corporate/vendor KYC and offline wallet-topup receipts.
 */
@RestController
@RequestMapping("/api/v1/documents")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','CORP_ADMIN','CORP_OPERATOR','VENDOR_ADMIN','VENDOR_OPERATOR')")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CORP_ADMIN','CORP_OPERATOR','VENDOR_ADMIN','VENDOR_OPERATOR')")
    public DocumentDto upload(
            @RequestParam String ownerType,
            @RequestParam(required = false) String ownerId,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String effectiveOwnerId = (ownerId == null || ownerId.isBlank()) ? principal.clientId() : ownerId;
        ClientScopeGuard.assertCanAccess(principal, effectiveOwnerId);
        Document document = documentService.upload(ownerType, effectiveOwnerId, file, principal);
        return DocumentDto.from(document);
    }

    @GetMapping
    public List<DocumentDto> listByOwner(
            @RequestParam String ownerType,
            @RequestParam(required = false) String ownerId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String effectiveOwnerId = principal.isClientUser() && (ownerId == null || ownerId.isBlank())
                ? principal.clientId() : ownerId;
        if (principal.isClientUser()) {
            ClientScopeGuard.assertCanAccess(principal, effectiveOwnerId);
        }
        return documentService.listByOwner(ownerType, effectiveOwnerId).stream().map(DocumentDto::from).toList();
    }

    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER')")
    public List<DocumentDto> pendingReview() {
        return documentService.listPendingReview().stream().map(DocumentDto::from).toList();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER')")
    public DocumentDto review(
            @PathVariable UUID id, @RequestBody ReviewRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        Document document = documentService.review(id, request.approve(), principal.phoneNumber(), request.notes());
        return DocumentDto.from(document);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Document document = documentService.get(id);
        if (principal.isClientUser()) {
            boolean ownsByClient = document.getOwnerId() != null && document.getOwnerId().equals(principal.clientId());
            boolean ownsByPath = document.getStoragePath() != null && principal.clientId() != null
                    && document.getStoragePath().contains("/" + principal.clientId() + "/");
            if (!ownsByClient && !ownsByPath) {
                throw new AccessDeniedException("You do not have access to this document");
            }
        }
        InputStreamResource resource = new InputStreamResource(documentService.loadFile(document));
        MediaType mediaType = document.getContentType() != null
                ? MediaType.parseMediaType(document.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    public record ReviewRequest(boolean approve, String notes) {
    }

    public record DocumentDto(
            String id, String ownerType, String ownerId, String fileName, String status,
            String uploadedBy, String reviewedBy, String reviewNotes, Instant createdAt) {
        static DocumentDto from(Document d) {
            return new DocumentDto(d.getId().toString(), d.getOwnerType(), d.getOwnerId(), d.getFileName(),
                    d.getStatus().name(), d.getUploadedBy(), d.getReviewedBy(), d.getReviewNotes(), d.getCreatedAt());
        }
    }
}
