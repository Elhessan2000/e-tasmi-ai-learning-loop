package model.service.stt;

/**
 * One speech-to-text vendor. The analysis service selects an implementation
 * from configuration and does not call a vendor HTTP API itself.
 */
public interface SpeechToTextProvider {

    /** Stable id stored on the analysis row, such as {@code openai} or {@code elevenlabs}. */
    String id();

    /** Model name that will be sent, or the default when the override is empty. */
    String model();

    boolean configured();

    /** Shown when {@link #configured()} is false. Must not contain secret values. */
    String unavailableReason();

    SpeechTranscript transcribe(byte[] audio, String fileName);
}
