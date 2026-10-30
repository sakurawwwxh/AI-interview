package ai.interview.it;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 集成测试用的独立数据库。
 *
 * <p>复用服务器上**已部署**的 PostgreSQL（不额外起任何中间件），但**不在业务库上跑测试**：
 * 首次使用时创建独立库（业务库名 + {@code _it}），测试上下文以 {@code ddl-auto=create-drop}
 * 自建自销，业务库 {@code interview_guide} 一行数据都不碰。
 *
 * <p><b>连接信息不落库</b>：优先取系统属性 {@code -Dit.pg.*}，否则读应用自己的
 * {@code application.yml}（该文件已被 .gitignore 忽略、含本地真实值）里的 {@code spring.datasource.*}，
 * 并把 {@code ${VAR:默认值}} 按「环境变量优先」解析。仓库里因此没有任何主机名或口令；
 * 新克隆的人没有这份 yml 时 {@link #available()} 返回 false，集成测试整类跳过而不是报错。
 * <p>可用系统属性覆盖：{@code -Dit.pg.host / it.pg.port / it.pg.user / it.pg.password / it.pg.adminDb / it.pg.testDb}。
 */
public final class IntegrationTestDatabase {

    private static final String CLASSPATH_CONFIG = "/application.yml";
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "5432";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "";

    private static Boolean reachable;
    private static boolean prepared;
    private static String host = DEFAULT_HOST;
    private static String port = DEFAULT_PORT;
    private static String user = DEFAULT_USER;
    private static String password = DEFAULT_PASSWORD;
    private static String adminDb = "postgres";
    private static String testDb = "interview_guide_it";

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
        return "jdbc:postgresql://" + host + ":" + port + "/" + testDb
            + "?sslmode=disable&connectTimeout=15&socketTimeout=60";
    }

    private static String adminUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + adminDb + "?sslmode=disable&connectTimeout=8";
    }

    private static String testUrl(String db) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + db + "?sslmode=disable&connectTimeout=8";
    }

    private static boolean probe() {
        loadConnectionInfo();
        try (Connection c = DriverManager.getConnection(adminUrl(), user, password);
             Statement s = c.createStatement()) {
            s.execute("select 1");
            return true;
        } catch (Exception e) {
            System.out.println("[it] 跳过集成测试：连不上 " + host + ":" + port + " —— " + e.getMessage());
            return false;
        }
    }

    private static void prepare() {
        if (prepared) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(adminUrl(), user, password)) {
            if (!databaseExists(admin)) {
                try (Statement s = admin.createStatement()) {
                    s.execute("create database " + testDb);
                    System.out.println("[it] 已创建隔离测试库 " + testDb);
                }
            }
            try (Connection test = DriverManager.getConnection(testUrl(testDb), user, password);
                 Statement s = test.createStatement()) {
                // 上一轮如果在中途失败，create-drop 没来得及收尾会留下半套表，
                // 下一轮启动就会 "relation already exists"。每次运行前重置 schema，
                // 让集成测试对失败中断也能自愈。
                s.execute("drop schema if exists public cascade");
                s.execute("create schema public");
                s.execute("create extension if not exists vector");
            }
            prepared = true;
        } catch (Exception e) {
            throw new IllegalStateException("准备隔离测试库失败（" + host + ":" + port + "/" + testDb + "）: " + e.getMessage(), e);
        }
    }

    private static boolean databaseExists(Connection admin) throws Exception {
        try (PreparedStatement ps = admin.prepareStatement("select 1 from pg_database where datname = ?")) {
            ps.setString(1, testDb);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 解析连接信息：系统属性优先，其次读应用配置的 spring.datasource.*，
     * 最后回落到本地默认值（连不上就跳过，不会连错库）。
     */
    @SuppressWarnings("unchecked")
    private static void loadConnectionInfo() {
        String url = null;
        String username = null;
        String pass = null;
        try (InputStream in = IntegrationTestDatabase.class.getResourceAsStream(CLASSPATH_CONFIG)) {
            if (in != null) {
                Map<String, Object> root = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
                Object spring = root == null ? null : root.get("spring");
                Object datasource = spring instanceof Map ? ((Map<String, Object>) spring).get("datasource") : null;
                if (datasource instanceof Map) {
                    Map<String, Object> ds = (Map<String, Object>) datasource;
                    url = asText(ds.get("url"));
                    username = asText(ds.get("username"));
                    pass = asText(ds.get("password"));
                }
            }
        } catch (Exception e) {
            System.out.println("[it] 读取 " + CLASSPATH_CONFIG + " 失败，按本地默认值处理: " + e.getMessage());
        }

        if (url != null) {
            Matcher m = JDBC_URL.matcher(url);
            if (m.find()) {
                host = m.group(1);
                if (m.group(2) != null) {
                    port = m.group(2);
                }
                adminDb = m.group(3);
            }
        }
        if (username != null && !username.isBlank()) {
            user = username;
        }
        if (pass != null && !pass.isBlank()) {
            password = pass;
        }

        host = System.getProperty("it.pg.host", host);
        port = System.getProperty("it.pg.port", port);
        user = System.getProperty("it.pg.user", user);
        password = System.getProperty("it.pg.password", password);
        adminDb = System.getProperty("it.pg.adminDb", adminDb);
        testDb = System.getProperty("it.pg.testDb", adminDb + "_it");
    }

    private static final Pattern JDBC_URL = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)");
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

    private static String asText(Object value) {
        return value == null ? null : resolveEnvPlaceholders(String.valueOf(value));
    }

    /** 把 ${VAR:默认值} 解析成「环境变量优先，否则默认值」。 */
    private static String resolveEnvPlaceholders(String raw) {
        Matcher m = ENV_PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String fromEnv = System.getenv(m.group(1));
            String replacement = (fromEnv != null && !fromEnv.isBlank())
                ? fromEnv
                : (m.group(2) == null ? "" : m.group(2));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
