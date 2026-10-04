package model.service.quran;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Attributable Qur'an passage used as the Challenge comparison reference.
 * The Arabic text is copied verbatim from the trusted source — never rewritten.
 */
public final class TrustedReference {

    public static final String SOURCE_QURAN_FOUNDATION = "QURAN_FOUNDATION";
    public static final String SOURCE_QURANPEDIA = "QURANPEDIA";

    private final String source;
    private final String verseKeys;
    private final String referenceText;
    private final List<Verse> verses;

    public TrustedReference(String source, String verseKeys, String referenceText, List<Verse> verses) {
        this.source = Objects.requireNonNull(source, "source");
        this.verseKeys = Objects.requireNonNull(verseKeys, "verseKeys");
        this.referenceText = Objects.requireNonNull(referenceText, "referenceText");
        this.verses = List.copyOf(verses == null ? List.of() : verses);
    }

    public String getSource() {
        return source;
    }

    public String getVerseKeys() {
        return verseKeys;
    }

    public String getReferenceText() {
        return referenceText;
    }

    public List<Verse> getVerses() {
        return Collections.unmodifiableList(verses);
    }

    public static final class Verse {
        private final String verseKey;
        private final String uthmaniText;

        public Verse(String verseKey, String uthmaniText) {
            this.verseKey = verseKey;
            this.uthmaniText = uthmaniText;
        }

        public String getVerseKey() {
            return verseKey;
        }

        public String getUthmaniText() {
            return uthmaniText;
        }
    }
}
