package model.service.stt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ElevenLabs batch speech-to-text.
 * Official contract: POST https://api.elevenlabs.io/v1/speech-to-text
 * with header xi-api-key, multipart model_id of scribe_v1 or scribe_v2, and file.
 * The transcript is the top-level text field. This provider does not call OpenAI.
 */
public final class ElevenLabsSpeechToTextProvider implements SpeechToTextProvider {

    static final String ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text";
    static final String DEFAULT_MODEL = "scribe_v2";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final Logger LOGGER = Logger.getLogger(ElevenLabsSpeechToTextProvider.class.getName());

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public String id() {
        return "elevenlabs";
    }

    @Override
    public String model() {
        String configured = SpeechToTextProviders.env("ELEVENLABS_STT_MODEL");
        return configured == null ? DEFAULT_MODEL : configured;
    }

    @Override
    public boolean configured() {
        return SpeechToTextProviders.env("ELEVENLABS_API_KEY") != null && supportedModel(model());
    }

    @Override
    public String unavailableReason() {
        if (SpeechToTextProviders.env("ELEVENLABS_API_KEY") == null) {
            return "ELEVENLABS_API_KEY is not configured.";
        }
        return "ELEVENLABS_STT_MODEL must be scribe_v1 or scribe_v2.";
    }

    @Override
    public SpeechTranscript transcribe(byte[] audio, String fileName) {
        if (!configured()) {
            return SpeechTranscript.fail(unavailableReason());
        }
        if (audio == null || audio.length == 0) {
            return SpeechTranscript.fail("Recitation media is unavailable for AI analysis.");
        }
        String safeName = fileName == null || fileName.isBlank() ? "recitation.webm" : fileName.trim();
        MultipartBody body = new MultipartBody();
        body.field("model_id", model());
        body.field("language_code", "ar");
        body.file("file", safeName, audio);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(TIMEOUT)
                .header("xi-api-key", SpeechToTextProviders.env("ELEVENLABS_API_KEY"))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.finish()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.log(Level.WARNING, "ElevenLabs transcription HTTP {0}", response.statusCode());
                return SpeechTranscript.fail("Cannot transcribe audio (HTTP " + response.statusCode() + ").");
            }
            String text = JsonText.topLevelString(response.body(), "text");
            if (text != null) {
                text = text.trim();
            }
            if (text == null || text.isEmpty()) {
                return SpeechTranscript.fail("No speech was detected in the audio.");
            }
            return SpeechTranscript.ok(text);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "ElevenLabs transcription failed ({0})", ex.getClass().getSimpleName());
            return SpeechTranscript.fail("Cannot transcribe audio (upstream error).");
        }
    }

    private static boolean supportedModel(String model) {
        return "scribe_v1".equals(model) || "scribe_v2".equals(model);
    }
}
