package model.service;

public class ZoomMeetingInfo {
    private final long meetingId;
    private final String joinUrl;
    private final String startUrl;
    private final String password;

    public ZoomMeetingInfo(long meetingId, String joinUrl, String startUrl, String password) {
        this.meetingId = meetingId;
        this.joinUrl = joinUrl;
        this.startUrl = startUrl;
        this.password = password;
    }

    public long getMeetingId() {
        return meetingId;
    }

    public String getJoinUrl() {
        return joinUrl;
    }

    public String getStartUrl() {
        return startUrl;
    }

    public String getPassword() {
        return password;
    }
}
