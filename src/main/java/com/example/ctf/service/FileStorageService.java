package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 附件存储：文件落在 ./data/uploads（容器内挂载数据卷），数据库只保存元数据。
 */
@Service
public class FileStorageService {

    @Value("${app.storage.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${app.storage.max-attachment-size:20971520}")
    private long maxAttachmentSize;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请选择要上传的附件");
        }
        if (file.getSize() > maxAttachmentSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String originalName = sanitize(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + extension(originalName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存失败");
        }
        return new StoredFile(storedName, originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize());
    }

    public StoredFile storeBytes(String originalName, byte[] content, String contentType) {
        if (content.length > maxAttachmentSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String storedName = UUID.randomUUID() + extension(originalName);
        try {
            Files.write(root.resolve(storedName), content);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存失败");
        }
        return new StoredFile(storedName, originalName, contentType, content.length);
    }

    public Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return path;
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(storedName));
        } catch (IOException ignored) {
            // 删除失败不影响主流程
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "attachment";
        }
        String base = Paths.get(name).getFileName().toString().replaceAll("[\\r\\n]", "_");
        return base.length() > 180 ? base.substring(0, 180) : base;
    }

    private String extension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        String ext = name.substring(idx).replaceAll("[^a-zA-Z0-9.]", "");
        return ext.length() > 15 ? ext.substring(0, 15) : ext;
    }

    public record StoredFile(String storedName, String originalName, String contentType, long size) {
    }
}
