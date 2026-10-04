package model.service.stt;

import java.util.Locale;

/** Selects the speech-to-text provider. An unknown name does not fall back to another vendor. */
public final class SpeechToTextProviders {

    private SpeechToTextProviders() {
    }

    public static SpeechToTextProvider select() {
        String raw = System.getenv("STT_PROVIDER");
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || "openai".equals(name)) {
            return new OpenAISpeechToTextProvider();
        }
        if ("elevenlabs".equals(name)) {
            return new ElevenLabsSpeechToTextProvider();
        }
        return new UnknownSpeechToTextProvider(name);
    }

    static String env(String name) {
        String value = System.getenv(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
