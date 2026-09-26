package util;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;
import java.util.logging.Logger;

/**
 * Custom JNDI ObjectFactory for Tomcat that builds a MySQL DataSource by
 * reading Railway environment variables at runtime.
 *
 * Tomcat does not interpolate environment variables inside {@code <Resource>}
 * attributes in {@code context.xml}, so the standard factory receives the
 * literal string {@code ${MYSQLHOST}} instead of the actual hostname.  This
 * factory sidesteps that limitation by ignoring the {@code url}, {@code
 * username}, and {@code password} attributes from {@code context.xml} and
 * reading the values directly from {@link System#getenv}.
 *
 * <p>Environment variables consumed:
 * <ul>
 *   <li>{@code MYSQLHOST}     – database hostname (required)</li>
 *   <li>{@code MYSQLPORT}     – database port (default: 3306)</li>
 *   <li>{@code MYSQLDATABASE} – database/schema name (required)</li>
 *   <li>{@code MYSQLUSER}     – database username (required)</li>
 *   <li>{@code MYSQLPASSWORD} – database password (may be empty)</li>
 * </ul>
 *
 * <p>Usage in {@code META-INF/context.xml}:
 * <pre>{@code
 * <Resource name="jdbc/ETasmiDS"
 *           auth="Container"
 *           type="javax.sql.DataSource"
 *           factory="util.RailwayDataSourceFactory"
 *           driverClassName="com.mysql.cj.jdbc.Driver"
 *           initialSize="2"
 *           maxActive="20"
 *           maxIdle="10"
 *           minIdle="2" />
 * }</pre>
 */
public class RailwayDataSourceFactory implements ObjectFactory {

    private static final Logger LOGGER =
            Logger.getLogger(RailwayDataSourceFactory.class.getName());

    private static final String JDBC_PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

    @Override
    public Object getObjectInstance(Object obj,
                                    Name name,
                                    Context nameCtx,
                                    Hashtable<?, ?> environment) throws Exception {

        if (!(obj instanceof Reference)) {
            return null;
        }

        // --- Read Railway environment variables ---
        String host     = env("MYSQLHOST");
        String port     = env("MYSQLPORT");
        String database = env("MYSQLDATABASE");
        String user     = env("MYSQLUSER");
        String password = System.getenv("MYSQLPASSWORD"); // empty string is valid

        if (host == null || database == null || user == null) {
            throw new IllegalStateException(
                    "[RailwayDataSourceFactory] Required environment variables are not set. "
                    + "MYSQLHOST=" + host
                    + ", MYSQLDATABASE=" + database
                    + ", MYSQLUSER=" + user
                    + ". Ensure these are configured in your Railway service.");
        }

        String effectivePort = (port == null) ? "3306" : port;
        String jdbcUrl = "jdbc:mysql://" + host + ":" + effectivePort + "/" + database + JDBC_PARAMS;

        LOGGER.info("[RailwayDataSourceFactory] Building DataSource for host=" + host
                + " port=" + effectivePort + " database=" + database);

        // --- Read pool-sizing attributes from context.xml (with sensible defaults) ---
        Reference ref = (Reference) obj;
        int initialSize = intAttr(ref, "initialSize", 2);
        int maxActive   = intAttr(ref, "maxActive",   20);
        int maxIdle     = intAttr(ref, "maxIdle",     10);
        int minIdle     = intAttr(ref, "minIdle",     2);
        String driverClassName = stringAttr(ref, "driverClassName",
                "com.mysql.cj.jdbc.Driver");

        // --- Build the Tomcat JDBC connection pool ---
        PoolProperties props = new PoolProperties();
        props.setUrl(jdbcUrl);
        props.setDriverClassName(driverClassName);
        props.setUsername(user);
        props.setPassword(password == null ? "" : password);
        props.setInitialSize(initialSize);
        props.setMaxActive(maxActive);
        props.setMaxIdle(maxIdle);
        props.setMinIdle(minIdle);
        props.setTestOnBorrow(true);
        props.setValidationQuery("SELECT 1");
        props.setValidationQueryTimeout(5);
        props.setTimeBetweenEvictionRunsMillis(30_000);
        props.setMinEvictableIdleTimeMillis(60_000);
        props.setRemoveAbandoned(true);
        props.setRemoveAbandonedTimeout(60);
        props.setLogAbandoned(true);

        DataSource ds = new DataSource();
        ds.setPoolProperties(props);
        return ds;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the trimmed env var value, or null if absent/blank. */
    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Reads a string attribute from the JNDI Reference, falling back to {@code defaultValue}. */
    private static String stringAttr(Reference ref, String attrName, String defaultValue) {
        RefAddr addr = ref.get(attrName);
        if (addr == null) return defaultValue;
        Object content = addr.getContent();
        if (content == null) return defaultValue;
        String s = content.toString().trim();
        return s.isEmpty() ? defaultValue : s;
    }

    /** Reads an integer attribute from the JNDI Reference, falling back to {@code defaultValue}. */
    private static int intAttr(Reference ref, String attrName, int defaultValue) {
        String s = stringAttr(ref, attrName, null);
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
