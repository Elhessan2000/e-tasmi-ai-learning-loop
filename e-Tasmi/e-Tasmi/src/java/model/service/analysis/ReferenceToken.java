package model.service.analysis;

import java.util.Objects;

/**
 * One word of the trusted reference, carrying the location that makes a finding addressable.
 *
 * <p>{@code surface} is the original Uthmani spelling and is the only form ever displayed.
 * {@code normalized} and {@code normalizedElided} differ only in how the dagger alef was
 * treated; a heard word matches this token when it equals either one. See
 * {@link ArabicTextNormalizer}.</p>
 */
public final class ReferenceToken {

    private final String verseKey;
    private final int wordPosition;
    private final String surface;
    private final String normalized;
    private final String normalizedElided;

    public ReferenceToken(String verseKey, int wordPosition, String surface) {
        this.verseKey = Objects.requireNonNull(verseKey, "verseKey");
        this.wordPosition = wordPosition;
        this.surface = Objects.requireNonNull(surface, "surface");
        this.normalized = ArabicTextNormalizer.normalize(surface);
        this.normalizedElided = ArabicTextNormalizer.normalizeDaggerElided(surface);
    }

    public String getVerseKey() {
        return verseKey;
    }

    public int getWordPosition() {
        return wordPosition;
    }

    public String getSurface() {
        return surface;
    }

    public String getNormalized() {
        return normalized;
    }

    /** True when normalisation left nothing comparable, e.g. a standalone pause mark. */
    public boolean isEmpty() {
        return normalized.isEmpty() && normalizedElided.isEmpty();
    }

    /** Accepts either dagger-alef variant, so mushaf orthography is not reported as an error. */
    public boolean matches(String heardNormalized) {
        if (heardNormalized == null || heardNormalized.isEmpty()) {
            return false;
        }
        return heardNormalized.equals(normalized) || heardNormalized.equals(normalizedElided);
    }
}
