package model.service.analysis;

import java.util.Objects;

/**
 * One addressable finding about a recitation, mirroring the AI-side columns of
 * {@code recitation_finding}.
 *
 * <p>Immutable. Every finding produced by the analysis pipeline is created with
 * {@link FindingAiStatus#PROPOSED} and {@link FindingInstructorStatus#PENDING}: a finding only
 * leaves {@code PENDING} through an explicit instructor decision, so nothing here may ever be
 * created as accepted.</p>
 *
 * <p>{@code expectedText} and {@code heardText} hold original surface forms, never normalised
 * comparison strings. Word-level findings carry a {@code verseKey} and a 1-based
 * {@code wordPosition} within that verse, which is what makes them individually verifiable.</p>
 */
public final class RecitationFinding {

    private final FindingType type;
    private final String verseKey;
    private final Integer wordPosition;
    private final String expectedText;
    private final String heardText;
    private final String explanation;
    private final FindingAiStatus aiStatus;
    private final FindingInstructorStatus instructorStatus;

    private RecitationFinding(FindingType type,
                             String verseKey,
                             Integer wordPosition,
                             String expectedText,
                             String heardText,
                             String explanation) {
        this.type = Objects.requireNonNull(type, "type");
        this.verseKey = verseKey;
        this.wordPosition = wordPosition;
        this.expectedText = expectedText;
        this.heardText = heardText;
        this.explanation = explanation;
        this.aiStatus = FindingAiStatus.PROPOSED;
        this.instructorStatus = FindingInstructorStatus.PENDING;
    }

    public static RecitationFinding missingWord(String verseKey, int wordPosition, String expectedText) {
        return new RecitationFinding(FindingType.MISSING_WORD, verseKey, wordPosition, expectedText, null, null);
    }

    public static RecitationFinding incorrectWord(String verseKey, int wordPosition,
                                                  String expectedText, String heardText) {
        return new RecitationFinding(FindingType.INCORRECT_WORD, verseKey, wordPosition,
                expectedText, heardText, null);
    }

    /**
     * An extra word has no reference position of its own, so it is anchored to the preceding
     * reference word to stay addressable. {@code verseKey} is null only when the extra word
     * precedes every reference word.
     */
    public static RecitationFinding extraWord(String verseKey, Integer wordPosition, String heardText) {
        return new RecitationFinding(FindingType.EXTRA_WORD, verseKey, wordPosition, null, heardText, null);
    }

    public static RecitationFinding passageMismatch(String explanation) {
        return new RecitationFinding(FindingType.PASSAGE_MISMATCH, null, null, null, null, explanation);
    }

    /** Advisory only: the instructor must listen to the audio to confirm or reject it. */
    public static RecitationFinding pronunciationObservation(String explanation) {
        return new RecitationFinding(FindingType.PRONUNCIATION_OBSERVATION, null, null, null, null, explanation);
    }

    /** Returns a copy carrying the supplied explanation; all other values are unchanged. */
    public RecitationFinding withExplanation(String newExplanation) {
        return new RecitationFinding(type, verseKey, wordPosition, expectedText, heardText, newExplanation);
    }

    public FindingType getType() {
        return type;
    }

    public String getVerseKey() {
        return verseKey;
    }

    public Integer getWordPosition() {
        return wordPosition;
    }

    public String getExpectedText() {
        return expectedText;
    }

    public String getHeardText() {
        return heardText;
    }

    public String getExplanation() {
        return explanation;
    }

    public FindingAiStatus getAiStatus() {
        return aiStatus;
    }

    public FindingInstructorStatus getInstructorStatus() {
        return instructorStatus;
    }

    /** Position label such as {@code 1:5#3}. Safe to log: it carries no Qur'an text. */
    public String locationLabel() {
        if (verseKey == null) {
            return "-";
        }
        return wordPosition == null ? verseKey : verseKey + "#" + wordPosition;
    }
}
