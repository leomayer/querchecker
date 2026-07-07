package at.querchecker.auth;

public record QuerCheckerPrincipal(Role role, Long accessKeyId) {

    public static QuerCheckerPrincipal withoutKey(Role role) {
        return new QuerCheckerPrincipal(role, null);
    }

    public static QuerCheckerPrincipal withKey(Role role, Long accessKeyId) {
        return new QuerCheckerPrincipal(role, accessKeyId);
    }

    public boolean hasKey() {
        return accessKeyId != null;
    }
}
