package ai.interview.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 事务共享的实测锚点：{@code JdbcTemplate} 的写入是否与 JPA 事务同生共死。
 *
 * <p>删知识库时，向量表删除走 JdbcTemplate、知识库记录删除走 JPA，两者必须在同一个
 * 事务里——「共用同一个 DataSource」是前提但不是保证，事务管理器有没有把连接绑到
 * 当前事务上只有真库能验证。这个用例同时给了正反两面：提交时写入可见，回滚时写入消失。
 *
 * <p>如果将来升级 Spring Boot 后事务管理器不再共享连接，这里会先红，
 * 而不是等到线上出现「记录还在、向量已没」才发现。
 */
@SpringBootTest
@Import(StubEmbeddingConfiguration.class)
@EnabledIf("ai.interview.it.IntegrationTestDatabase#available")
@DisplayName("JdbcTemplate 与 JPA 事务共享 · 集成测试")
class JdbcTransactionSharingIntegrationTest {

    private static final String TABLE = "it_tx_probe";

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestDatabase::jdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.ai.openai.api-key", () -> "it-placeholder-key");
    }

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("create table if not exists " + TABLE + " (id int primary key)");
        jdbcTemplate.update("delete from " + TABLE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("drop table if exists " + TABLE);
    }

    @Test
    @DisplayName("事务提交：JdbcTemplate 的写入落库")
    void jdbcWriteIsVisibleAfterCommit() {
        transactionTemplate.executeWithoutResult(status ->
            jdbcTemplate.update("insert into " + TABLE + "(id) values (1)"));

        assertEquals(1, count());
    }

    @Test
    @DisplayName("事务回滚：JdbcTemplate 的写入一起消失")
    void jdbcWriteDisappearsOnRollback() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("insert into " + TABLE + "(id) values (1)");
                throw new IllegalStateException("rollback on purpose");
            });
        } catch (IllegalStateException ignored) {
            // 预期：事务回滚
        }

        assertEquals(0, count(), "JdbcTemplate 没有加入 JPA 事务，向量删除与知识库删除会各自提交");
    }

    private int count() {
        Integer rows = jdbcTemplate.queryForObject("select count(*) from " + TABLE, Integer.class);
        return rows == null ? -1 : rows;
    }
}
