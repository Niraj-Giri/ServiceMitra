package com.mitra.files;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * Handles file uploads to Cloudinary and local file retrieval.
 *
 * SEC-06 FIXES:
 * 1. MIME type whitelist — only images and PDFs are accepted.
 * 2. File size limit — files over 10 MB are rejected before upload.
 * 3. Path traversal fix — resolved file path is verified to stay within
 *    the upload directory. Without this check a crafted filename like
 *    "../../etc/passwd" could escape the uploads directory.
 * 4. Cloudinary resource_type changed from "auto" (any file type) to
 *    explicit types based on MIME — prevents uploading executables.
 */
@Service
public class FileStorageService {

    /** Permitted MIME types. Extend only after security review. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/pdf"
    );

    /** Maximum upload size: 10 MB */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    private Cloudinary cloudinary;
    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create upload directory.", ex);
        }
    }

    @PostConstruct
    public void initCloudinary() {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
    }

    /**
     * Upload a file to Cloudinary after validating type and size.
     *
     * @throws IllegalArgumentException if the file type or size is not allowed.
     */
    public String storeFile(MultipartFile file) {
        // ── SEC-06a: Size validation ────────────────────────────────────────
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "File size exceeds the maximum allowed limit of 10 MB.");
        }

        // ── SEC-06b: MIME type whitelist ────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "File type '" + contentType + "' is not permitted. "
                    + "Allowed types: JPEG, PNG, WebP, GIF, PDF.");
        }

        // ── SEC-06c: Cloudinary upload with explicit resource type ──────────
        // resource_type "image" for images, "raw" for PDFs.
        // Never use "auto" — it allows any file type including executables.
        String resourceType = contentType.startsWith("image/") ? "image" : "raw";

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "folder",        "mechanic_mitra"
                    )
            );
            return (String) uploadResult.get("secure_url");
        } catch (Exception ex) {
            System.err.println("Cloudinary upload failed. Storing file locally. Reason: " + ex.getMessage());
            try {
                String originalFileName = file.getOriginalFilename();
                String fileExtension = "";
                if (originalFileName != null && originalFileName.contains(".")) {
                    fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
                }
                String newFileName = java.util.UUID.randomUUID().toString() + fileExtension;
                Path targetLocation = this.fileStorageLocation.resolve(newFileName);
                Files.write(targetLocation, file.getBytes());
                return newFileName;
            } catch (IOException ioEx) {
                throw new RuntimeException("Could not store file locally. Error: " + ioEx.getMessage(), ioEx);
            }
        }
    }

    /**
     * Load a local file as a Resource for download.
     *
     * SEC-06d: Path traversal protection — the resolved path is checked to
     * ensure it still starts with fileStorageLocation. Without this, a
     * filename like "../../etc/passwd" would escape the upload directory.
     */
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();

            // Reject any path that escapes the upload directory
            if (!filePath.startsWith(this.fileStorageLocation)) {
                throw new SecurityException("Invalid file path — directory traversal detected.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + fileName);
            }
        } catch (SecurityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("File not found: " + fileName, ex);
        }
    }
}
