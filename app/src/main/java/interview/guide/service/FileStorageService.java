package interview.guide.service;

import interview.guide.config.StorageConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储服务
 * File Storage Service for RustFS/S3
 */
@Service
public class FileStorageService {
    
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    
    private final S3Client s3Client;
    private final StorageConfigProperties storageConfig;
    
    public FileStorageService(S3Client s3Client, StorageConfigProperties storageConfig) {
        this.s3Client = s3Client;
        this.storageConfig = storageConfig;
    }
    
    /**
     * 上传简历文件到RustFS
     * 
     * @param file 上传的文件
     * @return 存储后的文件Key
     */
    public String uploadResume(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileKey = generateFileKey(originalFilename);
        
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
            
            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            log.info("简历上传成功: {} -> {}", originalFilename, fileKey);
            return fileKey;
            
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件读取失败", e);
        } catch (S3Exception e) {
            log.error("上传文件到RustFS失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从RustFS下载文件
     * 
     * @param fileKey 文件Key
     * @return 文件字节数组
     */
    public byte[] downloadResume(String fileKey) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .build();
            
            return s3Client.getObjectAsBytes(getRequest).asByteArray();
            
        } catch (S3Exception e) {
            log.error("下载文件失败: {} - {}", fileKey, e.getMessage(), e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除文件
     * 
     * @param fileKey 文件Key
     */
    public void deleteResume(String fileKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .build();
            
            s3Client.deleteObject(deleteRequest);
            log.info("文件删除成功: {}", fileKey);
            
        } catch (S3Exception e) {
            log.error("删除文件失败: {} - {}", fileKey, e.getMessage(), e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文件的访问URL
     * 
     * @param fileKey 文件Key
     * @return 文件访问URL
     */
    public String getFileUrl(String fileKey) {
        return String.format("%s/%s/%s", 
            storageConfig.getEndpoint(), 
            storageConfig.getBucket(), 
            fileKey);
    }
    
    /**
     * 检查存储桶是否存在，如果不存在则创建
     */
    public void ensureBucketExists() {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                .bucket(storageConfig.getBucket())
                .build();
            s3Client.headBucket(headRequest);
            log.info("存储桶已存在: {}", storageConfig.getBucket());
            
        } catch (NoSuchBucketException e) {
            log.info("存储桶不存在，正在创建: {}", storageConfig.getBucket());
            CreateBucketRequest createRequest = CreateBucketRequest.builder()
                .bucket(storageConfig.getBucket())
                .build();
            s3Client.createBucket(createRequest);
            log.info("存储桶创建成功: {}", storageConfig.getBucket());
            
        } catch (S3Exception e) {
            log.error("检查存储桶失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 生成唯一的文件Key
     * 格式: resumes/2024/01/15/uuid_originalname.pdf
     */
    private String generateFileKey(String originalFilename) {
        LocalDateTime now = LocalDateTime.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeName = sanitizeFilename(originalFilename);
        
        return String.format("resumes/%s/%s_%s", datePath, uuid, safeName);
    }
    
    /**
     * 清理文件名，移除不安全字符
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unknown";
        }
        // 只保留字母、数字、下划线、点和横线
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
