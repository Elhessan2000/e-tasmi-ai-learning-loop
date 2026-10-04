package model.service.stt;

final class UnknownSpeechToTextProvider implements SpeechToTextProvider {

    private final String name;

    UnknownSpeechToTextProvider(String name) {
        this.name = name == null ? "" : name;
    }

    @Override
    public String id() {
        return "unknown";
    }

    @Override
    public String model() {
        return "";
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return "STT_PROVIDER is not supported. Use openai or elevenlabs.";
    }

    @Override
    public SpeechTranscript transcribe(byte[] audio, String fileName) {
        return SpeechTranscript.fail(unavailableReason());
    }
}
