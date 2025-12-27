package interview.guide.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.mapper.ResumeMapper;
import interview.guide.modules.resume.repository.ResumeAnalysisRepository;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import interview.guide.modules.resume.model.ResumeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 简历持久化服务
 * Resume Persistence Service with deduplication
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumePersistenceService {
    
    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;
    private final ResumeMapper resumeMapper;
    
    /**
     * 检查简历是否已存在（基于文件内容hash）
     * 
     * @param file 上传的文件
     * @return 如果存在返回已有的简历实体，否则返回空
     */
    public Optional<ResumeEntity> findExistingResume(MultipartFile file) {
        try {
            String fileHash = calculateFileHash(file);
            Optional<ResumeEntity> existing = resumeRepository.findByFileHash(fileHash);
            
            if (existing.isPresent()) {
                log.info("检测到重复简历: hash={}", fileHash);
                ResumeEntity resume = existing.get();
                resume.incrementAccessCount();
                resumeRepository.save(resume);
            }
            
            return existing;
        } catch (Exception e) {
            log.error("检查简历重复时出错: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 保存新简历
     */
    @Transactional
    public ResumeEntity saveResume(MultipartFile file, String resumeText, 
                                   String storageKey, String storageUrl) {
        try {
            String fileHash = calculateFileHash(file);
            
            ResumeEntity resume = new ResumeEntity();
            resume.setFileHash(fileHash);
            resume.setOriginalFilename(file.getOriginalFilename());
            resume.setFileSize(file.getSize());
            resume.setContentType(file.getContentType());
            resume.setStorageKey(storageKey);
            resume.setStorageUrl(storageUrl);
            resume.setResumeText(resumeText);
            
            ResumeEntity saved = resumeRepository.save(resume);
            log.info("简历已保存: id={}, hash={}", saved.getId(), fileHash);
            
            return saved;
        } catch (Exception e) {
            log.error("保存简历失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.RESUME_UPLOAD_FAILED, "保存简历失败");
        }
    }
    
    /**
     * 保存简历评测结果
     */
    @Transactional
    public ResumeAnalysisEntity saveAnalysis(ResumeEntity resume, ResumeAnalysisResponse analysis) {
        try {
            ResumeAnalysisEntity entity = new ResumeAnalysisEntity();
            entity.setResume(resume);
            entity.setOverallScore(analysis.overallScore());
            
            // 保存各维度评分
            if (analysis.scoreDetail() != null) {
                entity.setContentScore(analysis.scoreDetail().contentScore());
                entity.setStructureScore(analysis.scoreDetail().structureScore());
                entity.setSkillMatchScore(analysis.scoreDetail().skillMatchScore());
                entity.setExpressionScore(analysis.scoreDetail().expressionScore());
                entity.setProjectScore(analysis.scoreDetail().projectScore());
            }
            
            entity.setSummary(analysis.summary());
            
            // 将列表序列化为JSON
            entity.setStrengthsJson(objectMapper.writeValueAsString(analysis.strengths()));
            entity.setSuggestionsJson(objectMapper.writeValueAsString(analysis.suggestions()));
            
            ResumeAnalysisEntity saved = analysisRepository.save(entity);
            log.info("简历评测结果已保存: analysisId={}, resumeId={}, score={}", 
                    saved.getId(), resume.getId(), analysis.overallScore());
            
            return saved;
        } catch (JsonProcessingException e) {
            log.error("序列化评测结果失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "保存评测结果失败");
        }
    }
    
    /**
     * 获取简历的最新评测结果
     */
    public Optional<ResumeAnalysisEntity> getLatestAnalysis(Long resumeId) {
        return Optional.ofNullable(analysisRepository.findFirstByResumeIdOrderByAnalyzedAtDesc(resumeId));
    }
    
    /**
     * 获取简历的最新评测结果（返回DTO）
     */
    public Optional<ResumeAnalysisResponse> getLatestAnalysisAsDTO(Long resumeId) {
        return getLatestAnalysis(resumeId).map(this::entityToDTO);
    }
    
    /**
     * 获取所有简历列表
     */
    public List<ResumeEntity> findAllResumes() {
        return resumeRepository.findAll();
    }
    
    /**
     * 获取简历的所有评测记录
     */
    public List<ResumeAnalysisEntity> findAnalysesByResumeId(Long resumeId) {
        return analysisRepository.findByResumeIdOrderByAnalyzedAtDesc(resumeId);
    }
    
    /**
     * 将实体转换为DTO
     */
    public ResumeAnalysisResponse entityToDTO(ResumeAnalysisEntity entity) {
        try {
            List<String> strengths = objectMapper.readValue(
                entity.getStrengthsJson() != null ? entity.getStrengthsJson() : "[]",
                new TypeReference<List<String>>() {}
            );
            
            List<ResumeAnalysisResponse.Suggestion> suggestions = objectMapper.readValue(
                entity.getSuggestionsJson() != null ? entity.getSuggestionsJson() : "[]",
                new TypeReference<List<ResumeAnalysisResponse.Suggestion>>() {}
            );
            
            return new ResumeAnalysisResponse(
                entity.getOverallScore(),
                resumeMapper.toScoreDetail(entity),  // 使用MapStruct自动映射
                entity.getSummary(),
                strengths,
                suggestions,
                entity.getResume().getResumeText()
            );
        } catch (JsonProcessingException e) {
            log.error("反序列化评测结果失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "获取评测结果失败");
        }
    }
    
    /**
     * 根据ID获取简历
     */
    public Optional<ResumeEntity> findById(Long id) {
        return resumeRepository.findById(id);
    }
    
    /**
     * 计算文件的SHA-256哈希值
     */
    private String calculateFileHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(file.getBytes());
        return HexFormat.of().formatHex(hash);
    }
}
