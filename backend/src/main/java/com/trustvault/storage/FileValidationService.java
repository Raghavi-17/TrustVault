package com.trustvault.storage;

import com.trustvault.common.BadRequestException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FileValidationService {

    private static final Logger log = LoggerFactory.getLogger(FileValidationService.class);

    private final Tika tika = new Tika();

    @Value("${trustvault.storage.max-file-size-bytes:15728640}")
    private long maxFileSizeBytes; // 15MB default

    // Allowed extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "doc", "txt", "csv", "xlsx", "xls", "png", "jpg", "jpeg", "md"
    );

    // Extension to expected MIME type prefixes/patterns
    private static final Map<String, Set<String>> EXTENSION_MIME_MAP = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/x-tika-ooxml")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/plain", "text/csv", "application/csv")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip", "application/x-tika-ooxml")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("md", Set.of("text/plain", "text/markdown", "text/x-web-markdown"))
    );

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("File size (" + file.getSize() + " bytes) exceeds maximum allowed size (" + maxFileSizeBytes + " bytes)");
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new BadRequestException("Filename cannot be blank");
        }

        // Path traversal check
        if (rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            throw new BadRequestException("Invalid filename containing directory traversal characters");
        }

        // Validate extension
        String extension = getFileExtension(rawFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("File type '." + extension + "' is not supported. Allowed extensions: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // MIME type inspection via Apache Tika (Magic Bytes inspection)
        try (InputStream inputStream = file.getInputStream()) {
            String detectedMime = tika.detect(inputStream, rawFilename);
            log.info("Inspected file '{}': size={}, detectedMime={}", rawFilename, file.getSize(), detectedMime);

            Set<String> allowedMimes = EXTENSION_MIME_MAP.get(extension);
            if (allowedMimes != null) {
                boolean matches = allowedMimes.stream().anyMatch(m -> detectedMime.equalsIgnoreCase(m) || detectedMime.startsWith(m));
                if (!matches && !isTextFallback(extension, detectedMime)) {
                    log.warn("MIME type mismatch for '{}': extension expects {}, but detected {}", rawFilename, allowedMimes, detectedMime);
                    throw new BadRequestException("File content does not match its declared extension '." + extension + "' (detected: " + detectedMime + ")");
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file contents for validation");
        }
    }

    private boolean isTextFallback(String extension, String detectedMime) {
        // Plain text files can sometimes be detected as text/plain or octet-stream for empty or simple text
        return ("txt".equalsIgnoreCase(extension) || "csv".equalsIgnoreCase(extension) || "md".equalsIgnoreCase(extension))
                && (detectedMime.startsWith("text/") || detectedMime.equals("application/octet-stream"));
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "document";
        // Strip path components across Windows and Unix safely
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String clean = (lastSlash >= 0) ? filename.substring(lastSlash + 1) : filename;
        // Replace non-alphanumeric (except dot, dash, underscore)
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 200) {
            clean = clean.substring(clean.length() - 200);
        }
        return clean;
    }

    public String getFileExtension(String filename) {
        if (filename == null) return null;
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
