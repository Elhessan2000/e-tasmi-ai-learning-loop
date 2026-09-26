package model.entity;

/**
 * Combines Enrollment and TasmiSession for student session views.
 */
public class StudentSessionView {
    private Enrollment enrollment;
    private TasmiSession session;

    public StudentSessionView(Enrollment enrollment, TasmiSession session) {
        this.enrollment = enrollment;
        this.session = session;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    public TasmiSession getSession() {
        return session;
    }
}