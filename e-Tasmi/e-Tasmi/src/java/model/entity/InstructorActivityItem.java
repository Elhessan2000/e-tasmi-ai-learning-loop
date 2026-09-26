package model.entity;

import java.time.Instant;

/**
 * Lightweight POJO for the instructor dashboard "Activity / Reminders" rail.
 *
 * Items are merged from multiple sources (next session reminder, recent recitations,
 * pending evaluation count, raw notifications) and ranked by urgency client-side.
 */
public class InstructorActivityItem {

    public enum Kind {
        SESSION_REMINDER,
        SESSION_LIVE,
        RECITATION_SUBMITTED,
        EVALUATIONS_PENDING,
        NOTIFICATION
    }

    private Kind kind;
    private String title;
    private String body;
    private String actionLabel;
    private String actionHref;
    private Instant occurredAt;
    private int rank;

    public InstructorActivityItem() {
    }

    public InstructorActivityItem(Kind kind, String title, String body, Instant occurredAt, int rank) {
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.occurredAt = occurredAt;
        this.rank = rank;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionHref() {
        return actionHref;
    }

    public void setActionHref(String actionHref) {
        this.actionHref = actionHref;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}
