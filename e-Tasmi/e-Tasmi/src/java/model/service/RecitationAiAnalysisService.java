package model.service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI-assisted Quran recitation evaluation.
 *
 * Pipeline:
 *   Step 1: transcribeAudio()    -> OpenAI speech-to-text (json format)
 *   Step 2: aiEvaluateRecitation -> Single GPT call that (a) classifies Quran vs non-Quran
 *                                   while knowing the expected passage, (b) produces the full
 *                                   instructor-facing evaluation report (correct / missing /
 *                                   incorrect / extra words, pronunciation notes, score,
 *                                   summary, feedback, matched-passage note).
 *
 * Fallbacks:
 *   - Empty transcript                          -> CANNOT_EVALUATE
 *   - LLM says NOT Quran with confidence >=0.85 -> REJECTED
 *   - LLM call fails                            -> local word-diff fallback (still useful)
 *
 * Rationale: the previous design used the LLM only as a yes/no gate and performed the
 * actual evaluation with a local Levenshtein diff + templated strings. That produced
 * alignment-artifact "mistakes" and false rejections on correct recitations. Passing the
 * expected passage to the LLM in a single reasoning pass fixes both problems.
 */
public class RecitationAiAnalysisService {

    private static final Logger LOGGER = Logger.getLogger(RecitationAiAnalysisService.class.getName());

    // --- Model / endpoint configuration ------------------------------------------------------
    // gpt-4o-transcribe is materially better than whisper-1 on classical/Quranic Arabic and uses
    // the same transcription endpoint. gpt-4o (full) reasons far more reliably than gpt-4o-mini for
    // the alignment + judgement step. Both are overridable via env vars (see resolve* methods).
    private static final String DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-transcribe";
    private static final String DEFAULT_EVALUATOR_MODEL = "gpt-4o";
    private static final String TRANSCRIPTION_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";
    private static final String CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    /**
     * Fixed RNG seed for the evaluator call. Combined with temperature=0 this makes the model's
     * output reproducible for identical inputs (OpenAI returns the same result for the same
     * {model, seed, prompt, system_fingerprint}). temperature=0 ALONE does not guarantee this.
     */
    private static final int EVALUATOR_SEED = 1_234_567;

    // --- Guardrails --------------------------------------------------------------------------
    private static final int MAX_MEDIA_BYTES = 25 * 1024 * 1024;
    /** Very conservative lower bound on bytes for a ~3 second audio clip (even heavily compressed). */
    private static final int MIN_MEDIA_BYTES = 4 * 1024;
    /** Reject only when the evaluator is *very* confident the audio is not Quran. */
    private static final double NON_QURAN_REJECTION_CONFIDENCE = 0.85;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public enum Status {
        OK,              // Evaluation succeeded (may include "different passage" note).
        REJECTED,        // High-confidence non-Quran content (song, noise, random speech, etc.).
        CANNOT_EVALUATE, // Empty transcript / upstream failure.
        FAILED           // Misconfiguration or bad input.
    }

    // =============================================================================================
    // Public entry point
    // =============================================================================================

    public AnalysisResult analyze(String expectedText, byte[] mediaBytes, String mediaFileName) {
        String normalizedExpected = trimToNull(expectedText);
        if (normalizedExpected == null) {
            return AnalysisResult.failed("Expected Quran text is required for AI analysis.");
        }
        if (mediaBytes == null || mediaBytes.length == 0) {
            return AnalysisResult.cannotEvaluate("Recitation media is unavailable for AI analysis.");
        }
        if (mediaBytes.length < MIN_MEDIA_BYTES) {
            return AnalysisResult.rejected(
                    "Audio is too short to evaluate. Please record at least 3 seconds of clear recitation.");
        }
        if (mediaBytes.length > MAX_MEDIA_BYTES) {
            return AnalysisResult.failed("Recitation file is too large for transcription. Keep it under 25MB.");
        }

        String apiKey = trimToNull(System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            return AnalysisResult.failed(
                    "OPENAI_API_KEY is not configured. Add it in your environment to enable AI analysis.");
        }

        // -------- Step 1: Transcription --------
        TranscriptionResult tr;
        try {
            tr = transcribeAudio(mediaBytes, mediaFileName, apiKey);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Transcription step failed", ex);
            return AnalysisResult.cannotEvaluate("Cannot transcribe audio (upstream error).");
        }
        if (tr == null || !tr.ok) {
            String reason = (tr == null || tr.reason == null) ? "Cannot transcribe audio." : tr.reason;
            return AnalysisResult.cannotEvaluate(reason);
        }
        if (trimToNull(tr.transcript) == null) {
            return AnalysisResult.cannotEvaluate("No speech was detected in the audio.");
        }

        // -------- Step 2: AI-assisted evaluation (classification + report in one pass) --------
        AiReport report;
        try {
            report = aiEvaluateRecitation(normalizedExpected, tr.transcript, apiKey);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "AI evaluator failed; falling back to local diff.", ex);
            report = null;
        }

