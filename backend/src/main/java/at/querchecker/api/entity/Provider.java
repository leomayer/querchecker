package at.querchecker.api.entity;

public enum Provider {
    BRAVE("Brave"),
    GROQ("Groq"),
    OPENROUTER("OpenRouter"),
    ICECAT("Icecat"),
    GOOGLE_DISCOVERY("Google Discovery"),
    ;

    private final String displayName;

    Provider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
