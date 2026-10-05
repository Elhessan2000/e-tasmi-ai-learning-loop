package model.service.analysis;

import model.entity.RecitationFindingRecord;
import util.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding rows for the instructor review workspace, in {@link FindingGroups} order.
 * The output is safe to embed inside a {@code <script>} element.
 */
public final class FindingReviewPayload {

    private FindingReviewPayload() {
    }

    public static String findingsJson(List<RecitationFindingRecord> rows) {
        List<Object> items = new ArrayList<>();
        for (FindingGroups.TypeGroup group : FindingGroups.group(rows)) {
            for (FindingGroups.AyahGroup ayah : group.getAyahs()) {
                for (RecitationFindingRecord row : ayah.getFindings()) {
                    items.add(item(row));
                }
            }
        }
        return scriptSafe(JsonUtil.value(items));
    }

    private static Map<String, Object> item(RecitationFindingRecord row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getFindingId());
        m.put("type", row.getFindingType().name());
        m.put("verseKey", row.getVerseKey());
        m.put("word", row.getWordPosition());
        m.put("expected", row.getExpectedText());
        m.put("heard", row.getHeardText());
        m.put("explanation", row.getExplanation());
        m.put("status", row.getInstructorStatus() == null ? null : row.getInstructorStatus().name());
        m.put("instructorOwned", row.getAiStatus() == FindingAiStatus.NOT_APPLICABLE);
        m.put("iExpected", row.getInstructorExpectedText());
        m.put("iHeard", row.getInstructorHeardText());
        m.put("iExplanation", row.getInstructorExplanation());
        m.put("iNote", row.getInstructorNote());
        return m;
    }

    /** Markup-significant characters only occur inside JSON strings, so escaping them keeps valid JSON. */
    private static String scriptSafe(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '<': sb.append("\\u003c"); break;
                case '>': sb.append("\\u003e"); break;
                case '&': sb.append("\\u0026"); break;
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
