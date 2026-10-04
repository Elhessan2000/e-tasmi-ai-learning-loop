package model.service;

import model.service.quran.QuranFoundationConfig;
import model.service.quranpedia.QuranpediaConfig;
import model.service.stt.SpeechToTextProvider;
import model.service.stt.SpeechToTextProviders;
import util.CloudinaryUtil;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.logging.Logger;

/**
 * Logs whether the learning-loop providers are configured. Never logs secret values.
 */
@WebListener
public class LearningLoopStartupDiagnostics implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(LearningLoopStartupDiagnostics.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info(summary());
    }

    static String summary() {
        boolean openai = notBlank(System.getenv("OPENAI_API_KEY"));
        String evaluator = explicitEvaluator();
        SpeechToTextProvider stt = SpeechToTextProviders.select();
        QuranFoundationConfig qf = QuranFoundationConfig.load();
        String qfHost = qf.isComplete() ? qf.getApiBaseNormalized() : "unconfigured";
        String authHost = "unset";
        if (qf.isComplete()) {
            try {
                authHost = qf.resolveAuthTokenEndpoint();
            } catch (RuntimeException ex) {
                authHost = "unresolved";
            }
        }
        boolean eleven = notBlank(System.getenv("ELEVENLABS_API_KEY"));
        QuranpediaConfig quranpedia = QuranpediaConfig.load();
        String legacy = notBlank(System.getenv("OPENAI_CLASSIFIER_MODEL"))
                && !notBlank(System.getenv("OPENAI_EVALUATOR_MODEL"))
                ? " evaluatorUsesLegacyClassifier=true"
                : "";
        return "Learning loop config:"
                + " OpenAI=" + (openai ? "configured" : "not_configured")
                + " evaluatorModel=" + evaluator
                + " evaluatorReasoningEffort=high"
                + " chatEndpoint=https://api.openai.com/v1/chat/completions"
                + " sttProvider=" + stt.id()
                + " sttModel=" + stt.model()
                + " sttConfigured=" + stt.configured()
                + " QuranFoundation=" + (qf.isComplete() ? "configured" : "not_configured")
                + " quranFoundationApi=" + qfHost
                + " quranFoundationAuth=" + authHost
                + " Cloudinary=" + (CloudinaryUtil.isConfigured() ? "configured" : "not_configured")
                + " ElevenLabs=" + (eleven ? "configured" : "not_configured")
                + " elevenLabsEndpoint=https://api.elevenlabs.io/v1/speech-to-text"
                + " Quranpedia=" + (quranpedia.isConfigured() ? "configured" : "not_configured")
                + " quranpediaEndpoint=" + (quranpedia.isConfigured() ? quranpedia.getEndpoint() : "unset")
                + " quranpediaAuth=none"
                + legacy;
    }

    private static String explicitEvaluator() {
        if (notBlank(System.getenv("OPENAI_EVALUATOR_MODEL"))) {
            return System.getenv("OPENAI_EVALUATOR_MODEL").trim();
        }
        if (notBlank(System.getenv("OPENAI_CLASSIFIER_MODEL"))) {
            return System.getenv("OPENAI_CLASSIFIER_MODEL").trim();
        }
        return "gpt-6.1-sol";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
