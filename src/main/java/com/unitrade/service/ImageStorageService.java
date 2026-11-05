package com.unitrade.service;

import com.unitrade.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores product images on the local filesystem, outside the web root.
 *
 * Safety measures:
 * - The real file type is detected from magic bytes; the client-supplied content type and file
 *   name are ignored. Only JPEG, PNG and WebP are accepted.
 * - JPEG/PNG headers are parsed with ImageIO to confirm they are real images and to reject
 *   oversized dimensions (decompression-bomb protection).
 * - Stored names are random UUIDs with a server-chosen extension, so no user input reaches the path.
 * - Files are served only through ImageController, which validates the name against a strict pattern.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 6000;
    private static final Pattern STORED_NAME = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|webp)$");

    public record StoredImage(String storedFilename, String contentType, long sizeBytes, String originalFilename) {
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    private final Path root;

    public ImageStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        log.info("Image uploads directory: {}", root);
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The uploaded file is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Each image must be 5 MB or smaller.");
        }

        ImageType type;
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            type = detectType(header);
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read.");
        }
        if (type == null) {
            throw ApiException.badRequest("Only JPEG, PNG and WebP images are allowed.");
        }
        if (type != ImageType.WEBP) {
            verifyRasterImage(file);
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + type.extension;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("Invalid file path.");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store upload {}", storedName, e);
            throw new IllegalStateException("Could not store the uploaded image", e);
        }
        return new StoredImage(storedName, type.contentType, file.getSize(), sanitizeOriginalName(file.getOriginalFilename()));
    }

    /** Resolves a stored image for download, or empty if the name is invalid or the file is missing. */
    public java.util.Optional<Resource> load(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            return java.util.Optional.empty();
        }
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new UrlResource(path.toUri()));
        } catch (MalformedURLException e) {
            return java.util.Optional.empty();
        }
    }

    public String contentTypeFor(String storedName) {
        if (storedName.endsWith(".png")) {
            return "image/png";
        }
        if (storedName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public void delete(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(storedName).normalize());
        } catch (IOException e) {
            log.warn("Could not delete image file {}: {}", storedName, e.getMessage());
        }
    }

    private static ImageType detectType(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8), png)) {
            return ImageType.PNG;
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }

    /** Reads only the image header (not the pixels) to validate the file and its dimensions. */
    private static void verifyRasterImage(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw ApiException.badRequest("The uploaded file is not a valid image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("The uploaded file is not a valid image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw ApiException.badRequest("Image dimensions must be at most " + MAX_DIMENSION + " pixels per side.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file is not a valid image.");
        }
    }

    private static String sanitizeOriginalName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.replace("\\", "/");
        String base = normalized.substring(normalized.lastIndexOf('/') + 1);
        String cleaned = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }
}
