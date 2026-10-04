package model.service.quran;

/**
 * Challenge trusted-reference source. Implementations must never invent Qur'an text.
 */
public interface TrustedReferenceProvider {

    TrustedReferenceResult fetch(int surah, int ayahStart, int ayahEnd);
}
