package model.service;

import model.entity.TasmiSession;

public class LiveSessionAccess {
    private final boolean allowed;
    private final String error;
    private final TasmiSession tasmiSession;
    private final String displayName;
    private final String email;
    private final boolean host;
    private final String fallbackUrl;

    private LiveSessionAccess(boolean allowed,
                              String error,
                              TasmiSession tasmiSession,
                              String displayName,
                              String email,
                              boolean host,
                              String fallbackUrl) {
        this.allowed = allowed;
        this.error = error;
        this.tasmiSession = tasmiSession;
        this.displayName = displayName;
        this.email = email;
        this.host = host;
        this.fallbackUrl = fallbackUrl;
    }

    public static LiveSessionAccess allowed(TasmiSession tasmiSession,
                                            String displayName,
                                            String email,
                                            boolean host,
                                            String fallbackUrl) {
        return new LiveSessionAccess(true, null, tasmiSession, displayName, email, host, fallbackUrl);
    }

    public static LiveSessionAccess denied(String error) {
        return new LiveSessionAccess(false, error, null, null, null, false, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getError() {
        return error;
    }

    public TasmiSession getTasmiSession() {
        return tasmiSession;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isHost() {
        return host;
    }

    public String getFallbackUrl() {
        return fallbackUrl;
    }
}
