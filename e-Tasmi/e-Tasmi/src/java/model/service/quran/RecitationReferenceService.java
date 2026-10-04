package model.service.quran;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application entry point for the Challenge trusted-reference layer.
 * Successful fetches are cached in memory; Qur'an text is immutable so there is no TTL.
 * Failed fetches use a short negative cache so a Quranpedia outage does not stampede the upstream.
 */
public final class RecitationReferenceService {

    private static final RecitationReferenceService INSTANCE = new RecitationReferenceService();
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long NEGATIVE_TTL_MS = 60_000L;

    private final TrustedReferenceProvider provider;
    private final Map<String, TrustedReference> cache = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TrustedReference> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };
    private final Map<String, NegativeCacheEntry> negativeCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, NegativeCacheEntry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    RecitationReferenceService(TrustedReferenceProvider provider) {
        this.provider = provider;
    }

    private RecitationReferenceService() {
        this(new model.service.quranpedia.QuranpediaReferenceProvider());
    }

    public static RecitationReferenceService getInstance() {
        return INSTANCE;
    }

    public TrustedReferenceResult fetch(int surah, int ayahStart, int ayahEnd) {
        String rangeError = QuranPassageRange.validate(surah, ayahStart, ayahEnd);
        if (rangeError != null) {
            return TrustedReferenceResult.unavailable(rangeError);
        }
        String key = cacheKey(surah, ayahStart, ayahEnd);
        synchronized (cache) {
            TrustedReference cached = cache.get(key);
            if (cached != null) {
                return TrustedReferenceResult.ok(cached);
            }
            NegativeCacheEntry negative = negativeCache.get(key);
            if (negative != null) {
                if (negative.expiresAtMs > System.currentTimeMillis()) {
                    return TrustedReferenceResult.unavailable(negative.reason);
                }
                negativeCache.remove(key);
            }
        }
        TrustedReferenceResult result = provider.fetch(surah, ayahStart, ayahEnd);
        if (result.isOk()) {
            synchronized (cache) {
                cache.put(key, result.getReference());
                negativeCache.remove(key);
            }
        } else {
            synchronized (cache) {
                negativeCache.put(key, new NegativeCacheEntry(
                        result.getReason(),
                        System.currentTimeMillis() + NEGATIVE_TTL_MS));
            }
        }
        return result;
    }

    static String cacheKey(int surah, int ayahStart, int ayahEnd) {
        return surah + ":" + ayahStart + "-" + ayahEnd;
    }

    private static final class NegativeCacheEntry {
        final String reason;
        final long expiresAtMs;

        NegativeCacheEntry(String reason, long expiresAtMs) {
            this.reason = reason == null || reason.isBlank()
                    ? "Trusted Qur'an reference is unavailable."
                    : reason;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
