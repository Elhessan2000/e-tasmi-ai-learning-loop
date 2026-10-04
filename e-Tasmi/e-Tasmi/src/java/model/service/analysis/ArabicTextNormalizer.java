package model.service.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalisation used to compare trusted Uthmani mushaf text against an ASR transcript.
 *
 * <p>The two sides use different orthographies. The mushaf writes a dagger (superscript)
 * alef, alef wasla, and full harakat; an ASR transcript writes plain modern spelling with
 * few or no marks. Comparing them verbatim manufactures a finding on almost every word, so
 * every comparison runs on a normalised copy. Display always uses the original surface form:
 * normalised strings are never rendered, stored, or logged.</p>
 *
 * <p><b>Dagger alef.</b> {@code U+0670} cannot simply be deleted and cannot simply be kept.
 * Deleting it turns {@code الصرط} against a transcript's {@code الصراط} into a false finding;
 * expanding it to a plain alef turns {@code الرحمان} against a transcript's {@code الرحمن}
 * into a different false finding. A reference word therefore carries two normalised forms —
 * one with the dagger expanded, one with it elided — and matches when either form equals the
 * heard word. That is narrower than collapsing every alef, so a genuine alef substitution
 * such as {@code قال} against {@code قل} is still reported.</p>
 */
public final class ArabicTextNormalizer {

    private static final char ALEF = '\u0627';
    private static final char YEH = '\u064A';
    private static final char WAW = '\u0648';
    private static final char HEH = '\u0647';
    private static final char KAF = '\u0643';
    private static final char DAGGER_ALEF = '\u0670';

    private ArabicTextNormalizer() {
    }

    /** Splits on whitespace, preserving each word's original surface form. */
    public static List<String> splitWords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (String part : raw.trim().split("\\s+")) {
            String word = part.trim();
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Normalised form with the dagger alef expanded to a plain alef.
     * This is the form used for the heard (transcript) side.
     */
    public static String normalize(String value) {
        return normalize(value, true);
    }

    /** Normalised form with the dagger alef removed rather than expanded. */
    public static String normalizeDaggerElided(String value) {
        return normalize(value, false);
    }

    private static String normalize(String value, boolean expandDaggerAlef) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == DAGGER_ALEF) {
                if (expandDaggerAlef) {
                    out.append(ALEF);
                }
                continue;
            }
            if (isRemovedMark(c)) {
                continue;
            }

            char mapped = mapLetter(c);
            if (mapped == 0) {
                continue;
            }
            if (Character.isWhitespace(mapped)) {
                out.append(' ');
                continue;
            }
            // Everything that survives normalisation is an Arabic letter. Digits, Latin text,
            // punctuation, and ayah numbers are dropped so they cannot become findings.
            if (mapped >= '\u0621' && mapped <= '\u064A') {
                out.append(mapped);
            }
        }

        return collapseWhitespace(out.toString());
    }

    /**
     * Harakat, Qur'anic annotation and pause marks, tatweel, and bidi controls.
     * The dagger alef ({@code U+0670}) is deliberately not part of this set.
     */
    private static boolean isRemovedMark(char c) {
        if (c == '\u0640') {               // tatweel
            return true;
        }
        if (c >= '\u0610' && c <= '\u061A') {   // honorifics and small high marks
            return true;
        }
        if (c >= '\u064B' && c <= '\u065F') {   // harakat, shadda, sukun, small vowels
            return true;
        }
        if (c >= '\u06D6' && c <= '\u06ED') {   // pause marks, sajda, rub el hizb, ayah end
            return true;
        }
        if (c == '\u061C' || (c >= '\u200C' && c <= '\u200F')) {   // bidi and joiner controls
            return true;
        }
        return c == '\u0621';             // standalone hamza: ASR drops it inconsistently
    }

    /** Returns the comparison letter, {@code 0} to drop the character. */
    private static char mapLetter(char c) {
        switch (c) {
            case '\u0622':   // alef with madda
            case '\u0623':   // alef with hamza above
            case '\u0625':   // alef with hamza below
            case '\u0671':   // alef wasla
            case '\u0672':
            case '\u0673':
            case '\u0675':
                return ALEF;
            case '\u0649':   // alef maqsura
            case '\u0626':   // yeh with hamza
            case '\u06CC':   // Farsi yeh
                return YEH;
            case '\u0624':   // waw with hamza
                return WAW;
            case '\u0629':   // ta marbuta
                return HEH;
            case '\u06A9':   // Keheh
                return KAF;
            default:
                return c;
        }
    }

    private static String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
