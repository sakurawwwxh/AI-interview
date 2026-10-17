package ai.interview.it;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * 集成测试用的确定性 EmbeddingModel stub。
 *
 * <p>为什么需要它：检索链路的集成测试要真库、真 pgvector，但**不该依赖外部 embedding 服务**
 * （慢、要额度、非确定性）。这里用**词袋哈希向量**替代：
 * <ul>
 *   <li>同一段文本 → 完全相同的向量（可重复）</li>
 *   <li>词/字重叠越多 → 余弦相似度越高（**保持语义排序**）</li>
 * </ul>
 * 这一点很关键：若用纯随机或纯哈希向量，topK 与相似度阈值的断言就失去意义，
 * 测试会变成"只验证没抛异常"。
 *
 * <p>维度与生产一致（text-embedding-v3 = 1024），避免维度不一致掩盖真实问题。
 */
@TestConfiguration
public class StubEmbeddingConfiguration {

    static final int DIMENSIONS = 1024;

    @Bean
    @Primary
    EmbeddingModel stubEmbeddingModel() {
        return new BagOfWordsEmbeddingModel();
    }

    static final class BagOfWordsEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> inputs = request.getInstructions();
            List<Embedding> results = new ArrayList<>(inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                results.add(new Embedding(vectorize(inputs.get(i)), i));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(Document document) {
            return vectorize(document.getText());
        }

        static float[] vectorize(String text) {
            float[] vector = new float[DIMENSIONS];
            if (text == null || text.isEmpty()) {
                return vector;
            }
            for (String token : tokenize(text)) {
                vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
            }
            double sumOfSquares = 0;
            for (float value : vector) {
                sumOfSquares += value * value;
            }
            double norm = Math.sqrt(sumOfSquares);
            if (norm > 0) {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] /= (float) norm;
                }
            }
            return vector;
        }

        /**
         * 分词：ASCII 字母数字连成词；其它字符（含中日韩）逐字成 token——
         * 中文没有空格，按字切分才能让"词重叠"体现为向量相似度。
         */
        static List<String> tokenize(String text) {
            List<String> tokens = new ArrayList<>();
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                i += Character.charCount(codePoint);
                if (codePoint < 128 && Character.isLetterOrDigit(codePoint)) {
                    ascii.append((char) Character.toLowerCase(codePoint));
                    continue;
                }
                if (ascii.length() > 0) {
                    tokens.add(ascii.toString());
                    ascii.setLength(0);
                }
                if (!Character.isWhitespace(codePoint) && !isPunctuation(codePoint)) {
                    tokens.add(new String(Character.toChars(codePoint)));
                }
            }
            if (ascii.length() > 0) {
                tokens.add(ascii.toString());
            }
            return tokens;
        }

        private static boolean isPunctuation(int codePoint) {
            int type = Character.getType(codePoint);
            return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
        }
    }
}
