package com.yxw.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProfileAvatarMediaService {

    private static final long MAX_FILE_SIZE = 3L * 1024 * 1024;
    private static final List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final Path uploadRoot;

    public ProfileAvatarMediaService(@Value("${app.profile.avatar-upload-dir:./.run-logs/profile-avatars}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to initialize avatar upload directory", ex);
        }
    }

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("no image selected");
        }

        String originalFilename = String.valueOf(file.getOriginalFilename());
        String contentType = String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
        if (!isSupportedImage(contentType, originalFilename)) {
            throw new IllegalArgumentException("uploaded file must be an image");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("avatar image must not exceed 3MB");
        }

        String extension = resolveExtension(originalFilename, contentType);
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = uploadRoot.resolve(filename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalArgumentException("avatar upload failed", ex);
        }

        return "/api/profile/avatar/" + filename;
    }

    public Resource loadAsResource(String filename) {
        String safeFilename = sanitizeFilename(filename);
        Path file = uploadRoot.resolve(safeFilename).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("avatar not found");
        }

        try {
            return new UrlResource(file.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("avatar not found", ex);
        }
    }

    public MediaType resolveMediaType(String filename) {
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private boolean isSupportedImage(String contentType, String originalFilename) {
        if (StringUtils.hasText(contentType) && contentType.startsWith("image/")) {
            return true;
        }
        String extension = StringUtils.getFilenameExtension(StringUtils.cleanPath(String.valueOf(originalFilename)));
        return StringUtils.hasText(extension) && IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(String.valueOf(filename));
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            throw new IllegalArgumentException("avatar not found");
        }
        return cleaned;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String extension = StringUtils.getFilenameExtension(StringUtils.cleanPath(String.valueOf(originalFilename)));
        if (StringUtils.hasText(extension)) {
            return "." + extension.toLowerCase(Locale.ROOT);
        }

        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };
    }
}
