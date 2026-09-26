package util;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection helper.
 *
 * Resolution order:
 *   1. Railway-style env vars (MYSQLHOST, MYSQLPORT, MYSQLDATABASE, MYSQLUSER, MYSQLPASSWORD)
 *      → JDBC URL is built dynamically. Also supports MYSQL_URL (mysql://user:pass@host:port/db).
 *   2. Explicit env/system property override (ETASMI_JDBC_URL, ETASMI_JDBC_USER, ETASMI_JDBC_PASSWORD).
 *   3. JNDI DataSource (Tomcat local / Docker Compose) at java:comp/env/jdbc/ETasmiDS.
 *   4. Error with a clear message.
 *
 * Why env-vars come before JNDI:
 *   The packaged WAR ships META-INF/context.xml pointing at the local Docker host
 *   (db:3306). On platforms like Railway that context is stale; preferring env vars
 *   avoids calling the wrong DataSource and lets the same WAR run anywhere.
 */
public final class Db {
    private static final String DEFAULT_JNDI_NAME = "java:comp/env/jdbc/ETasmiDS";
    private static final String DEFAULT_JDBC_PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

    private static volatile boolean mysqlDriverRegistered;

    private Db() {}

    /**
     * DriverManager does not always see {@code mysql-connector-j} from the WAR's {@code WEB-INF/lib}
     * (Tomcat webapp classloader). Explicit registration fixes "No suitable driver found for jdbc:mysql:".
     */
    private static void ensureMysqlDriverLoaded() throws SQLException {
        if (mysqlDriverRegistered) {
            return;
        }
        synchronized (Db.class) {
            if (mysqlDriverRegistered) {
                return;
            }
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ex) {
                throw new SQLException("MySQL JDBC driver not found. Ensure mysql-connector-j is in WEB-INF/lib.", ex);
            }
            mysqlDriverRegistered = true;
        }
    }

    public static Connection getConnection() throws SQLException {
        // 1. Railway-style discrete env vars → build JDBC URL dynamically.
        Connection railwayConnection = tryRailwayEnv();
        if (railwayConnection != null) {
            return railwayConnection;
        }

        // 2. Explicit full JDBC URL override (env or system property).
        String url = firstNonBlank(System.getenv("ETASMI_JDBC_URL"), System.getProperty("etasmi.jdbc.url"));
        String username = firstNonBlank(System.getenv("ETASMI_JDBC_USER"), System.getProperty("etasmi.jdbc.user"));
        String password = firstNonBlank(System.getenv("ETASMI_JDBC_PASSWORD"), System.getProperty("etasmi.jdbc.password"));
        if (url != null) {
            ensureMysqlDriverLoaded();
            return DriverManager.getConnection(url, username, password);
        }

        // 3. JNDI DataSource (local Tomcat / Docker Compose).
        NamingException jndiFailure = null;
        try {
            DataSource dataSource = tryLookupDataSource(DEFAULT_JNDI_NAME);
            if (dataSource != null) {
                return dataSource.getConnection();
            }
        } catch (NamingException ex) {
            jndiFailure = ex;
        }

        // 4. No configuration found → clear error.
        String message = "No database configuration found. Provide Railway env vars " +
                "(MYSQLHOST/MYSQLPORT/MYSQLDATABASE/MYSQLUSER/MYSQLPASSWORD), or set ETASMI_JDBC_URL, " +
                "or configure the JNDI DataSource jdbc/ETasmiDS in Tomcat.";
        if (jndiFailure != null) {
            throw new SQLException(message, jndiFailure);
        }
        throw new SQLException(message);
    }

    /**
     * Attempts to build a JDBC connection from Railway-style environment variables.
     * Returns null if the required variables are not set (so the caller can fall through).
     */
    private static Connection tryRailwayEnv() throws SQLException {
        String host = trimToNull(System.getenv("MYSQLHOST"));
        String database = trimToNull(System.getenv("MYSQLDATABASE"));
        String user = trimToNull(System.getenv("MYSQLUSER"));
        String password = System.getenv("MYSQLPASSWORD"); // may be empty string, that's still valid
        String port = trimToNull(System.getenv("MYSQLPORT"));

        if (host == null || database == null || user == null) {
            // Not a Railway-style env; also honour the combined MYSQL_URL form if provided.
            String combined = trimToNull(System.getenv("MYSQL_URL"));
            if (combined != null) {
                ensureMysqlDriverLoaded();
                return DriverManager.getConnection(toJdbcUrl(combined));
            }
            return null;
        }

        ensureMysqlDriverLoaded();
        String effectivePort = (port == null) ? "3306" : port;
        String jdbcUrl = "jdbc:mysql://" + host + ":" + effectivePort + "/" + database + DEFAULT_JDBC_PARAMS;
        return DriverManager.getConnection(jdbcUrl, user, password == null ? "" : password);
    }

    /**
     * Convert Railway's combined "mysql://user:pass@host:port/db" URL into a JDBC URL.
     */
    private static String toJdbcUrl(String mysqlUrl) {
        String normalized = mysqlUrl;
        if (normalized.startsWith("mysql://")) {
            normalized = "jdbc:" + normalized;
        }
        if (normalized.contains("?")) {
            return normalized;
        }
        return normalized + DEFAULT_JDBC_PARAMS;
    }

    private static DataSource tryLookupDataSource(String jndiName) throws NamingException {
        Context initCtx = new InitialContext();
        try {
            return (DataSource) initCtx.lookup(jndiName);
        } catch (ClassCastException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
