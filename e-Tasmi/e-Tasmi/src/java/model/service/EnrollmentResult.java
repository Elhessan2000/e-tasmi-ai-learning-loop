package model.service;

public class EnrollmentResult {
    private final boolean success;
    private final String error;
    private final long enrollmentId;
    private final boolean alreadyEnrolled;
    private final boolean paymentRequired;
    private final String code;
    private final String message;

    private EnrollmentResult(boolean success,
                             String error,
                             long enrollmentId,
                             boolean alreadyEnrolled,
                             boolean paymentRequired,
                             String code,
                             String message) {
        this.success = success;
        this.error = error;
        this.enrollmentId = enrollmentId;
        this.alreadyEnrolled = alreadyEnrolled;
        this.paymentRequired = paymentRequired;
        this.code = code;
        this.message = message;
    }

    public static EnrollmentResult success(long enrollmentId,
                                           boolean alreadyEnrolled,
                                           boolean paymentRequired,
                                           String code,
                                           String message) {
        return new EnrollmentResult(true, null, enrollmentId, alreadyEnrolled, paymentRequired, code, message);
    }

    public static EnrollmentResult failure(String error) {
        return new EnrollmentResult(false, error, 0, false, false, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public long getEnrollmentId() {
        return enrollmentId;
    }

    public boolean isAlreadyEnrolled() {
        return alreadyEnrolled;
    }

    public boolean isPaymentRequired() {
        return paymentRequired;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
