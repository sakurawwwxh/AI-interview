package interview.guide.modules.knowledgebase.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 * 负责文档分块、向量化和检索
 */
@Slf4j
@Service
public class KnowledgeBaseVectorService {
    
    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    
    public KnowledgeBaseVectorService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // 使用TokenTextSplitter，每个chunk约500 tokens，重叠50 tokens
        this.textSplitter = new TokenTextSplitter();
    }
    
    /**
     * 将知识库内容向量化并存储
     * 
     * @param knowledgeBaseId 知识库ID
     * @param content 知识库文本内容
     */
    @Transactional
    public void vectorizeAndStore(Long knowledgeBaseId, String content) {
        log.info("开始向量化知识库: kbId={}, contentLength={}", knowledgeBaseId, content.length());
        
        try {
            // 1. 先删除该知识库的旧向量数据
            deleteByKnowledgeBaseId(knowledgeBaseId);
            
            // 2. 将文本分块
            List<Document> chunks = textSplitter.apply(
                List.of(new Document(content))
            );
            
            log.info("文本分块完成: {} 个chunks", chunks.size());
            
            // 3. 为每个chunk添加metadata（知识库ID）
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("kb_id", knowledgeBaseId.toString());
                chunk.getMetadata().put("kb_id_long", knowledgeBaseId);
            });
            
            // 4. 向量化并存储
            vectorStore.add(chunks);
            
            log.info("知识库向量化完成: kbId={}, chunks={}", knowledgeBaseId, chunks.size());
            
        } catch (Exception e) {
            log.error("向量化知识库失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("向量化知识库失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 基于多个知识库进行相似度搜索
     * 
     * @param query 查询文本
     * @param knowledgeBaseIds 知识库ID列表（如果为空则搜索所有）
     * @param topK 返回top K个结果
     * @return 相关文档列表
     */
    public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK) {
        log.info("向量相似度搜索: query={}, kbIds={}, topK={}", query, knowledgeBaseIds, topK);
        
        try {
            // 使用VectorStore的similaritySearch方法（只接受查询字符串）
            List<Document> allResults = vectorStore.similaritySearch(query);
            
            // 如果指定了知识库ID，进行过滤
            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                allResults = allResults.stream()
                    .filter(doc -> {
                        Object kbId = doc.getMetadata().get("kb_id_long");
                        return kbId != null && knowledgeBaseIds.contains(Long.valueOf(kbId.toString()));
                    })
                    .collect(Collectors.toList());
                log.debug("使用metadata过滤，找到 {} 个相关文档", allResults.size());
            }
            
            // 限制返回数量
            List<Document> results = allResults.stream()
                .limit(topK)
                .collect(Collectors.toList());
            
            log.info("搜索完成: 找到 {} 个相关文档", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除指定知识库的所有向量数据
     * 使用反射调用delete方法（兼容不同版本的API）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        log.info("删除知识库向量数据: kbId={}", knowledgeBaseId);
        
        try {
            // 尝试通过反射调用delete方法
            try {
                java.lang.reflect.Method deleteMethod = vectorStore.getClass()
                    .getMethod("delete", Map.class);
                deleteMethod.invoke(vectorStore, Map.of("kb_id_long", knowledgeBaseId));
                log.info("已删除知识库向量数据: kbId={}", knowledgeBaseId);
            } catch (NoSuchMethodException e) {
                log.warn("VectorStore不支持delete方法，跳过删除: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("删除向量数据失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
        }
    }
}

