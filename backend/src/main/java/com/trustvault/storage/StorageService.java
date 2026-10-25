package com.trustvault.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface StorageService {

    /**
     * Stores file and returns a unique server-generated storage key.
     */
    String store(MultipartFile file);

    /**
     * Stores stream with a given storage key.
     */
    String store(InputStream inputStream, String originalFilename);

    /**
     * Loads the file as a Spring Resource for download.
     */
    Resource loadAsResource(String storageKey);

    /**
     * Deletes the stored file.
     */
    boolean delete(String storageKey);

    /**
     * Computes the SHA-256 hash of a file.
     */
    String calculateSha256(MultipartFile file);

    /**
     * Computes the SHA-256 hash from stream.
     */
    String calculateSha256(InputStream inputStream);
}
