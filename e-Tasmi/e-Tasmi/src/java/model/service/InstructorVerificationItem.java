package model.service;

import model.entity.Instructor;
import model.entity.User;

public class InstructorVerificationItem {
    private final Instructor instructor;
    private final User user;
    private final boolean qualificationFileAvailable;

    public InstructorVerificationItem(Instructor instructor, User user) {
        this(instructor, user, false);
    }

    public InstructorVerificationItem(Instructor instructor, User user, boolean qualificationFileAvailable) {
        this.instructor = instructor;
        this.user = user;
        this.qualificationFileAvailable = qualificationFileAvailable;
    }

    public Instructor getInstructor() {
        return instructor;
    }

    public User getUser() {
        return user;
    }

    public boolean isQualificationFileAvailable() {
        return qualificationFileAvailable;
    }
}
