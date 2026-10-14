package com.b2bfintech.common.document;

import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generic document upload/review, reused for client KYC and offline wallet-topup receipts
 * (implementation plan &sect;4.1/&sect;4.3). {@code ownerType}/{@code ownerId} let callers group
 * documents without a dedicated table per use case.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto upload(
            @RequestParam String ownerType,
            @RequestParam(required = false) String ownerId,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Document document = documentService.upload(ownerType, ownerId, file, principal.phoneNumber());
        return DocumentDto.from(document);
    }

    @GetMapping
    public List<DocumentDto> listByOwner(@RequestParam String ownerType, @RequestParam(required = false) String ownerId) {
        return documentService.listByOwner(ownerType, ownerId).stream().map(DocumentDto::from).toList();
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
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        Document document = documentService.get(id);
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
