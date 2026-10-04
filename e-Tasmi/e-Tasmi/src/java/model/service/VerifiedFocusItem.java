package model.service;

/**
 * One learner-facing practice point. It carries no finding id, analysis id, AI status,
 * confidence, provider, or transcript.
 */
public final class VerifiedFocusItem {
    private final String typeKey;
    private final String verseLabel;
    private final Integer wordPosition;
    private final String expectedPhrase;
    private final String heardPhrase;
    private final String guidance;
    private final String hint;
    private final String practiceKey;

    public VerifiedFocusItem(String typeKey, String verseLabel, Integer wordPosition,
                             String expectedPhrase, String heardPhrase,
                             String guidance, String hint, String practiceKey) {
        this.typeKey = typeKey;
        this.verseLabel = verseLabel;
        this.wordPosition = wordPosition;
        this.expectedPhrase = expectedPhrase;
        this.heardPhrase = heardPhrase;
        this.guidance = guidance;
        this.hint = hint;
        this.practiceKey = practiceKey;
    }

    public String getTypeKey() {
        return typeKey;
    }

    public String getVerseLabel() {
        return verseLabel;
    }

    public Integer getWordPosition() {
        return wordPosition;
    }

    public String getExpectedPhrase() {
        return expectedPhrase;
    }

    public String getHeardPhrase() {
        return heardPhrase;
    }

    public String getGuidance() {
        return guidance;
    }

    public String getHint() {
        return hint;
    }

    public String getPracticeKey() {
        return practiceKey;
    }
}