        if (report == null) {
            // AI is unreachable / invalid response: still deliver a useful report using the local diff.
            return buildFallbackResult(normalizedExpected, tr.transcript);
        }

        // REJECTION gate — depends ONLY on the "is it Quran audio at all?" signal.
        // Passage mismatch (wrong surah / mixed surahs / wrong ayah) NEVER triggers a rejection.
        // We require high confidence AND that no Quran passages were identified in the transcript.
        boolean evaluatorIdentifiedQuranPassages =
                report.detectedPassages != null && !report.detectedPassages.isEmpty();
        if (!report.isQuran
                && report.isQuranConfidence >= NON_QURAN_REJECTION_CONFIDENCE
                && !evaluatorIdentifiedQuranPassages) {
            String reason = (trimToNull(report.reasonIfNotQuran) != null)
                    ? report.reasonIfNotQuran
                    : "This audio does not appear to be a Quran recitation.";
            return AnalysisResult.rejected(reason)
                    .withTranscript(tr.transcript, normalizedExpected);
        }

        // Defensive: if the AI returned nonsense (empty on every list and score<=0), fall back.
        if (isEffectivelyEmpty(report)) {
            return buildFallbackResult(normalizedExpected, tr.transcript);
        }

        // Accuracy% for the existing UI derives from the AI's own word lists.
        int correctCount = size(report.correctWords);
        int missingCount = size(report.missingWords);
        int incorrectCount = size(report.incorrectWords);
        int expectedBase = Math.max(1, correctCount + missingCount + incorrectCount);
        double accuracyPercent = 100.0 * correctCount / expectedBase;

        // Build a prominent passage-match note. If the AI didn't provide one, synthesize from
        // detected_passages / mixed_passages so the instructor always sees the context.
        String passageNote = buildPassageNote(report);

        // If the student clearly recited a different passage, prepend that to the summary so
        // the instructor sees it first-line.
        String summary = report.summary;
        if (!report.matchesExpectedPassage) {
            String prefix = "Passage mismatch — ";
            if (trimToNull(summary) == null) {
                summary = "This submission is Quran recitation but does not match the expected passage.";
            } else if (!summary.toLowerCase(Locale.ROOT).startsWith("passage mismatch")) {
                summary = prefix + summary;
            }
        }

