package com.b2bfintech.common.document;

import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final ClientCodeResolver clientCodeResolver;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            ClientCodeResolver clientCodeResolver) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.clientCodeResolver = clientCodeResolver;
    }

    public Document upload(String ownerType, String ownerId, MultipartFile file, AuthenticatedUser principal) {
        String storagePath = storageService.store(file, clientCodeResolver.resolve(principal));
        Document document = new Document();
        document.setOwnerType(ownerType);
        document.setOwnerId(ownerId);
        document.setFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setStoragePath(storagePath);
        document.setUploadedBy(principal.phoneNumber());
        return documentRepository.save(document);
    }

    public List<Document> listByOwner(String ownerType, String ownerId) {
        return documentRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    public List<Document> listPendingReview() {
        return documentRepository.findByStatus(Document.DocumentStatus.PENDING_REVIEW);
    }

    public Document review(UUID documentId, boolean approve, String reviewedBy, String notes) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        document.setStatus(approve ? Document.DocumentStatus.APPROVED : Document.DocumentStatus.REJECTED);
        document.setReviewedBy(reviewedBy);
        document.setReviewNotes(notes);
        return documentRepository.save(document);
    }

    public Document get(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    public InputStream loadFile(Document document) {
        return storageService.load(document.getStoragePath());
    }
}
