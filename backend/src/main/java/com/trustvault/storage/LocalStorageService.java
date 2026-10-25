package com.trustvault.storage;

import com.trustvault.common.ResourceNotFoundException;
import com.trustvault.common.StorageException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${trustvault.storage.local-dir:./storage/documents}")
    private String storageDirPath;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(storageDirPath).toAbsolutePath().normalize();
            Files.createDirectories(this.rootLocation);
            log.info("Initialized secure local file storage at: {}", this.rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage directory: " + storageDirPath, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String storageKey = UUID.randomUUID().toString();
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file");
            }
            Path destinationFile = this.rootLocation.resolve(storageKey).normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation)) {
                throw new StorageException("Cannot store file outside current directory");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Stored file with storageKey: {}", storageKey);
            return storageKey;
        } catch (IOException e) {
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String originalFilename) {
        String storageKey = UUID.randomUUID().toString();
        try {
            Path destinationFile = this.rootLocation.resolve(storageKey).normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation)) {
                throw new StorageException("Cannot store file outside current directory");
            }
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return storageKey;
        } catch (IOException e) {
            throw new StorageException("Failed to store input stream", e);
        }
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("Storage key cannot be null or blank");
        }
        if (storageKey.contains("..") || storageKey.contains("/") || storageKey.contains("\\")) {
            throw new StorageException("Invalid storage key containing directory traversal characters");
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        validateStorageKey(storageKey);
        try {
            Path file = this.rootLocation.resolve(storageKey).normalize().toAbsolutePath();
            if (!file.getParent().equals(this.rootLocation)) {
                throw new StorageException("Access to path outside storage root is forbidden");
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Document file not found in storage for key: " + storageKey);
            }
        } catch (MalformedURLException e) {
            throw new StorageException("Could not read file: " + storageKey, e);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        validateStorageKey(storageKey);
        try {
            Path file = this.rootLocation.resolve(storageKey).normalize().toAbsolutePath();
            if (!file.getParent().equals(this.rootLocation)) {
                throw new StorageException("Cannot delete file outside storage directory");
            }
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete file with storageKey: {}", storageKey, e);
            return false;
        }
    }

    @Override
    public String calculateSha256(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return calculateSha256(is);
        } catch (IOException e) {
            throw new StorageException("Failed to read file for hash computation", e);
        }
    }

    @Override
    public String calculateSha256(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(inputStream, md)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Consume stream
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new StorageException("Failed to calculate SHA-256 checksum", e);
        }
    }
}
