package dev.devix7.ixskins;

import java.util.Locale;
import java.util.Objects;

public record SkinSource(Kind kind, String value) {
    public enum Kind {
        PLAYER,
        RAWURL
    }

    public SkinSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Skin value cannot be empty");
        }
    }

    public static SkinSource player(String username) {
        return new SkinSource(Kind.PLAYER, username);
    }

    public static SkinSource rawUrl(String url) {
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return new SkinSource(Kind.RAWURL, trimmed);
    }

    public String display() {
        return value;
    }

    public String cacheKey() {
        String normalized = kind == Kind.PLAYER ? value.toLowerCase(Locale.ROOT) : value;
        return kind.name() + ":" + normalized;
    }
}
