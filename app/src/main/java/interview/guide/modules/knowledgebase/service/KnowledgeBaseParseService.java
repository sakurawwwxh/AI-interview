package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.ContentTypeDetectionService;
import interview.guide.infrastructure.file.TextCleaningService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 知识库解析服务
 * 使用Apache Tika解析多种文档格式
 */
@Slf4j
@Service
public class KnowledgeBaseParseService {

    private final Tika tika;
    private final ContentTypeDetectionService contentTypeDetectionService;
    private final TextCleaningService textCleaningService;

    public KnowledgeBaseParseService(ContentTypeDetectionService contentTypeDetectionService,
                                     TextCleaningService textCleaningService) {
        this.contentTypeDetectionService = contentTypeDetectionService;
        this.textCleaningService = textCleaningService;
        this.tika = new Tika();
        // 设置最大文本提取长度为5MB（知识库可能比简历更大）
        this.tika.setMaxStringLength(5 * 1024 * 1024);
    }

    /**
     * 解析上传的知识库文件，提取文本内容
     *
     * @param file 上传的文件（支持PDF、DOCX、DOC、TXT、MD等）
     * @return 提取的文本内容
     */
    public String parseContent(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("开始解析知识库文件: {}", fileName);

        try (InputStream inputStream = file.getInputStream()) {
            String content = tika.parseToString(inputStream);

            // 使用统一的文本清理服务
            String cleanedContent = textCleaningService.cleanText(content);

            log.info("知识库解析成功，提取文本长度: {} 字符", cleanedContent.length());
            return cleanedContent;

        } catch (IOException | TikaException e) {
            log.error("知识库解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "知识库解析失败: " + e.getMessage());
        }
    }

    /**
     * 检测文件的MIME类型
     */
    public String detectContentType(MultipartFile file) {
        return contentTypeDetectionService.detectContentType(file);
    }
}

