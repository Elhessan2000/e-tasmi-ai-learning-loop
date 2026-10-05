package model.service.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which leading transcript words sit outside the assigned passage.
 *
 * <p>Runs before alignment and uses only {@link ArabicTextNormalizer}. It never calls a
 * model. A phrase is removed only when the trusted reference does not itself begin with
 * that phrase, so Al-Fatihah 1:1 (the basmala in the Hafs text) stays in the comparison.</p>
 */
public final class RecitationBoundaryPolicy {

    public static final String OPENING_PREFIX = "[[boundary:opening]] ";
    public static final String CONTINUATION_PREFIX = "[[boundary:continuation]] ";

    public static final String OPENING_NOTE = "Opening formula detected before the assigned passage.";
    public static final String CONTINUATION_NOTE = "Student continued beyond the assigned passage.";

    /** Longest form first so the shorter form is not taken out of a longer match. */
    private static final List<List<String>> ISTIADHAH = List.of(
            phrase("أعوذ بالله السميع العليم من الشيطان الرجيم"),
            phrase("أعوذ بالله من الشيطان الرجيم")
    );
    private static final List<String> BASMALA = phrase("بسم الله الرحمن الرحيم");

    private RecitationBoundaryPolicy() {
    }

    static final class Split {
        final List<String> surfaces;
        final List<String> normalized;
        final String openingNote;

        Split(List<String> surfaces, List<String> normalized, String openingNote) {
            this.surfaces = surfaces;
            this.normalized = normalized;
            this.openingNote = openingNote;
        }
    }

    static Split stripLeading(List<ReferenceToken> reference,
                              List<String> surfaces,
                              List<String> normalized) {
        int index = 0;
        boolean istiadhah = false;
        boolean basmala = false;

        int istiadhahLength = leadingLength(normalized, index, ISTIADHAH, reference);
        if (istiadhahLength > 0) {
            index += istiadhahLength;
            istiadhah = true;
        }
        if (matchesAt(normalized, index, BASMALA) && !referenceStartsWith(reference, BASMALA)) {
            index += BASMALA.size();
            basmala = true;
        }

        if (index == 0) {
            return new Split(surfaces, normalized, null);
        }
        return new Split(surfaces.subList(index, surfaces.size()),
                normalized.subList(index, normalized.size()),
                openingNote(istiadhah, basmala));
    }

    /** Folds boundary lines into the existing passage note without replacing it. */
    public static String mergeIntoPassageNote(String passageNote, ComparisonOutcome comparison) {
        if (comparison == null) {
            return passageNote;
        }
        StringBuilder text = new StringBuilder();
        if (passageNote != null && !passageNote.isBlank()) {
            text.append(passageNote.trim());
        }
        appendLine(text, OPENING_PREFIX, comparison.getOpeningNote());
        appendLine(text, CONTINUATION_PREFIX, comparison.getContinuationNote());
        return text.length() == 0 ? null : text.toString();
    }

    public static String openingText(String passageNote) {
        return lineAfter(passageNote, OPENING_PREFIX);
    }

    public static String continuationText(String passageNote) {
        return lineAfter(passageNote, CONTINUATION_PREFIX);
    }

    private static String openingNote(boolean istiadhah, boolean basmala) {
        if (istiadhah && basmala) {
            return OPENING_NOTE + " Isti'adhah and basmala were not part of the assigned passage.";
        }
        if (istiadhah) {
            return OPENING_NOTE + " Isti'adhah was not part of the assigned passage.";
        }
        if (basmala) {
            return OPENING_NOTE + " The basmala was not part of the assigned passage.";
        }
        return OPENING_NOTE;
    }

    private static int leadingLength(List<String> heard, int index, List<List<String>> phrases,
                                     List<ReferenceToken> reference) {
        for (List<String> phrase : phrases) {
            if (matchesAt(heard, index, phrase) && !referenceStartsWith(reference, phrase)) {
                return phrase.size();
            }
        }
        return 0;
    }

    private static boolean matchesAt(List<String> heard, int index, List<String> phrase) {
        if (index < 0 || heard.size() - index < phrase.size()) {
            return false;
        }
        for (int i = 0; i < phrase.size(); i++) {
            if (!phrase.get(i).equals(heard.get(index + i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean referenceStartsWith(List<ReferenceToken> reference, List<String> phrase) {
        if (reference.size() < phrase.size()) {
            return false;
        }
        for (int i = 0; i < phrase.size(); i++) {
            if (!reference.get(i).matches(phrase.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> phrase(String arabic) {
        List<String> tokens = new ArrayList<>();
        for (String word : ArabicTextNormalizer.splitWords(arabic)) {
            String normalized = ArabicTextNormalizer.normalize(word);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return List.copyOf(tokens);
    }

    private static void appendLine(StringBuilder text, String prefix, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(prefix).append(note.trim());
    }

    private static String lineAfter(String passageNote, String prefix) {
        if (passageNote == null || passageNote.isBlank()) {
            return null;
        }
        for (String line : passageNote.split("\\R")) {
            if (line.startsWith(prefix)) {
                String value = line.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
