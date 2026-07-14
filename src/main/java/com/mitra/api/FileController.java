package com.mitra.api;

import com.mitra.files.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
// SEC-05: @CrossOrigin removed -- CORS is centrally managed in SecurityConfig
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Upload a file. Requires authentication (enforced by SecurityConfig).
     * SEC-06: FileStorageService validates MIME type and file size.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileNameOrUrl = fileStorageService.storeFile(file);

            String fileUrl = fileNameOrUrl;
            if (fileNameOrUrl != null && !fileNameOrUrl.startsWith("http")) {
                fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/files/")
                        .path(fileNameOrUrl)
                        .toUriString();
            }

            Map<String, String> response = new HashMap<>();
            response.put("fileName", fileNameOrUrl);
            response.put("fileUrl", fileUrl);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            // File type or size validation failed
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Download/serve a local file.
     * SEC-06d: Path traversal protection enforced in FileStorageService.
     */
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        try {
            Resource resource = fileStorageService.loadFileAsResource(fileName);

            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (IOException ex) {
                // Could not determine MIME type
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (SecurityException ex) {
            // Path traversal attempt detected -- return 400
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
