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
 * Existing OpenAI transcription call. Endpoint and default model are unchanged.
 */
public final class OpenAISpeechToTextProvider implements SpeechToTextProvider {

    static final String ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";
    static final String DEFAULT_MODEL = "gpt-4o-transcribe";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final Logger LOGGER = Logger.getLogger(OpenAISpeechToTextProvider.class.getName());

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String model() {
        String configured = SpeechToTextProviders.env("OPENAI_RECITATION_MODEL");
        return configured == null ? DEFAULT_MODEL : configured;
    }

    @Override
    public boolean configured() {
        return SpeechToTextProviders.env("OPENAI_API_KEY") != null;
    }

    @Override
    public String unavailableReason() {
        return "OPENAI_API_KEY is not configured. Add it in your environment to enable AI analysis.";
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
        body.field("model", model());
        body.field("language", "ar");
        body.field("response_format", "json");
        body.field("temperature", "0");
        body.field("prompt",
                "Arabic Quran recitation. Transcribe faithfully without adding, inventing, or translating words.");
        body.file("file", safeName, audio);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + SpeechToTextProviders.env("OPENAI_API_KEY"))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.finish()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.log(Level.WARNING, "OpenAI transcription HTTP {0}", response.statusCode());
                return SpeechTranscript.fail("Cannot transcribe audio (HTTP " + response.statusCode() + ").");
            }
            String text = JsonText.topLevelString(response.body(), "text");
            if (text != null) {
                text = text.trim();
            }
            return SpeechTranscript.ok(text == null || text.isEmpty() ? null : text);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "OpenAI transcription failed ({0})", ex.getClass().getSimpleName());
            return SpeechTranscript.fail("Cannot transcribe audio (upstream error).");
        }
    }
}
