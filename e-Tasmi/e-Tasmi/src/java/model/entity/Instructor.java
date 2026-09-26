package model.entity;

public class Instructor {
    private long instructorId;
    private long userId;
    private String title;
    private String qualification;
    private String qualificationFile;
    private String bio;
    private InstructorVerificationStatus verificationStatus;
    private String zoomEmail;
    private String paymentAccountHolder;
    private String paymentMethodName;
    private String paymentAccountDetails;
    private String paymentQrUrl;
    private String paymentInstructions;

    public long getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(long instructorId) {
        this.instructorId = instructorId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public String getQualificationFile() {
        return qualificationFile;
    }

    public void setQualificationFile(String qualificationFile) {
        this.qualificationFile = qualificationFile;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public InstructorVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(InstructorVerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getZoomEmail() {
        return zoomEmail;
    }

    public void setZoomEmail(String zoomEmail) {
        this.zoomEmail = zoomEmail;
    }

    public String getPaymentAccountHolder() {
        return paymentAccountHolder;
    }

    public void setPaymentAccountHolder(String paymentAccountHolder) {
        this.paymentAccountHolder = paymentAccountHolder;
    }

    public String getPaymentMethodName() {
        return paymentMethodName;
    }

    public void setPaymentMethodName(String paymentMethodName) {
        this.paymentMethodName = paymentMethodName;
    }

    public String getPaymentAccountDetails() {
        return paymentAccountDetails;
    }

    public void setPaymentAccountDetails(String paymentAccountDetails) {
        this.paymentAccountDetails = paymentAccountDetails;
    }

    public String getPaymentQrUrl() {
        return paymentQrUrl;
    }

    public void setPaymentQrUrl(String paymentQrUrl) {
        this.paymentQrUrl = paymentQrUrl;
    }

    public String getPaymentInstructions() {
        return paymentInstructions;
    }

    public void setPaymentInstructions(String paymentInstructions) {
        this.paymentInstructions = paymentInstructions;
    }
}
