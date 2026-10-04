package model.service;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded background pool for post-submission AI analysis. One Tomcat instance, challenge Docker.
 */
@WebListener
public class RecitationAutoAnalysisExecutor implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(RecitationAutoAnalysisExecutor.class.getName());
    private static final int POOL_SIZE = Math.max(2, Math.min(4,
            Integer.parseInt(System.getenv().getOrDefault("ETASMI_ANALYSIS_POOL_SIZE", "2"))));

    private static volatile ExecutorService executor;

    static void submit(long recitationId, Runnable task) {
        executor().execute(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Uncaught error in auto analysis for recitation " + recitationId, ex);
            }
        });
    }

    private static ExecutorService executor() {
        ExecutorService local = executor;
        if (local != null) {
            return local;
        }
        synchronized (RecitationAutoAnalysisExecutor.class) {
            if (executor == null) {
                AtomicInteger seq = new AtomicInteger();
                ThreadFactory factory = r -> {
                    Thread t = new Thread(r, "recitation-auto-analysis-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
                executor = Executors.newFixedThreadPool(POOL_SIZE, factory);
            }
            return executor;
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        executor();
        submit(0L, () -> new RecitationAnalysisJobRecovery().reconcileAllOnStartup());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ExecutorService local = executor;
        executor = null;
        if (local == null) {
            return;
        }
        local.shutdown();
        try {
            if (!local.awaitTermination(30, TimeUnit.SECONDS)) {
                local.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            local.shutdownNow();
        }
    }
}
