package com.unitrade.controller;

import com.unitrade.exception.ApiException;
import com.unitrade.service.ImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves uploaded product images. The file name must match the server-generated pattern, so path
 * traversal and access to arbitrary files are impossible. Responses carry nosniff and a sandboxing
 * CSP so a file can never be interpreted as HTML or script.
 */
@RestController
public class ImageController {

    private final ImageStorageService storage;

    public ImageController(ImageStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/api/images/{filename}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = storage.load(filename).orElseThrow(() -> ApiException.notFound("Image not found."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.contentTypeFor(filename)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}
