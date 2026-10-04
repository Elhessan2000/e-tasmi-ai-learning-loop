package model.service.stt;

/** Speech-to-text outcome. Failure text is safe to show; it never includes credentials. */
public final class SpeechTranscript {

    private final boolean ok;
    private final String text;
    private final String failureReason;

    private SpeechTranscript(boolean ok, String text, String failureReason) {
        this.ok = ok;
        this.text = text;
        this.failureReason = failureReason;
    }

    public static SpeechTranscript ok(String text) {
        return new SpeechTranscript(true, text, null);
    }

    public static SpeechTranscript fail(String reason) {
        return new SpeechTranscript(false, null, reason);
    }

    public boolean isOk() {
        return ok;
    }

    public String getText() {
        return text;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
