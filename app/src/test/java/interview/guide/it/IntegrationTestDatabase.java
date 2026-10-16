package interview.guide.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 集成测试用的独立数据库。
 *
 * <p>复用服务器上**已部署**的 PostgreSQL（不额外起任何中间件），但**不在业务库上跑测试**：
 * 首次使用时创建独立库（默认 {@code interview_guide_it}），测试上下文以
 * {@code ddl-auto=create-drop} 自建自销，业务库 {@code interview_guide} 一行数据都不碰。
 *
 * <p>连接信息默认沿用 {@code application.yml} 的同一套默认值，可用系统属性覆盖：
 * {@code -Dit.pg.host / it.pg.port / it.pg.user / it.pg.password / it.pg.testDb}。
 *
 * <p>不可达时 {@link #available()} 返回 false，集成测试整类跳过——不连数据库的环境里
 * {@code mvn test} 不会因此变红。
 */
public final class IntegrationTestDatabase {

    private static final String HOST = System.getProperty("it.pg.host", "<remote-host>");
    private static final String PORT = System.getProperty("it.pg.port", "5432");
    private static final String USER = System.getProperty("it.pg.user", "postgres");
    private static final String PASSWORD = System.getProperty("it.pg.password", "<redacted>");
    private static final String ADMIN_DB = System.getProperty("it.pg.adminDb", "interview_guide");
    private static final String TEST_DB = System.getProperty("it.pg.testDb", "interview_guide_it");

    private static Boolean reachable;
    private static boolean prepared;

    private IntegrationTestDatabase() {
    }

    /** 供 {@code @EnabledIf} 使用：数据库不可达时整类跳过。 */
    public static boolean available() {
        if (reachable == null) {
            reachable = probe();
        }
        return reachable;
    }

    /** 供 {@code @DynamicPropertySource} 使用：确保测试库与 pgvector 扩展就绪，返回 JDBC URL。 */
    public static synchronized String jdbcUrl() {
        prepare();
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + TEST_DB
            + "?sslmode=disable&connectTimeout=15&socketTimeout=60";
    }

    private static String adminUrl() {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + ADMIN_DB + "?sslmode=disable&connectTimeout=8";
    }

    private static String testUrl(String db) {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + db + "?sslmode=disable&connectTimeout=8";
    }

    private static boolean probe() {
        try (Connection c = DriverManager.getConnection(adminUrl(), USER, PASSWORD);
             Statement s = c.createStatement()) {
            s.execute("select 1");
            return true;
        } catch (Exception e) {
            System.out.println("[it] 跳过集成测试：连不上 " + HOST + ":" + PORT + " —— " + e.getMessage());
            return false;
        }
    }

    private static void prepare() {
        if (prepared) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(adminUrl(), USER, PASSWORD)) {
            if (!databaseExists(admin)) {
                try (Statement s = admin.createStatement()) {
                    s.execute("create database " + TEST_DB);
                    System.out.println("[it] 已创建隔离测试库 " + TEST_DB);
                }
            }
            try (Connection test = DriverManager.getConnection(testUrl(TEST_DB), USER, PASSWORD);
                 Statement s = test.createStatement()) {
                s.execute("create extension if not exists vector");
            }
            prepared = true;
        } catch (Exception e) {
            throw new IllegalStateException("准备隔离测试库失败（" + HOST + ":" + PORT + "/" + TEST_DB + "）: " + e.getMessage(), e);
        }
    }

    private static boolean databaseExists(Connection admin) throws Exception {
        try (PreparedStatement ps = admin.prepareStatement("select 1 from pg_database where datname = ?")) {
            ps.setString(1, TEST_DB);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
