package com.b2bfintech.common.document;

import com.b2bfintech.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;

    public DocumentService(DocumentRepository documentRepository, DocumentStorageService storageService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    public Document upload(String ownerType, String ownerId, MultipartFile file, String uploadedBy) {
        String storagePath = storageService.store(file);
        Document document = new Document();
        document.setOwnerType(ownerType);
        document.setOwnerId(ownerId);
        document.setFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setStoragePath(storagePath);
        document.setUploadedBy(uploadedBy);
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
