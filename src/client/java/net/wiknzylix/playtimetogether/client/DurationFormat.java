package net.wiknzylix.playtimetogether.client;

import net.minecraft.text.Text;

/** Display formats deliberately leave the server's stored precision unchanged. */
public enum DurationFormat {
    HOURS_MINUTES("hours_minutes"),
    HOURS_MINUTES_SECONDS("hours_minutes_seconds"),
    HOURS("hours");

    private final String key;

    DurationFormat(String key) { this.key = key; }

    public Text label() { return Text.translatable("playtime_together.format." + key); }

    public DurationFormat next() { return values()[(ordinal() + 1) % values().length]; }

    public String format(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        return switch (this) {
            case HOURS_MINUTES -> Text.translatable("playtime_together.duration.hm", hours, minutes % 60L).getString();
            case HOURS_MINUTES_SECONDS -> Text.translatable("playtime_together.duration.hms", hours, minutes % 60L, seconds % 60L).getString();
            case HOURS -> Text.translatable("playtime_together.duration.h", hours).getString();
        };
    }
}
