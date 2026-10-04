package model.service;

import model.service.quran.TrustedReference;

import java.util.Collections;
import java.util.List;

/**
 * Student result page: verified view plus safe display context (dates, journey phase, QF verses).
 */
public final class StudentRecitationResultPage {
    private final VerifiedRecitationView view;
    private final String submittedDateLabel;
    private final StudentRecitationAnalysisPhase analysisPhase;
    private final int journeyActiveStep;
    private final List<TrustedReference.Verse> displayVerses;
    private final String storedReferenceText;

    public StudentRecitationResultPage(VerifiedRecitationView view, String submittedDateLabel,
                                       StudentRecitationAnalysisPhase analysisPhase, int journeyActiveStep,
                                       List<TrustedReference.Verse> displayVerses) {
        this(view, submittedDateLabel, analysisPhase, journeyActiveStep, displayVerses, null);
    }

    public StudentRecitationResultPage(VerifiedRecitationView view, String submittedDateLabel,
                                       StudentRecitationAnalysisPhase analysisPhase, int journeyActiveStep,
                                       List<TrustedReference.Verse> displayVerses, String storedReferenceText) {
        this.view = view;
        this.submittedDateLabel = submittedDateLabel == null ? "" : submittedDateLabel;
        this.analysisPhase = analysisPhase;
        this.journeyActiveStep = journeyActiveStep;
        this.displayVerses = displayVerses == null ? List.of() : List.copyOf(displayVerses);
        this.storedReferenceText = storedReferenceText == null || storedReferenceText.isBlank()
                ? ""
                : storedReferenceText.trim();
    }

    public VerifiedRecitationView getView() {
        return view;
    }

    public String getSubmittedDateLabel() {
        return submittedDateLabel;
    }

    /**
     * Null when the evaluation is published (journey step 4).
     */
    public StudentRecitationAnalysisPhase getAnalysisPhase() {
        return analysisPhase;
    }

    /** 1=Submitted … 5=Practice Again (only meaningful when published for step 5). */
    public int getJourneyActiveStep() {
        return journeyActiveStep;
    }

    public List<TrustedReference.Verse> getDisplayVerses() {
        return Collections.unmodifiableList(displayVerses);
    }

    /** Stored QF {@code reference_text} from the analysis row, used when live verse fetch is empty. */
    public String getStoredReferenceText() {
        return storedReferenceText;
    }
}
