package model.service.analysis;

/**
 * Mirrors {@code recitation_finding.instructor_status}; the human verification gate.
 *
 * <p>Only {@link #ACCEPTED}, {@link #EDITED}, and {@link #INSTRUCTOR_ADDED} may ever reach a
 * student. Nothing may move a finding off {@link #PENDING} except an explicit instructor
 * decision. After the evaluation is saved the status is frozen.</p>
 */
public enum FindingInstructorStatus {
    PENDING,
    ACCEPTED,
    EDITED,
    REJECTED,
    INSTRUCTOR_ADDED
}
