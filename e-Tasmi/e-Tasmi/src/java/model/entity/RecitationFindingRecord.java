package model.entity;

import model.service.analysis.FindingAiStatus;
import model.service.analysis.FindingInstructorStatus;
import model.service.analysis.FindingType;

import java.time.Instant;

/**
 * One persisted finding row, mirroring {@code recitation_finding}.
 *
 * <p>This is the storage shape, including the instructor-decision columns. It is separate from
 * {@link model.service.analysis.RecitationFinding}, whose constructor always forces
 * {@code PROPOSED} / {@code PENDING}. Reading a later instructor decision back through that
 * class would silently discard it.</p>
 *
 * <p>Phase 3 only inserts {@code PROPOSED} / {@code PENDING} and leaves every instructor column
 * null. The columns are mapped so a later phase can read what it writes.</p>
 */
public class RecitationFindingRecord {
    private long findingId;
    private long analysisId;
    private long recitationId;
    private FindingType findingType;
    private String verseKey;
    private Integer wordPosition;
    private String expectedText;
    private String heardText;
    private String explanation;
    private FindingAiStatus aiStatus;
    private Double aiConfidence;
    private FindingInstructorStatus instructorStatus;
    private String instructorExpectedText;
    private String instructorHeardText;
    private String instructorExplanation;
    private String instructorNote;
    private Long decidedByInstructorId;
    private Instant decidedAt;
    private Instant createdAt;

    public long getFindingId() {
        return findingId;
    }

    public void setFindingId(long findingId) {
        this.findingId = findingId;
    }

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

    public FindingType getFindingType() {
        return findingType;
    }

    public void setFindingType(FindingType findingType) {
        this.findingType = findingType;
    }

    public String getVerseKey() {
        return verseKey;
    }

    public void setVerseKey(String verseKey) {
        this.verseKey = verseKey;
    }

    public Integer getWordPosition() {
        return wordPosition;
    }

    public void setWordPosition(Integer wordPosition) {
        this.wordPosition = wordPosition;
    }

    public String getExpectedText() {
        return expectedText;
    }

    public void setExpectedText(String expectedText) {
        this.expectedText = expectedText;
    }

    public String getHeardText() {
        return heardText;
    }

    public void setHeardText(String heardText) {
        this.heardText = heardText;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public FindingAiStatus getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(FindingAiStatus aiStatus) {
        this.aiStatus = aiStatus;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public FindingInstructorStatus getInstructorStatus() {
        return instructorStatus;
    }

    public void setInstructorStatus(FindingInstructorStatus instructorStatus) {
        this.instructorStatus = instructorStatus;
    }

    public String getInstructorExpectedText() {
        return instructorExpectedText;
    }

    public void setInstructorExpectedText(String instructorExpectedText) {
        this.instructorExpectedText = instructorExpectedText;
    }

    public String getInstructorHeardText() {
        return instructorHeardText;
    }

    public void setInstructorHeardText(String instructorHeardText) {
        this.instructorHeardText = instructorHeardText;
    }

    public String getInstructorExplanation() {
        return instructorExplanation;
    }

    public void setInstructorExplanation(String instructorExplanation) {
        this.instructorExplanation = instructorExplanation;
    }

    public String getInstructorNote() {
        return instructorNote;
    }

    public void setInstructorNote(String instructorNote) {
        this.instructorNote = instructorNote;
    }

    public Long getDecidedByInstructorId() {
        return decidedByInstructorId;
    }

    public void setDecidedByInstructorId(Long decidedByInstructorId) {
        this.decidedByInstructorId = decidedByInstructorId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
