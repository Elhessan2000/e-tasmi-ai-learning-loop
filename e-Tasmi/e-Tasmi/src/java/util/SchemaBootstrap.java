package util;

import model.service.quran.QuranBundledCatalog;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-shot schema initializer.
 *
 * On application startup this checks whether the `user` table exists in the
 * target database. If it does NOT, we run the bundled schema script
 * (WEB-INF/db/railway_schema.sql) to create every table in the correct order.
 * If the table already exists, we do nothing — so redeploys are safe.
 *
 * This removes the need to run the schema manually from DBeaver / Railway SQL
 * console: the Railway deployment auto-provisions its own schema the first
 * time the container boots against an empty database.
 */
@WebListener
public class SchemaBootstrap implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(SchemaBootstrap.class.getName());
    private static final String SCHEMA_RESOURCE = "/WEB-INF/db/railway_schema.sql";
    private static final String MARKER_TABLE = "user";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Use System.out directly so the message is guaranteed to appear in
        // platforms that only surface stdout/stderr (e.g. Railway logs).
        say("contextInitialized() fired — starting bootstrap check.");
        ServletContext ctx = sce.getServletContext();
        QuranBundledCatalog.init(ctx);
        try (Connection conn = Db.getConnection()) {
            say("Connected to DB. catalog=" + safeCatalog(conn));
            if (tableExists(conn, MARKER_TABLE)) {
                say("`user` table found — skipping schema bootstrap.");
                return;
            }
            say("`user` table missing — running railway_schema.sql to create tables.");
            String sql = readResource(ctx, SCHEMA_RESOURCE);
            if (sql == null || sql.isBlank()) {
                sayErr("Schema resource " + SCHEMA_RESOURCE + " not found in WAR. "
                        + "Tables will NOT be created automatically.");
                return;
            }
            int executed = executeScript(conn, sql);
            say("Schema bootstrap completed. Statements executed: " + executed);
        } catch (Exception ex) {
            sayErr("Failed to bootstrap schema. "
                    + "Run railway_schema.sql manually against the Railway MySQL database.");
            ex.printStackTrace(System.err);
            LOGGER.log(Level.SEVERE, "[SchemaBootstrap] Failed to bootstrap schema.", ex);
        }
    }

    private static void say(String msg) {
        System.out.println("[SchemaBootstrap] " + msg);
        LOGGER.info("[SchemaBootstrap] " + msg);
    }

    private static void sayErr(String msg) {
        System.err.println("[SchemaBootstrap] " + msg);
        LOGGER.severe("[SchemaBootstrap] " + msg);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // no-op
    }

    private boolean tableExists(Connection conn, String tableName) {
        try {
            DatabaseMetaData md = conn.getMetaData();
            String catalog = safeCatalog(conn);
            try (ResultSet rs = md.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
            // Fallback: some drivers match case-insensitively already, but try uppercase too.
            try (ResultSet rs = md.getTables(catalog, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "[SchemaBootstrap] tableExists() failed; assuming table missing.", ex);
            return false;
        }
    }

    private String safeCatalog(Connection conn) {
        try {
            return conn.getCatalog();
        } catch (Exception ex) {
            return null;
        }
    }

    private String readResource(ServletContext ctx, String path) {
        try (InputStream in = ctx.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "[SchemaBootstrap] Could not read schema resource " + path, ex);
            return null;
        }
    }

    /**
     * Extremely small SQL script runner sufficient for our schema file:
     *   - strips full-line comments starting with "--"
     *   - splits statements on a trailing ";" at the end of a line
     *   - ignores blank statements
     *
     * The schema does not contain stored procedures or DELIMITER changes, so
     * semicolon-splitting is safe here.
     */
    private int executeScript(Connection conn, String script) throws Exception {
        StringBuilder buffer = new StringBuilder();
        int executed = 0;
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
            for (String rawLine : script.split("\r?\n")) {
                String line = rawLine;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                buffer.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String sqlStmt = buffer.toString().trim();
                    // strip trailing ';'
                    if (sqlStmt.endsWith(";")) {
                        sqlStmt = sqlStmt.substring(0, sqlStmt.length() - 1).trim();
                    }
                    buffer.setLength(0);
                    if (sqlStmt.isEmpty()) {
                        continue;
                    }
                    try {
                        stmt.execute(sqlStmt);
                        executed++;
                    } catch (Exception perStatement) {
                        LOGGER.log(Level.SEVERE,
                                "[SchemaBootstrap] Statement failed: " + firstLine(sqlStmt), perStatement);
                        throw perStatement;
                    }
                }
            }
        } finally {
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (Exception ignored) {
                // not fatal
            }
        }
        return executed;
    }

    private String firstLine(String sql) {
        int newline = sql.indexOf('\n');
        String head = newline < 0 ? sql : sql.substring(0, newline);
        return head.length() > 120 ? head.substring(0, 120) + "…" : head;
    }
}
