package interview.guide.mapper;

import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 简历相关的对象映射器
 * 使用MapStruct自动生成转换代码
 * 
 * 注意：JSON字段(strengthsJson, suggestionsJson)需要在Service层手动处理
 */
@Mapper(componentModel = "spring")
public interface ResumeMapper {
    
    /**
     * 将实体基础字段映射到DTO的ScoreDetail
     * 复杂的JSON字段和其他字段需要在Service层手动设置
     */
    @Mapping(target = "contentScore", source = "contentScore", qualifiedByName = "nullToZero")
    @Mapping(target = "structureScore", source = "structureScore", qualifiedByName = "nullToZero")
    @Mapping(target = "skillMatchScore", source = "skillMatchScore", qualifiedByName = "nullToZero")
    @Mapping(target = "expressionScore", source = "expressionScore", qualifiedByName = "nullToZero")
    @Mapping(target = "projectScore", source = "projectScore", qualifiedByName = "nullToZero")
    ResumeAnalysisResponse.ScoreDetail toScoreDetail(ResumeAnalysisEntity entity);
    
    @Named("nullToZero")
    default int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
