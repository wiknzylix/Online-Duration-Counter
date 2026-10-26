package net.wiknzylix.playtimetogether.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable, precompiled OR matcher for player display rules. No Minecraft dependency. */
public final class PlayerRuleMatcher {
    private final List<String> keywords;
    private final Pattern regex;
    private final String regexError;
    private final boolean numericIds;
    private final boolean fakePlayers;

    public PlayerRuleMatcher(String keywords, String regex, boolean numericIds, boolean fakePlayers) {
        this.keywords = Arrays.stream((keywords == null ? "" : keywords).split("[,，;；\\r\\n]+"))
            .map(String::strip).filter(keyword -> !keyword.isEmpty())
            .map(keyword -> keyword.toLowerCase(Locale.ROOT)).distinct().toList();
        Pattern compiled = null;
        String error = null;
        if (regex != null && !regex.isBlank()) {
            try { compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE); }
            catch (PatternSyntaxException invalid) { error = invalid.getDescription(); }
        }
        this.regex = compiled;
        this.regexError = error;
        this.numericIds = numericIds;
        this.fakePlayers = fakePlayers;
    }

    /** The fake flag is server-provided metadata, never inferred from a name or UUID. */
    public boolean matches(String id, boolean fake) {
        if (fakePlayers && fake) return true;
        String name = id == null ? "" : id;
        if (numericIds && asciiDigitsOnly(name)) return true;
        if (!keywords.isEmpty()) {
            String folded = name.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) if (folded.contains(keyword)) return true;
        }
        return regex != null && regex.matcher(name).find();
    }

    public String regexError() { return regexError; }

    private static boolean asciiDigitsOnly(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }
}
