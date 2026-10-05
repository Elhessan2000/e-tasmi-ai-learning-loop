package model.service;

import java.util.List;

/**
 * Learner-facing published history for the Progress page. Built only from evaluations with
 * {@code published_at} set; carries no analysis id, transcript, provider, or AI status.
 */
public final class StudentProgressHistory {

    public static final class Entry {
        private final long recitationId;
        private final String dateIso;
        private final String dateLabel;
        private final String sessionTitle;
        private final String passage;
        private final int attemptNumber;
        private final int score;
        private final int focusCount;

        public Entry(long recitationId, String dateIso, String dateLabel, String sessionTitle,
                     String passage, int attemptNumber, int score, int focusCount) {
            this.recitationId = recitationId;
            this.dateIso = dateIso;
            this.dateLabel = dateLabel;
            this.sessionTitle = sessionTitle;
            this.passage = passage;
            this.attemptNumber = attemptNumber;
            this.score = score;
            this.focusCount = focusCount;
        }

        public long getRecitationId() { return recitationId; }
        public String getDateIso() { return dateIso; }
        public String getDateLabel() { return dateLabel; }
        public String getSessionTitle() { return sessionTitle; }
        public String getPassage() { return passage; }
        public int getAttemptNumber() { return attemptNumber; }
        public int getScore() { return score; }
        public int getFocusCount() { return focusCount; }
    }

    /** Latest published result compared with an earlier published attempt on the same session. */
    public static final class Comparison {
        private final Entry latest;
        private final Entry previous;

        public Comparison(Entry latest, Entry previous) {
            this.latest = latest;
            this.previous = previous;
        }

        public Entry getLatest() { return latest; }
        /** Null when the latest result is the first published attempt for its session. */
        public Entry getPrevious() { return previous; }
        public boolean hasPrevious() { return previous != null; }
        public int getScoreDelta() { return previous == null ? 0 : latest.getScore() - previous.getScore(); }
        public int getFocusDelta() { return previous == null ? 0 : latest.getFocusCount() - previous.getFocusCount(); }
    }

    /** Verified focus of the latest published result; {@code primary} is null when nothing was confirmed. */
    public static final class LatestFocus {
        private final Entry source;
        private final VerifiedFocusItem primary;
        private final String primaryLocation;
        private final Integer primaryAyah;
        private final int itemCount;

        public LatestFocus(Entry source, VerifiedFocusItem primary, String primaryLocation, Integer primaryAyah,
                           int itemCount) {
            this.source = source;
            this.primary = primary;
            this.primaryLocation = primaryLocation;
            this.primaryAyah = primaryAyah;
            this.itemCount = itemCount;
        }

        public Entry getSource() { return source; }
        public VerifiedFocusItem getPrimary() { return primary; }
        /** Surah name when {@link #getPrimaryAyah()} is set, otherwise the passage label. */
        public String getPrimaryLocation() { return primaryLocation; }
        public Integer getPrimaryAyah() { return primaryAyah; }
        public int getItemCount() { return itemCount; }
    }

    private static final StudentProgressHistory EMPTY = new StudentProgressHistory(List.of(), 0, null, null);

    private final List<Entry> entries;
    private final int totalPublished;
    private final LatestFocus latestFocus;
    private final Comparison comparison;

    public StudentProgressHistory(List<Entry> entries, int totalPublished, LatestFocus latestFocus,
                                  Comparison comparison) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.totalPublished = Math.max(totalPublished, this.entries.size());
        this.latestFocus = latestFocus;
        this.comparison = comparison;
    }

    public static StudentProgressHistory empty() {
        return EMPTY;
    }

    public List<Entry> getEntries() { return entries; }
    public int getTotalPublished() { return totalPublished; }
    public boolean isEmpty() { return entries.isEmpty(); }
    public LatestFocus getLatestFocus() { return latestFocus; }
    public Comparison getComparison() { return comparison; }
}
