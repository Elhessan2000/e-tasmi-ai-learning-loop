package model.service.analysis;

import model.entity.RecitationFindingRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation order for instructor findings. Does not merge or drop records.
 */
public final class FindingGroups {

    public static final List<FindingType> TYPES = List.of(
            FindingType.MISSING_WORD,
            FindingType.INCORRECT_WORD,
            FindingType.EXTRA_WORD,
            FindingType.PASSAGE_MISMATCH,
            FindingType.PRONUNCIATION_OBSERVATION,
            FindingType.OTHER
    );

    private FindingGroups() {
    }

    public static List<TypeGroup> group(List<RecitationFindingRecord> rows) {
        List<TypeGroup> groups = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return groups;
        }
        for (FindingType type : TYPES) {
            TypeGroup group = new TypeGroup(type);
            for (RecitationFindingRecord row : rows) {
                if (row == null || row.getFindingType() != type) {
                    continue;
                }
                String verseKey = row.getVerseKey() == null ? "" : row.getVerseKey().trim();
                AyahGroup ayah = group.ayah(verseKey);
                ayah.findings.add(row);
            }
            if (!group.ayahs.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    public static final class TypeGroup {
        private final FindingType type;
        private final List<AyahGroup> ayahs = new ArrayList<>();

        private TypeGroup(FindingType type) {
            this.type = type;
        }

        public FindingType getType() {
            return type;
        }

        public List<AyahGroup> getAyahs() {
            return ayahs;
        }

        public int getCount() {
            int count = 0;
            for (AyahGroup ayah : ayahs) {
                count += ayah.findings.size();
            }
            return count;
        }

        private AyahGroup ayah(String verseKey) {
            for (AyahGroup ayah : ayahs) {
                if (ayah.verseKey.equals(verseKey)) {
                    return ayah;
                }
            }
            AyahGroup created = new AyahGroup(verseKey);
            ayahs.add(created);
            return created;
        }
    }

    public static final class AyahGroup {
        private final String verseKey;
        private final List<RecitationFindingRecord> findings = new ArrayList<>();

        private AyahGroup(String verseKey) {
            this.verseKey = verseKey;
        }

        public String getVerseKey() {
            return verseKey;
        }

        public List<RecitationFindingRecord> getFindings() {
            return findings;
        }
    }
}
