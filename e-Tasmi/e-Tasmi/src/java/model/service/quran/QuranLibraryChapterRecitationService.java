package model.service.quran;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter-level MP3, ayah timings, and reciter catalogue from Content API.
 * <p>
 * Parsed {@code timestamps} arrays from {@code audio_file.timestamps[]} power ayah-follows-audio highlighting.
 */
public final class QuranLibraryChapterRecitationService {

    private static final Logger LOGGER = Logger.getLogger(QuranLibraryChapterRecitationService.class.getName());

    /** Relay cap for catalogue JSON */
    public static final int MAX_RELAY_CHARS = 400_000;

    private static final Pattern PAT_AUDIO_URL =
            Pattern.compile("\"audio_url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.MULTILINE);
    private static final Pattern PAT_DURATION =
            Pattern.compile("\"duration\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    /** Verse timing objects nest {@code segments} arrays; match only verse_key + millis in the slice after each match. */
    private static final Pattern PAT_VERSE_KEY_IN_TIMESTAMPS =
            Pattern.compile("\"verse_key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern PAT_TIMESTAMP_FROM =
            Pattern.compile("\"timestamp_from\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern PAT_TIMESTAMP_TO =
            Pattern.compile("\"timestamp_to\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    private final QuranFoundationClient client = new QuranFoundationClient();

    /** {@code GET /content/api/v4/resources/chapter_reciters}. */
    public HttpResponse<String> fetchChapterRecitersCatalogRaw() throws IOException {
        HttpResponse<String> resp = client.getJson("/content/api/v4/resources/chapter_reciters");
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            LOGGER.log(Level.WARNING, "QF chapter_reciters catalogue HTTP {0}", resp.statusCode());
        }
        return resp;
    }

    /**
     * Whole-chapter recording via documented {@code GET /chapter_recitations/{reciter_id}/{chapter_number}} —
     * not {@code chapter_reciters}. With {@code segments=true}, Quran Foundation returns {@code timestamps[]}
     * on {@code audio_file} (ayah-level {@code verse_key}, {@code timestamp_from}, {@code timestamp_to}) per
     * <a href="https://api-docs.quran.foundation/docs/content_apis_versioned/4.0.0/chapter-reciter-audio-file/">Chapter reciter audio file</a>.
     */
    public HttpResponse<String> fetchChapterReciterMp3Payload(int chapterNumber, long reciterId) throws IOException {
        QuranLibraryVersesService.validateChapter(chapterNumber);
        if (reciterId <= 0L || reciterId > 9_999L) {
            throw new IOException("reciter_id out of range");
        }
        String path = "/content/api/v4/chapter_recitations/" + reciterId + "/" + chapterNumber + "?segments=true";
        HttpResponse<String> resp = client.getJson(path);
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOGGER.log(Level.WARNING,
                    "QF chapter_reciter audio rejected: chapter={0} reciter={1} HTTP {2}",
                    new Object[]{Integer.valueOf(chapterNumber), Long.valueOf(reciterId), Integer.valueOf(code)});
        }
        return resp;
    }

    /**
     * Extracts {@code audio_url} from {@code audio_file}, optional duration, and {@code audio_file.timestamps[]}
     * as {@code verseTimings}: {@code [{verseKey, from, to}]} in milliseconds (same contract as previous JS).
     */
    public static Map<String, Object> parseAudioPublicMap(HttpResponse<String> upstream) throws IOException {
        String body = upstream.body();
        if (body == null || body.isBlank()) {
            throw new IOException("empty audio response");
        }
        Matcher m = PAT_AUDIO_URL.matcher(body);
        if (!m.find()) {
            throw new IOException("audio_url not found");
        }
        String url = QuranVersesParse.unescapeJsonString(m.group(1));
        if (url == null || url.isBlank()) {
            throw new IOException("audio_url empty");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audioUrl", url.startsWith("//") ? "https:" + url : url);

        Matcher d = PAT_DURATION.matcher(body);
        if (d.find()) {
            try {
                out.put("durationSec", Double.valueOf(Double.parseDouble(d.group(1))));
            } catch (NumberFormatException ignored) {
                /* leave duration off when malformed */
            }
        }

        List<Map<String, Object>> timings = parseTimestampArray(body);
        if (!timings.isEmpty()) {
            out.put("verseTimings", timings);
        }
        return out;
    }

    /** Parses {@code audio_file.timestamps} / top-level {@code timestamps} verse entries after {@code segments=true}. */
    private static List<Map<String, Object>> parseTimestampArray(String body) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int idx = body.indexOf("\"timestamps\"");
        if (idx < 0) {
            return rows;
        }
        Matcher vm = PAT_VERSE_KEY_IN_TIMESTAMPS.matcher(body);
        vm.region(idx, Math.min(body.length(), idx + 1_200_000));
        while (vm.find()) {
            String verseKey = QuranVersesParse.unescapeJsonString(vm.group(1));
            if (verseKey == null || verseKey.isBlank()) {
                continue;
            }
            int segStart = vm.start();
            int segEnd = Math.min(body.length(), segStart + 2_048);
            String chunk = body.substring(segStart, segEnd);
            Long fromMs = firstLongLike(PAT_TIMESTAMP_FROM, chunk);
            Long toMs = firstLongLike(PAT_TIMESTAMP_TO, chunk);
            if (fromMs == null || toMs == null || toMs < fromMs) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("verseKey", verseKey);
            r.put("from", fromMs);
            r.put("to", toMs);
            rows.add(r);
        }
        return rows;
    }

    private static Long firstLongLike(Pattern pat, String row) {
        Matcher m = pat.matcher(row);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.valueOf((long) Double.parseDouble(m.group(1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String cappedRelayBody(HttpResponse<String> resp) {
        String b = resp.body();
        if (b == null) {
            return "";
        }
        return b.length() > MAX_RELAY_CHARS ? b.substring(0, MAX_RELAY_CHARS) : b;
    }
}
