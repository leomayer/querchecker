package at.querchecker.auth;

/**
 * Ebene-2-Kontingent des Keys für den heutigen Tag erschöpft.
 * Wird vom {@link AccessKeyUsageService} vor der Provider-Pipeline geworfen und
 * vom Caller auf {@code LookupStatus.KEY_QUOTA_EXCEEDED} gemappt (separat vom
 * Ebene-1 Provider-Kontingent {@code QUOTA_EXCEEDED} — andere Ursache, andere Reset-Frist).
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(Long accessKeyId) {
        super("Tageskontingent erschöpft für accessKeyId=" + accessKeyId);
    }
}