        return AnalysisResult.ok(
                tr.transcript,
                normalizedExpected,
                accuracyPercent,
                nonNull(report.correctWords),
                nonNull(report.missingWords),
                nonNull(report.extraWords),
                nonNull(report.incorrectWords),
                nullOr(report.feedback),
                clampScore(report.score),
                nullOr(summary),
                nullOr(passageNote),
                nonNull(report.pronunciationNotes),
                report.isQuranConfidence,
                report.matchesExpectedPassage,
                report.mixedPassages,
                nonNull(report.detectedPassages),
                nullOr(report.referenceText)
        );
    }

    private String buildPassageNote(AiReport r) {
        String note = trimToNull(r.matchedPassageNote);
        if (note != null) return note;

        if (r.matchesExpectedPassage) {
            return "Recitation matches the expected passage.";
        }

        StringBuilder sb = new StringBuilder(
                "This submission appears to be Quran recitation, but it does not match the expected target passage.");
        if (r.detectedPassages != null && !r.detectedPassages.isEmpty()) {
            sb.append(" Detected content includes: ");
            sb.append(String.join("; ", r.detectedPassages));
            sb.append('.');
        }
        if (r.mixedPassages) {
            sb.append(" The student mixed āyāt from multiple sūrahs.");
        }
        return sb.toString();
    }

    // =============================================================================================
    // Step 1 - Transcription
    // =============================================================================================

    private TranscriptionResult transcribeAudio(byte[] mediaBytes, String mediaFileName, String apiKey)
            throws Exception {
        String model = trimToNull(System.getenv("OPENAI_RECITATION_MODEL"));
        if (model == null) {
            model = DEFAULT_TRANSCRIPTION_MODEL;
        }
        String safeFileName = trimToNull(mediaFileName);
        if (safeFileName == null) {
            safeFileName = "recitation.webm";
        }

        // "json" works for every OpenAI STT model (whisper-1, gpt-4o-transcribe, gpt-4o-mini-transcribe)
        // and gives us the plain transcript which is all we need; richer reasoning happens in Step 2.
        String boundary = "----eTasmiBoundary" + System.nanoTime();
        byte[] payload = buildTranscriptionPayload(boundary, model, safeFileName, mediaBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TRANSCRIPTION_ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.log(Level.WARNING, "Transcription API returned status {0}: {1}",
                    new Object[]{response.statusCode(), truncate(response.body(), 500)});
            return TranscriptionResult.fail("Cannot transcribe audio (HTTP " + response.statusCode() + ").");
        }

        Object parsed;
        try {
            parsed = MiniJson.parse(response.body());
        } catch (Exception ex) {
            return TranscriptionResult.fail("Cannot transcribe audio (invalid response).");
        }
        if (!(parsed instanceof Map)) {
            return TranscriptionResult.fail("Cannot transcribe audio (invalid response).");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        String text = asString(root.get("text"));
        return TranscriptionResult.ok(trimToNull(text));
    }

    private byte[] buildTranscriptionPayload(String boundary, String model, String fileName, byte[] mediaBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFormField(out, boundary, "model", model);
        writeFormField(out, boundary, "language", "ar");
        writeFormField(out, boundary, "response_format", "json");
        writeFormField(out, boundary, "temperature", "0");
        // Neutral hint only; do NOT seed specific verses (avoids transcription bias / hallucination).
        writeFormField(out, boundary, "prompt",
                "Arabic Quran recitation. Transcribe faithfully without adding, inventing, or translating words.");
        writeFileField(out, boundary, "file", fileName, mediaBytes);
        writeAscii(out, "--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeAscii(out, value == null ? "" : value);
        writeAscii(out, "\r\n");
    }

    private void writeFileField(ByteArrayOutputStream out, String boundary, String name, String filename, byte[] bytes) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
        writeAscii(out, "Content-Type: application/octet-stream\r\n\r\n");
        out.write(bytes, 0, bytes.length);
        writeAscii(out, "\r\n");
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }

    // =============================================================================================
    // Step 2 - AI-assisted evaluation (classification + structured report in one call)
    // =============================================================================================

    private AiReport aiEvaluateRecitation(String expectedText, String transcript, String apiKey) throws Exception {
        String model = trimToNull(System.getenv("OPENAI_EVALUATOR_MODEL"));
        if (model == null) {
            // Back-compat: honor the old env variable name if set.
            model = trimToNull(System.getenv("OPENAI_CLASSIFIER_MODEL"));
        }
        if (model == null) {
            model = DEFAULT_EVALUATOR_MODEL;
        }

        String systemPrompt =
                "You are an expert Quran (Qur'an) recitation evaluator for a Tasmi (Quran memorization) platform. " +
                "You help an instructor grade a student's recitation.\n\n" +
                "CRITICAL DISTINCTION — two independent signals:\n" +
                "  (A) is_quran: Does the audio transcript consist of recitation from the Qur'an corpus (any surah, " +
                "any ayah, any order, even mixed across surahs, even with errors)?\n" +
                "  (B) matches_expected_passage: Does the recited content correspond to the specific passage the " +
                "instructor asked the student to recite?\n" +
                "These are INDEPENDENT. A student who recites Surah Al-Ikhlas when asked for Surah Al-Baqarah is " +
                "still reciting Quran -> is_quran=true, matches_expected_passage=false.\n\n" +
                "ZERO-GUESSING CONTRACT (highest priority — overrides everything below):\n" +
                "  - Judge ONLY what is explicitly present in the supplied expected text and transcript.\n" +
                "  - Flag a word 'incorrect' ONLY when the transcript clearly shows a different word than the " +
                "reference. If a difference is plausibly an ASR artifact of the correct word, count it correct.\n" +
                "  - Never output a mistake you cannot tie to a specific token in the transcript.\n" +
                "  - If a rule cannot be verified with 100% certainty, mark it unverified rather than guessing.\n\n" +
                "HARD RULES:\n" +
                "1. Set is_quran=true whenever the recitation is from the Qur'an, EVEN IF:\n" +
                "   - it is from a different surah than expected;\n" +
                "   - it mixes ayāt from multiple surahs;\n" +
                "   - the student made many mistakes;\n" +
                "   - only partial verses are recited.\n" +
                "2. Set is_quran=false ONLY when the content is CLEARLY NOT Qur'an: modern song lyrics or nasheeds, " +
                "poetry, news, casual conversation, noise, silence, gibberish, or a du'a / adhan / hadith recited " +
                "as such (not as Qur'an). Use high confidence (>=0.85) ONLY when you are certain.\n" +
                "3. NEVER put reasoning like 'not from the expected surah' in reason_if_not_quran. That is a passage " +
                "mismatch, not a non-Quran audio. In that case is_quran must remain true.\n" +
                "4. The transcript comes from automatic speech recognition and MAY contain small ASR errors " +
                "(missing diacritics, hamza variants, segmentation, occasional wrong letter). Do NOT treat such ASR " +
                "artifacts as student mistakes when the intended word is obviously Qur'anic.\n" +
                "5. CHOOSING THE REFERENCE TEXT for the diff (NEVER recite Quran from memory):\n" +
                "   - If matches_expected_passage=true, the EXPECTED passage supplied below is the single " +
                "authoritative source of truth. Copy it VERBATIM into 'reference_text' — do NOT paraphrase, " +
                "re-spell, re-diacritise, or 'correct' it from your own memory.\n" +
                "   - If matches_expected_passage=false BUT is_quran=true, evaluate against the correct Qur'anic " +
                "text of WHAT THE STUDENT ACTUALLY RECITED. Reproduce only text you are 100% certain of; if you " +
                "are not certain of the exact wording, set reference_text to the expected passage and rely on " +
                "the passage-mismatch flag instead of inventing verses.\n" +
                "   - Put the exact reference text you used into 'reference_text'.\n" +
                "6. Word lists are always relative to 'reference_text':\n" +
                "   - correct_words: words the student recited correctly vs reference_text.\n" +
                "   - missing_words: words present in reference_text but not recited.\n" +
                "   - incorrect_words: mispronunciations/substitutions, each as '<expected> -> <heard>'.\n" +
                "   - extra_words: words the student added not in reference_text (ignore ordinary basmala/isti'adha).\n" +
                "7. detected_passages: 0-5 short human-readable labels for the passages detected in the transcript " +
                "(e.g. 'Surah Al-Ikhlas 112:1-4', 'Surah Al-Kawthar 108:1-3'). Empty if unclear.\n" +
                "8. mixed_passages=true only if detected_passages has 2+ distinct surahs.\n" +
                "9. pronunciation_notes / tajwīd: a PLAIN TEXT TRANSCRIPT CANNOT prove acoustic rules " +
                "(madd length, ghunna nasalisation, qalqala bounce, makhārij quality) — that information is " +
                "lost in transcription. Therefore you MUST NOT assert any tajwīd verdict from the transcript " +
                "alone. Only add a note when the transcript shows an unambiguous letter/word substitution; " +
                "phrase every acoustic observation as unverified, e.g. \"unverified: possible madd issue at … " +
                "— requires acoustic review\". When in doubt, leave pronunciation_notes empty.\n" +
                "10. score 0-100 reflects accuracy + completeness + observed tajwīd quality vs reference_text. " +
                "When matches_expected_passage=false, still score the student's recitation of what they chose — " +
                "the instructor will use the passage-mismatch flag separately.\n" +
                "11. summary: 1 concise sentence for the instructor. If passage mismatch, say so up front.\n" +
                "12. feedback: 2-4 actionable sentences for the student. If they recited the wrong passage, " +
                "politely note it alongside any recitation errors.\n" +
                "13. Respond with STRICT JSON ONLY. No prose, no markdown, no code fences.";

        String userPrompt =
                "Expected Quran passage (assigned lesson):\n\"\"\"\n" + expectedText + "\n\"\"\"\n\n" +
                "Student transcript (from ASR — may contain small errors):\n\"\"\"\n" + transcript + "\n\"\"\"\n\n" +
                "Return JSON with EXACTLY this schema:\n" +
                "{\n" +
                "  \"is_quran\": boolean,\n" +
                "  \"is_quran_confidence\": number 0..1,\n" +
                "  \"reason_if_not_quran\": string (empty if is_quran=true; NEVER mention passage/surah mismatch here),\n" +
                "  \"matches_expected_passage\": boolean,\n" +
                "  \"detected_passages\": string[]   // e.g. [\"Surah Al-Ikhlas 112:1-4\"]\n," +
                "  \"mixed_passages\": boolean,\n" +
                "  \"matched_passage_note\": string   // human-readable one-line note about passage match\n," +
                "  \"reference_text\": string   // the exact Quran text you diffed against\n," +
                "  \"correct_words\": string[],\n" +
                "  \"missing_words\": string[],\n" +
                "  \"incorrect_words\": string[]   // each item formatted as \"<expected> -> <heard>\"\n," +
                "  \"extra_words\": string[],\n" +
                "  \"pronunciation_notes\": string[],\n" +
                "  \"score\": integer 0..100,\n" +
                "  \"summary\": string,\n" +
                "  \"feedback\": string\n" +
                "}";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        // With temperature=0 the model is already greedy; top_p stays at 1 (top_p=0 is degenerate
        // and rejected by some models). Determinism comes from temperature=0 + a fixed seed.
        body.put("top_p", 1);
        body.put("seed", EVALUATOR_SEED);
        body.put("response_format", Collections.singletonMap("type", "json_object"));
        body.put("messages", Arrays.asList(
                mapOf("role", "system", "content", systemPrompt),
                mapOf("role", "user", "content", userPrompt)
        ));
        String jsonBody = MiniJson.stringify(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.log(Level.WARNING, "Evaluator API returned status {0}: {1}",
                    new Object[]{response.statusCode(), truncate(response.body(), 500)});
            return null;
        }

        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;

        // Reproducibility audit trail: identical {model, seed, prompt, fingerprint} -> identical
        // output. If two runs of the same recitation diverge, this is the only legitimate reason.
        LOGGER.log(Level.INFO, "Evaluator system_fingerprint={0}", asString(root.get("system_fingerprint")));

        String content = extractAssistantContent(root);
        if (content == null) return null;

        Object payload;
        try {
            payload = MiniJson.parse(content);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Evaluator returned malformed JSON: {0}", truncate(content, 300));
            return null;
        }
        if (!(payload instanceof Map)) return null;

        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) payload;

        AiReport r = new AiReport();
        r.isQuran = asBool(p.get("is_quran"), true);
        double conf = asDouble(p.get("is_quran_confidence"), 0.0);
        r.isQuranConfidence = Math.max(0.0, Math.min(1.0, conf));
        r.reasonIfNotQuran = asString(p.get("reason_if_not_quran"));
        r.matchesExpectedPassage = asBool(p.get("matches_expected_passage"), true);
        r.mixedPassages = asBool(p.get("mixed_passages"), false);
        r.detectedPassages = asStringList(p.get("detected_passages"));
        r.referenceText = asString(p.get("reference_text"));
        r.matchedPassageNote = asString(p.get("matched_passage_note"));
        r.correctWords = asStringList(p.get("correct_words"));
        r.missingWords = asStringList(p.get("missing_words"));
        r.incorrectWords = asStringList(p.get("incorrect_words"));
        r.extraWords = asStringList(p.get("extra_words"));
        r.pronunciationNotes = asStringList(p.get("pronunciation_notes"));
        r.score = (int) Math.round(asDouble(p.get("score"), 0.0));
        r.summary = asString(p.get("summary"));
        r.feedback = asString(p.get("feedback"));

        // ---- Defensive correction ------------------------------------------------------
        // Some models ignore the distinction and return is_quran=false with a reason that
        // actually describes a passage MISMATCH (e.g. "verses from Al-Ikhlas, not the
        // expected Al-Baqarah"). That's a passage mismatch, not a non-Quran audio.
        // If we detect that pattern, promote to Quran-with-mismatch and let the full
        // report render.
        if (!r.isQuran && reasonLooksLikePassageMismatch(r.reasonIfNotQuran, r.detectedPassages)) {
            LOGGER.log(Level.INFO,
                    "Evaluator returned is_quran=false with a passage-mismatch reason; promoting to Quran+mismatch. reason={0}",
                    truncate(r.reasonIfNotQuran, 200));
            r.isQuran = true;
            r.matchesExpectedPassage = false;
            if (trimToNull(r.matchedPassageNote) == null) {
                r.matchedPassageNote = "This appears to be Quran recitation, but it does not match the expected passage. "
                        + r.reasonIfNotQuran;
            }
            r.reasonIfNotQuran = null;
            // Confidence no longer used for rejection, but normalise so the UI display is sensible.
            r.isQuranConfidence = Math.max(r.isQuranConfidence, 0.5);
        }

        return r;
    }

    /**
     * Heuristic: the evaluator flagged the audio as non-Quran but the *reason* actually
     * describes a passage mismatch. We treat this as a passage mismatch (still Quran).
     */
    private static boolean reasonLooksLikePassageMismatch(String reason, List<String> detectedPassages) {
        if (detectedPassages != null && !detectedPassages.isEmpty()) {
            // If the model identified Quranic passages, the content IS Quran by definition.
            return true;
        }
        if (reason == null) return false;
        String r = reason.toLowerCase(Locale.ROOT);
        String[] signals = {
                "surah", "sura", "surat", "al-", "quran", "qur'an", "qur\u2019an",
                "ayah", "ayat", "verse", "verses", "ikhlas", "kawthar", "baqarah", "fatihah",
                "fatiha", "different passage", "expected passage", "expected surah",
                "not from the expected", "expected lesson"
        };
        for (String s : signals) {
            if (r.contains(s)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private String extractAssistantContent(Map<String, Object> chatResponse) {
        Object choicesObj = chatResponse.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) return null;
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) return null;
        Object messageObj = ((Map<String, Object>) first).get("message");
        if (!(messageObj instanceof Map)) return null;
        Object contentObj = ((Map<String, Object>) messageObj).get("content");
        return asString(contentObj);
    }

    // =============================================================================================
    // Local fallback evaluation (only used if the AI call fails) -- still useful to the instructor
    // =============================================================================================

    private AnalysisResult buildFallbackResult(String expectedText, String transcript) {
        Comparison c = compareTokens(tokenizeArabic(expectedText), tokenizeArabic(transcript));
        int totalExpected = Math.max(1, c.correctWords.size() + c.missingWords.size() + c.replacedWords.size());
        double accuracy = 100.0 * c.correctWords.size() / totalExpected;

        double sim = Math.max(0, Math.min(1, accuracy / 100.0));
        double blended = 0.7 * accuracy + 0.3 * (sim * 100.0);
        int score = (int) Math.round(Math.max(0, Math.min(100, blended)));

        String summary = String.format(Locale.US,
                "Local fallback analysis (AI evaluator unavailable). Accuracy %.1f%%.", accuracy);
        String feedback = buildTemplatedFeedback(c, score);
        String passageNote = "AI evaluator unavailable — showing a local word-diff only.";

        return AnalysisResult.ok(
                transcript,
                expectedText,
                accuracy,
                c.correctWords,
                c.missingWords,
                c.extraWords,
                c.replacedWords,
                feedback,
                score,
                summary,
                passageNote,
                Collections.emptyList(),
                0.0,
                true,                       // assume match; fallback has no better signal
                false,
                Collections.emptyList(),
                expectedText
        );
    }

    private String buildTemplatedFeedback(Comparison c, int score) {
        StringBuilder sb = new StringBuilder();
        if (score >= 90) sb.append("Excellent recitation overall.");
        else if (score >= 75) sb.append("Good recitation with a few noticeable issues.");
        else if (score >= 50) sb.append("Partial match — several words differ and need practice.");
        else sb.append("The recitation has many mismatches and needs significant practice.");
        if (!c.missingWords.isEmpty()) sb.append(" Missing: ").append(summarize(c.missingWords)).append('.');
        if (!c.replacedWords.isEmpty()) sb.append(" Substituted: ").append(summarize(c.replacedWords)).append('.');
        if (!c.extraWords.isEmpty()) sb.append(" Extra/repeated: ").append(summarize(c.extraWords)).append('.');
        return sb.toString();
    }

    private String summarize(List<String> values) {
        if (values == null || values.isEmpty()) return "-";
        int max = Math.min(values.size(), 4);
        String text = String.join(", ", values.subList(0, max));
        return values.size() > max ? text + " ..." : text;
    }

    // =============================================================================================
    // Local word-level diff (fallback + Arabic normalization shared with fallback path)
    // =============================================================================================

    private Comparison compareTokens(List<String> expectedTokens, List<String> heardTokens) {
        if (expectedTokens.isEmpty()) {
            return new Comparison(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());
        }
        int m = expectedTokens.size();
        int n = heardTokens.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = expectedTokens.get(i - 1).equals(heardTokens.get(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }

        List<String> correct = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        List<String> replaced = new ArrayList<>();

        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && expectedTokens.get(i - 1).equals(heardTokens.get(j - 1))
                    && dp[i][j] == dp[i - 1][j - 1]) {
                correct.add(expectedTokens.get(i - 1));
                i--; j--;
                continue;
            }
            if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                replaced.add(expectedTokens.get(i - 1) + " -> " + heardTokens.get(j - 1));
                i--; j--;
                continue;
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) { missing.add(expectedTokens.get(i - 1)); i--; continue; }
            if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) { extra.add(heardTokens.get(j - 1)); j--; continue; }
            if (i > 0) { missing.add(expectedTokens.get(i - 1)); i--; }
            else if (j > 0) { extra.add(heardTokens.get(j - 1)); j--; }
        }
        Collections.reverse(correct);
        Collections.reverse(missing);
        Collections.reverse(extra);
        Collections.reverse(replaced);
        return new Comparison(correct, missing, extra, replaced);
    }

    private List<String> tokenizeArabic(String value) {
        String normalized = normalizeArabic(value);
        if (normalized.isEmpty()) return Collections.emptyList();
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) if (!part.isBlank()) tokens.add(part);
        return tokens;
    }

    private String normalizeArabic(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return "";
        normalized = normalized
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')
                .replace('ة', 'ه');
        normalized = normalized.replaceAll("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]", "");
        normalized = normalized.replaceAll("[^\\p{IsArabic}\\p{Nd}\\s]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    // =============================================================================================
    // Shared helpers
    // =============================================================================================

    private boolean isEffectivelyEmpty(AiReport r) {
        int total = size(r.correctWords) + size(r.missingWords) + size(r.incorrectWords) + size(r.extraWords);
        return total == 0 && r.score <= 0 && trimToNull(r.summary) == null && trimToNull(r.feedback) == null;
    }

    private int clampScore(int score) {
        if (score < 0) return 0;
        if (score > 100) return 100;
        return score;
    }

    private int size(List<?> list) { return list == null ? 0 : list.size(); }
    private <T> List<T> nonNull(List<T> list) { return list == null ? List.of() : list; }
    private String nullOr(String s) { String t = trimToNull(s); return t; }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (Exception ignored) { return fallback; }
        }
        return fallback;
    }

    private static boolean asBool(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
            if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
        }
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        return fallback;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        List<?> src = (List<?>) value;
        List<String> out = new ArrayList<>(src.size());
        for (Object o : src) {
            if (o == null) continue;
            String s = o instanceof CharSequence ? o.toString() : String.valueOf(o);
            String t = trimToNull(s);
            if (t != null) out.add(t);
        }
        return out;
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // =============================================================================================
    // Internal value types
    // =============================================================================================

    private static final class TranscriptionResult {
        final boolean ok;
        final String reason;
        final String transcript;

        private TranscriptionResult(boolean ok, String reason, String transcript) {
            this.ok = ok;
            this.reason = reason;
            this.transcript = transcript;
        }
        static TranscriptionResult ok(String transcript) { return new TranscriptionResult(true, null, transcript); }
        static TranscriptionResult fail(String reason)  { return new TranscriptionResult(false, reason, null); }
    }

    private static final class AiReport {
        boolean isQuran;
        double isQuranConfidence;
        String reasonIfNotQuran;
        boolean matchesExpectedPassage;
        boolean mixedPassages;
        List<String> detectedPassages;
        String referenceText;
        String matchedPassageNote;
        List<String> correctWords;
        List<String> missingWords;
        List<String> incorrectWords; // "expected -> heard" strings
        List<String> extraWords;
        List<String> pronunciationNotes;
        int score;
        String summary;
        String feedback;
    }

    private static final class Comparison {
        final List<String> correctWords;
        final List<String> missingWords;
        final List<String> extraWords;
        final List<String> replacedWords;
        Comparison(List<String> correct, List<String> missing, List<String> extra, List<String> replaced) {
            this.correctWords = correct;
            this.missingWords = missing;
            this.extraWords = extra;
            this.replacedWords = replaced;
        }
    }

    // =============================================================================================
    // Public result type (backward compatible with existing JSP, enriched with new AI fields)
    // =============================================================================================

    public static final class AnalysisResult {
        private final Status status;
        private final String reason;
        private final String transcript;
        private final String expectedText;
        private final double accuracyPercent;
        private final int score;
        private final List<String> correctWords;
        private final List<String> missingWords;
        private final List<String> extraWords;
        private final List<String> replacedWords;
        private final String feedback;
        private final String summary;
        private final String matchedPassageNote;
        private final List<String> pronunciationNotes;
        private final double isQuranConfidence;
        private final boolean matchesExpectedPassage;
        private final boolean mixedPassages;
        private final List<String> detectedPassages;
        private final String referenceText;

        private AnalysisResult(Status status, String reason, String transcript, String expectedText,
                               double accuracyPercent, int score,
                               List<String> correctWords, List<String> missingWords,
                               List<String> extraWords, List<String> replacedWords,
                               String feedback, String summary, String matchedPassageNote,
                               List<String> pronunciationNotes, double isQuranConfidence,
                               boolean matchesExpectedPassage, boolean mixedPassages,
                               List<String> detectedPassages, String referenceText) {
            this.status = status;
            this.reason = reason;
            this.transcript = transcript;
            this.expectedText = expectedText;
            this.accuracyPercent = accuracyPercent;
            this.score = score;
            this.correctWords = correctWords == null ? List.of() : List.copyOf(correctWords);
            this.missingWords = missingWords == null ? List.of() : List.copyOf(missingWords);
            this.extraWords = extraWords == null ? List.of() : List.copyOf(extraWords);
            this.replacedWords = replacedWords == null ? List.of() : List.copyOf(replacedWords);
            this.feedback = feedback;
            this.summary = summary;
            this.matchedPassageNote = matchedPassageNote;
            this.pronunciationNotes = pronunciationNotes == null ? List.of() : List.copyOf(pronunciationNotes);
            this.isQuranConfidence = isQuranConfidence;
            this.matchesExpectedPassage = matchesExpectedPassage;
            this.mixedPassages = mixedPassages;
            this.detectedPassages = detectedPassages == null ? List.of() : List.copyOf(detectedPassages);
            this.referenceText = referenceText;
        }

        static AnalysisResult ok(String transcript, String expectedText, double accuracyPercent,
                                 List<String> correctWords, List<String> missingWords,
                                 List<String> extraWords, List<String> replacedWords,
                                 String feedback, int score, String summary,
                                 String matchedPassageNote, List<String> pronunciationNotes,
                                 double isQuranConfidence,
                                 boolean matchesExpectedPassage, boolean mixedPassages,
                                 List<String> detectedPassages, String referenceText) {
            return new AnalysisResult(Status.OK, null, transcript, expectedText, accuracyPercent, score,
                    correctWords, missingWords, extraWords, replacedWords,
                    feedback, summary, matchedPassageNote, pronunciationNotes, isQuranConfidence,
                    matchesExpectedPassage, mixedPassages, detectedPassages, referenceText);
        }

        static AnalysisResult rejected(String reason) {
            return new AnalysisResult(Status.REJECTED, reason, null, null, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), null, null, null, List.of(), 0.0,
                    true, false, List.of(), null);
        }
        static AnalysisResult cannotEvaluate(String reason) {
            return new AnalysisResult(Status.CANNOT_EVALUATE, reason, null, null, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), null, null, null, List.of(), 0.0,
                    true, false, List.of(), null);
        }
        static AnalysisResult failed(String reason) {
            return new AnalysisResult(Status.FAILED, reason, null, null, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), null, null, null, List.of(), 0.0,
                    true, false, List.of(), null);
        }

        /** Attach transcript/expected context so the UI can display them on failure cards too. */
        AnalysisResult withTranscript(String transcript, String expectedText) {
            return new AnalysisResult(this.status, this.reason, transcript, expectedText,
                    this.accuracyPercent, this.score,
                    this.correctWords, this.missingWords, this.extraWords, this.replacedWords,
                    this.feedback, this.summary, this.matchedPassageNote, this.pronunciationNotes,
                    this.isQuranConfidence, this.matchesExpectedPassage, this.mixedPassages,
                    this.detectedPassages, this.referenceText);
        }

        // --- New structured API ---
        public Status getStatus() { return status; }
        public String getReason() { return reason; }
        public int getScore() { return score; }
        public String getSummary() { return summary; }
        public String getFeedback() { return feedback; }
        public String getMatchedPassageNote() { return matchedPassageNote; }
        public List<String> getPronunciationNotes() { return pronunciationNotes; }
        public double getIsQuranConfidence() { return isQuranConfidence; }
        public boolean isMatchesExpectedPassage() { return matchesExpectedPassage; }
        public boolean isMixedPassages() { return mixedPassages; }
        public List<String> getDetectedPassages() { return detectedPassages; }
        public String getReferenceText() { return referenceText; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            switch (status) {
                case OK:
                    m.put("status", "ok");
                    m.put("transcript", transcript);
                    m.put("expected", expectedText);
                    m.put("matches_expected_passage", matchesExpectedPassage);
                    m.put("mixed_passages", mixedPassages);
                    m.put("detected_passages", detectedPassages);
                    m.put("reference_text", referenceText);
                    m.put("matched_passage_note", matchedPassageNote);
                    m.put("correct_words", correctWords);
                    m.put("missing_words", missingWords);
                    m.put("incorrect_words", replacedWords);
                    m.put("extra_words", extraWords);
                    m.put("pronunciation_notes", pronunciationNotes);
                    m.put("summary", summary);
                    m.put("feedback", feedback);
                    m.put("score", score);
                    m.put("accuracy_percent", accuracyPercent);
                    m.put("is_quran_confidence", isQuranConfidence);
                    break;
                case REJECTED:
                    m.put("status", "rejected");
                    m.put("reason", reason);
                    break;
                case CANNOT_EVALUATE:
                    m.put("status", "cannot_evaluate");
                    m.put("reason", reason);
                    break;
                case FAILED:
                default:
                    m.put("status", "failed");
                    m.put("reason", reason);
                    break;
            }
            return m;
        }

        // --- Legacy getters (kept for the existing JSP) ---
        public boolean isSuccess() { return status == Status.OK; }
        public String getError() { return status == Status.OK ? null : reason; }
        public String getTranscript() { return transcript; }
        public String getExpectedText() { return expectedText; }
        public double getAccuracyPercent() { return accuracyPercent; }
        public List<String> getCorrectWords() { return correctWords; }
        public List<String> getMissingWords() { return missingWords; }
        public List<String> getExtraWords() { return extraWords; }
        public List<String> getReplacedWords() { return replacedWords; }
        public String getDraftFeedback() { return feedback; }
    }

    // =============================================================================================
    // Minimal JSON parser / serializer
    //
    // We avoid pulling a dependency into WEB-INF/lib for a couple of endpoints.
    // Supports: objects, arrays, strings (incl. \\uXXXX), numbers (as Double), booleans, null.
    // =============================================================================================

    private static final class MiniJson {
        private final String src;
        private int pos;

        private MiniJson(String src) { this.src = src; this.pos = 0; }

        static Object parse(String text) {
            if (text == null) throw new IllegalArgumentException("null json");
            MiniJson p = new MiniJson(text);
            p.skipWs();
            Object v = p.readValue();
            p.skipWs();
            return v;
        }

        private Object readValue() {
            skipWs();
            if (pos >= src.length()) throw new IllegalStateException("unexpected end");
            char c = src.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBoolean();
            if (c == 'n') { expectLiteral("null"); return null; }
            return readNumber();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{'); skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = readString();
                skipWs(); expect(':'); skipWs();
                Object value = readValue();
                map.put(key, value);
                skipWs();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw new IllegalStateException("expected , or } at " + pos);
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('['); skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                skipWs();
                list.add(readValue());
                skipWs();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw new IllegalStateException("expected , or ] at " + pos);
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalStateException("bad escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new IllegalStateException("bad unicode escape");
                            int cp = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            pos += 4;
                            sb.append((char) cp);
                            break;
                        default: throw new IllegalStateException("bad escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalStateException("unterminated string");
        }

        private Object readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') pos++;
                else break;
            }
            String num = src.substring(start, pos);
            try { return Double.parseDouble(num); }
            catch (NumberFormatException ex) { throw new IllegalStateException("bad number: " + num); }
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalStateException("bad boolean at " + pos);
        }

        private void expectLiteral(String lit) {
            if (!src.startsWith(lit, pos)) throw new IllegalStateException("expected " + lit);
            pos += lit.length();
        }

        private void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new IllegalStateException("expected '" + c + "' at " + pos);
            }
            pos++;
        }

        private char peek() {
            if (pos >= src.length()) throw new IllegalStateException("unexpected end");
            return src.charAt(pos);
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        static String stringify(Object value) {
            StringBuilder sb = new StringBuilder();
            write(sb, value);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object value) {
            if (value == null) { sb.append("null"); return; }
            if (value instanceof Boolean) { sb.append(((Boolean) value) ? "true" : "false"); return; }
            if (value instanceof Number) { sb.append(value.toString()); return; }
            if (value instanceof CharSequence) { writeString(sb, value.toString()); return; }
            if (value instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(sb, e.getKey());
                    sb.append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
                return;
            }
            if (value instanceof Iterable) {
                sb.append('[');
                boolean first = true;
                for (Object item : (Iterable<?>) value) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, item);
                }
                sb.append(']');
                return;
            }
            writeString(sb, String.valueOf(value));
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':  sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
        }
    }
}
