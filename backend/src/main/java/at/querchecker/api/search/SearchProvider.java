package at.querchecker.api.search;

import at.querchecker.api.entity.Provider;

public enum SearchProvider {
    BRAVE,
    GOOGLE_DISCOVERY;

    public Provider toProvider() {
        return switch (this) {
            case BRAVE -> Provider.BRAVE;
            case GOOGLE_DISCOVERY -> Provider.GOOGLE_DISCOVERY;
        };
    }
}
