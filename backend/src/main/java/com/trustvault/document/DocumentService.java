package com.trustvault.document;

import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.audit.AuditEventType;
import com.trustvault.authorization.DocumentAuthorizationService;
import com.trustvault.common.BadRequestException;
import com.trustvault.common.ResourceNotFoundException;
import com.trustvault.storage.FileValidationService;
import com.trustvault.storage.StorageService;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final DocumentAuthorizationService authorizationService;
    private final AuditService auditService;

    public DocumentService(
            DocumentRepository documentRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            StorageService storageService,
            FileValidationService fileValidationService,
            DocumentAuthorizationService authorizationService,
            AuditService auditService) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentDto uploadDocument(
            String title,
            String description,
            String categoryId,
            String tags,
            MultipartFile file,
            String actorId,
            String actorUsername,
            String clientIp,
            String userAgent) {

        User owner = authorizationService.checkActiveUser(actorId);

        // Security validation on file
        fileValidationService.validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = fileValidationService.sanitizeFilename(originalFilename);

        Category category = null;
        if (categoryId != null && !categoryId.isBlank()) {
            category = categoryRepository.findById(categoryId).orElse(null);
        }

        // Store file privately and compute SHA-256
        String storageKey = storageService.store(file);
        String sha256Hash = storageService.calculateSha256(file);

        Document doc = new Document(
                owner,
                title,
                description,
                category,
                originalFilename,
                sanitizedFilename,
                storageKey,
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                file.getSize(),
                sha256Hash,
                tags
        );

        Document saved = documentRepository.save(doc);

        auditService.recordEvent(
                owner.getId(),
                owner.getUsername(),
                AuditEventType.DOCUMENT_UPLOAD,
                "DOCUMENT",
                saved.getId(),
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Uploaded document: '" + saved.getTitle() + "' (" + sanitizedFilename + ", " + saved.getFileSizeBytes() + " bytes)"
        );

        return DocumentDto.fromEntity(saved, "OWNER");
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> getAccessibleDocuments(String userId, Pageable pageable) {
        authorizationService.checkActiveUser(userId);
        return documentRepository.findAccessibleDocuments(userId, pageable)
                .map(doc -> {
                    String effectivePerm = authorizationService.resolveEffectivePermission(doc, userId);
                    return DocumentDto.fromEntity(doc, effectivePerm);
                });
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> searchDocuments(
            String userId,
            String query,
            String categoryId,
            String ownerId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {

        authorizationService.checkActiveUser(userId);
        String sanitizedQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String sanitizedCat = (categoryId != null && !categoryId.isBlank()) ? categoryId : null;
        String sanitizedOwner = (ownerId != null && !ownerId.isBlank()) ? ownerId : null;

        return documentRepository.searchAccessibleDocuments(
                userId,
                sanitizedQuery,
                sanitizedCat,
                sanitizedOwner,
                fromDate,
                toDate,
                pageable
        ).map(doc -> {
            String effectivePerm = authorizationService.resolveEffectivePermission(doc, userId);
            return DocumentDto.fromEntity(doc, effectivePerm);
        });
    }

    @Transactional(readOnly = true)
    public DocumentDto getDocumentDetails(String documentId, String userId, String clientIp, String userAgent) {
        Document doc = authorizationService.authorizeRead(documentId, userId, clientIp, userAgent);
        String effectivePerm = authorizationService.resolveEffectivePermission(doc, userId);

        auditService.recordEvent(
                userId,
                authorizationService.checkActiveUser(userId).getUsername(),
                AuditEventType.DOCUMENT_READ,
                "DOCUMENT",
                documentId,
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Viewed details for document: " + doc.getTitle()
        );

        return DocumentDto.fromEntity(doc, effectivePerm);
    }

    @Transactional(readOnly = true)
    public DownloadPayload downloadDocument(String documentId, String userId, String clientIp, String userAgent) {
        Document doc = authorizationService.authorizeDownload(documentId, userId, clientIp, userAgent);
        Resource resource = storageService.loadAsResource(doc.getStorageKey());

        auditService.recordEvent(
                userId,
                authorizationService.checkActiveUser(userId).getUsername(),
                AuditEventType.DOCUMENT_DOWNLOAD,
                "DOCUMENT",
                documentId,
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Downloaded document: " + doc.getTitle() + " (" + doc.getOriginalFilename() + ")"
        );

        return new DownloadPayload(resource, doc.getOriginalFilename(), doc.getMimeType(), doc.getFileSizeBytes());
    }

    @Transactional(readOnly = true)
    public DownloadPayload previewDocument(String documentId, String userId, String clientIp, String userAgent) {
        Document doc = authorizationService.authorizePreview(documentId, userId, clientIp, userAgent);
        Resource resource = storageService.loadAsResource(doc.getStorageKey());

        auditService.recordEvent(
                userId,
                authorizationService.checkActiveUser(userId).getUsername(),
                AuditEventType.DOCUMENT_READ,
                "DOCUMENT",
                documentId,
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Previewed document in-browser: " + doc.getTitle() + " (" + doc.getOriginalFilename() + ")"
        );

        return new DownloadPayload(resource, doc.getOriginalFilename(), doc.getMimeType(), doc.getFileSizeBytes());
    }

    @Transactional
    public DocumentDto updateMetadata(String documentId, DocumentUpdateRequest request, String userId, String clientIp, String userAgent) {
        Document doc = authorizationService.authorizeEdit(documentId, userId, clientIp, userAgent);

        doc.setTitle(request.title());
        doc.setDescription(request.description());
        doc.setTags(request.tags());

        if (request.categoryId() != null && !request.categoryId().isBlank()) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new BadRequestException("Invalid category ID: " + request.categoryId()));
            doc.setCategory(category);
        } else {
            doc.setCategory(null);
        }

        Document updated = documentRepository.save(doc);

        auditService.recordEvent(
                userId,
                authorizationService.checkActiveUser(userId).getUsername(),
                AuditEventType.METADATA_EDIT,
                "DOCUMENT",
                documentId,
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Updated metadata for document: " + doc.getTitle()
        );

        String effectivePerm = authorizationService.resolveEffectivePermission(updated, userId);
        return DocumentDto.fromEntity(updated, effectivePerm);
    }

    @Transactional
    public DocumentDto replaceContent(String documentId, MultipartFile file, String userId, String clientIp, String userAgent) {
        Document doc = authorizationService.authorizeEdit(documentId, userId, clientIp, userAgent);

        fileValidationService.validateFile(file);

        String oldStorageKey = doc.getStorageKey();

        // Store new file
        String newStorageKey = storageService.store(file);
        String newSha256 = storageService.calculateSha256(file);
        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = fileValidationService.sanitizeFilename(originalFilename);

        doc.setStorageKey(newStorageKey);
        doc.setSha256Hash(newSha256);
        doc.setOriginalFilename(originalFilename);
        doc.setSanitizedFilename(sanitizedFilename);
        doc.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        doc.setFileSizeBytes(file.getSize());
        doc.setVersion(doc.getVersion() + 1);

        Document saved = documentRepository.save(doc);

        // Safely remove previous file content
        storageService.delete(oldStorageKey);

        auditService.recordEvent(
                userId,
                authorizationService.checkActiveUser(userId).getUsername(),
                AuditEventType.CONTENT_REPLACE,
                "DOCUMENT",
                documentId,
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Replaced document content with new file: " + sanitizedFilename + ", incremented to v" + saved.getVersion()
        );

        String effectivePerm = authorizationService.resolveEffectivePermission(saved, userId);
        return DocumentDto.fromEntity(saved, effectivePerm);
    }

    @Transactional
    public void deleteDocument(String documentId, String userId, String clientIp, String userAgent) {
        Document doc = authorizationService.authorizeOwner(documentId, userId, clientIp, userAgent, "document deletion");

        String storageKey = doc.getStorageKey();
        String title = doc.getTitle();

        documentRepository.delete(doc);
        storageService.delete(storageKey);

        auditService.recordEvent(
                userId,
                authorizationService.checkActiveUser(userId).getUsername(),
                AuditEventType.DOCUMENT_DELETE,
                "DOCUMENT",
                documentId,
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Permanently deleted document: " + title
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryDto::fromEntity)
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(String name, String description, String actorId, String actorUsername, String clientIp, String userAgent) {
        if (categoryRepository.existsByName(name)) {
            throw new BadRequestException("Category with name '" + name + "' already exists");
        }
        Category category = new Category(name, description);
        Category saved = categoryRepository.save(category);

        auditService.recordEvent(
                actorId,
                actorUsername,
                AuditEventType.CATEGORY_CREATE,
                "CATEGORY",
                saved.getId(),
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Created document category: '" + saved.getName() + "'"
        );

        return CategoryDto.fromEntity(saved);
    }

    @Transactional
    public CategoryDto createCategory(String name, String description) {
        return createCategory(name, description, null, "ADMIN", null, null);
    }

    public record DownloadPayload(Resource resource, String filename, String mimeType, long fileSize) {}
}
