package model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One persisted analysis run, mirroring {@code recitation_analysis}.
 *
 * <p>Rows are append-only. A re-analysis inserts a new row and never updates or deletes an
 * earlier one. {@code findings} is populated by the DAO when a caller asks for them; it is
 * empty when only the analysis header was loaded.</p>
 */
public class RecitationAnalysis {
    private long analysisId;
    private long recitationId;
    private String status;
    private String statusReason;
    private String sttProvider;
    private String sttModel;
    private String analysisModel;
    private String transcript;
    private String referenceSource;
    private String referenceVerseKeys;
    private String referenceText;
    private Boolean matchesExpectedPassage;
    private Double accuracyPercent;
    private Integer aiSuggestedScore;
    private String aiSummary;
    private String aiFeedback;
    private String matchedPassageNote;
    private Integer correctWordCount;
    private Double isQuranConfidence;
    private Boolean mixedPassages;
    private String detectedPassages;
    private Instant createdAt;
    private List<RecitationFindingRecord> findings = new ArrayList<>();

    public long getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(long analysisId) {
        this.analysisId = analysisId;
    }

    public long getRecitationId() {
        return recitationId;
    }

    public void setRecitationId(long recitationId) {
        this.recitationId = recitationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public String getSttProvider() {
        return sttProvider;
    }

    public void setSttProvider(String sttProvider) {
        this.sttProvider = sttProvider;
    }

    public String getSttModel() {
        return sttModel;
    }

    public void setSttModel(String sttModel) {
        this.sttModel = sttModel;
    }

    public String getAnalysisModel() {
        return analysisModel;
    }

    public void setAnalysisModel(String analysisModel) {
        this.analysisModel = analysisModel;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getReferenceSource() {
        return referenceSource;
    }

    public void setReferenceSource(String referenceSource) {
        this.referenceSource = referenceSource;
    }

    public String getReferenceVerseKeys() {
        return referenceVerseKeys;
    }

    public void setReferenceVerseKeys(String referenceVerseKeys) {
        this.referenceVerseKeys = referenceVerseKeys;
    }

    public String getReferenceText() {
        return referenceText;
    }

    public void setReferenceText(String referenceText) {
        this.referenceText = referenceText;
    }

    public Boolean getMatchesExpectedPassage() {
        return matchesExpectedPassage;
    }

    public void setMatchesExpectedPassage(Boolean matchesExpectedPassage) {
        this.matchesExpectedPassage = matchesExpectedPassage;
    }

    public Double getAccuracyPercent() {
        return accuracyPercent;
    }

    public void setAccuracyPercent(Double accuracyPercent) {
        this.accuracyPercent = accuracyPercent;
    }

    public Integer getAiSuggestedScore() {
        return aiSuggestedScore;
    }

    public void setAiSuggestedScore(Integer aiSuggestedScore) {
        this.aiSuggestedScore = aiSuggestedScore;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public void setAiFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    public String getMatchedPassageNote() {
        return matchedPassageNote;
    }

    public void setMatchedPassageNote(String matchedPassageNote) {
        this.matchedPassageNote = matchedPassageNote;
    }

    public Integer getCorrectWordCount() {
        return correctWordCount;
    }

    public void setCorrectWordCount(Integer correctWordCount) {
        this.correctWordCount = correctWordCount;
    }

    public Double getIsQuranConfidence() {
        return isQuranConfidence;
    }

    public void setIsQuranConfidence(Double isQuranConfidence) {
        this.isQuranConfidence = isQuranConfidence;
    }

    public Boolean getMixedPassages() {
        return mixedPassages;
    }

    public void setMixedPassages(Boolean mixedPassages) {
        this.mixedPassages = mixedPassages;
    }

    public String getDetectedPassages() {
        return detectedPassages;
    }

    public void setDetectedPassages(String detectedPassages) {
        this.detectedPassages = detectedPassages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<RecitationFindingRecord> getFindings() {
        return findings;
    }

    public void setFindings(List<RecitationFindingRecord> findings) {
        this.findings = findings == null ? new ArrayList<>() : findings;
    }
}
