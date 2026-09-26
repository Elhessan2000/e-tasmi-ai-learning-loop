package model.service;

public class PaymentResult {
    private final boolean success;
    private final String error;
    private final String code;
    private final String message;

    private PaymentResult(boolean success, String error, String code, String message) {
        this.success = success;
        this.error = error;
        this.code = code;
        this.message = message;
    }

    public static PaymentResult success() {
        return new PaymentResult(true, null, null, null);
    }

    public static PaymentResult success(String code, String message) {
        return new PaymentResult(true, null, code, message);
    }

    public static PaymentResult failure(String error) {
        return new PaymentResult(false, error, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
