package model.service;

import model.entity.StudentLevel;
import model.entity.UserRole;

public class RegistrationRequest {
    private String fullName;
    private String email;
    private String phone;
    private char[] password;
    private UserRole role;
    private StudentLevel studentLevel;
    private String instructorBio;
    private String qualificationFilePath;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public StudentLevel getStudentLevel() {
        return studentLevel;
    }

    public void setStudentLevel(StudentLevel studentLevel) {
        this.studentLevel = studentLevel;
    }

    public String getInstructorBio() {
        return instructorBio;
    }

    public void setInstructorBio(String instructorBio) {
        this.instructorBio = instructorBio;
    }

    public String getQualificationFilePath() {
        return qualificationFilePath;
    }

    public void setQualificationFilePath(String qualificationFilePath) {
        this.qualificationFilePath = qualificationFilePath;
    }
}
