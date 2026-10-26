package net.wiknzylix.playtimetogether.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

/** A local HUD. This class never creates or modifies a world scoreboard objective. */
public final class PlaytimeHud {
    public static final int ONLINE = 0xFF9DB79B;
    public static final int OFFLINE = 0xFF909090;
    private static final int MUTED = 0xFFA2A2A2;
    private static final Text SUBTITLE = Text.literal("Have Fun").styled(style -> style.withItalic(true));
    private static PanelLayout cachedLayout;

    private PlaytimeHud() { }

    static void clearCache() { cachedLayout = null; }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientConfig options = PlaytimeClient.config();
        if (client.world == null || client.player == null || client.options.hudHidden || !options.enabled
            || PlaytimeClient.snapshot() == null || client.currentScreen instanceof PlaytimeConfigScreen
            || client.currentScreen instanceof PlaytimePreviewScreen || client.currentScreen instanceof PlaytimePositionScreen
            || vanillaSidebarVisible(client)) return;

        int availableWidth = context.getScaledWindowWidth();
        int availableHeight = context.getScaledWindowHeight();
        List<PlaytimeClient.DisplayEntry> entries = PlaytimeClient.entries(true);
        boolean paused = PlaytimeClient.snapshot().paused();
        PanelBounds bounds = bounds(availableWidth, availableHeight, options, entries, paused);
        drawPanel(context, bounds.x, bounds.y, bounds.width, bounds.maximumHeight, options, entries, paused);
    }

    public static boolean vanillaSidebarVisible(MinecraftClient client) {
        if (client.world == null || client.player == null) return false;
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(client.player.getName().getString());
        if (team != null) {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (slot != null) objective = scoreboard.getObjectiveForSlot(slot);
        }
        if (objective == null) objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        return objective != null && scoreboard.getScoreboardEntries(objective).stream().anyMatch(entry -> !entry.hidden());
    }

    public static int measureHeight(int panelWidth, int maximumHeight, ClientConfig options,
                                     List<PlaytimeClient.DisplayEntry> entries, boolean paused) {
        return layout(MinecraftClient.getInstance().textRenderer, Math.max(1, panelWidth), Math.max(1, maximumHeight), options, entries, paused).actualHeight;
    }

    /** Shared geometry for the HUD and the local drag editor; positions span the available movement range. */
    public static PanelBounds bounds(int screenWidth, int screenHeight, ClientConfig options,
                                      List<PlaytimeClient.DisplayEntry> entries, boolean paused) {
        int panelWidth = Math.clamp(options.width, 1, Math.max(1, screenWidth));
        int maximumHeight = Math.clamp(options.height, 1, Math.max(1, screenHeight));
        int height = measureHeight(panelWidth, maximumHeight, options, entries, paused);
        double horizontal = Double.isFinite(options.positionX) ? Math.clamp(options.positionX, 0.0, 1.0) : 1.0;
        double vertical = Double.isFinite(options.positionY) ? Math.clamp(options.positionY, 0.0, 1.0) : 1.0;
        return new PanelBounds((int) Math.round(Math.max(0, screenWidth - panelWidth) * horizontal),
            (int) Math.round(Math.max(0, screenHeight - height) * vertical), panelWidth, maximumHeight, height);
    }

    public static void drawPanel(DrawContext context, int x, int y, int panelWidth, int maximumHeight,
                                 ClientConfig options, List<PlaytimeClient.DisplayEntry> entries, boolean paused) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        panelWidth = Math.max(1, panelWidth);
        maximumHeight = Math.max(1, maximumHeight);
        PanelLayout layout = layout(renderer, panelWidth, maximumHeight, options, entries, paused);
        int panelHeight = layout.actualHeight;
        int headingHeight = Math.min(panelHeight, layout.headingHeight);
        int backgroundAlpha = Math.round(options.backgroundOpacity * 255F / 100F) << 24;
        // Every drawing operation stays inside the configured rectangle, including unusually large fonts.
        context.enableScissor(x, y, x + panelWidth, y + panelHeight);
        context.fill(x, y + headingHeight, x + panelWidth, y + panelHeight, backgroundAlpha | 0x111111);
        context.fill(x, y, x + panelWidth, y + headingHeight, backgroundAlpha | 0x1A1A1A);
        drawScaledCentered(context, renderer, layout.title,
            x + panelWidth / 2, y + 1, layout.titleScale, 0xFF000000 | (options.titleColor & 0xFFFFFF));
        drawScaledCentered(context, renderer, SUBTITLE, x + panelWidth / 2,
            y + layout.subtitleY, layout.subtitleScale, 0xFF000000 | (options.subtitleColor & 0xFFFFFF));

        int top = y + layout.headingHeight + 1;
        int bottom = y + panelHeight - 1;
        if (top < bottom) {
            context.enableScissor(x, top, x + panelWidth, bottom);
            for (DrawRow row : layout.rows) {
                drawScaled(context, renderer, row.left, x + layout.padding, y + row.y + row.leftOffset, row.leftScale, row.color);
                if (row.right != null) drawScaled(context, renderer, row.right,
                    x + row.rightX, y + row.y + row.rightOffset, row.rightScale, row.color);
            }
            if (!layout.empty.isEmpty()) drawScaled(context, renderer, layout.empty,
                x + layout.padding, top, layout.emptyScale, MUTED);
            context.disableScissor();
        }
        context.disableScissor();
    }

    private static PanelLayout layout(TextRenderer renderer, int panelWidth, int panelHeight, ClientConfig options,
                                       List<PlaytimeClient.DisplayEntry> entries, boolean paused) {
        Object language = Language.getInstance();
        if (cachedLayout != null && cachedLayout.source == entries && cachedLayout.width == panelWidth
            && cachedLayout.height == panelHeight && cachedLayout.fontScale == options.fontScale
            && cachedLayout.format == options.timeFormat && cachedLayout.paused == paused && cachedLayout.language == language
            && cachedLayout.onlineColor == options.onlineColor && cachedLayout.offlineColor == options.offlineColor) return cachedLayout;

        float scale = (float) Math.clamp(options.fontScale, 0.35, 3.0);
        float titleScale = Math.max(0.55F, scale * 1.35F);
        float subtitleScale = scale * 0.75F;
        int line = Math.max(1, (int) Math.ceil(renderer.fontHeight * scale) + 1);
        int subtitleY = 1 + (int) Math.ceil(renderer.fontHeight * titleScale);
        int headingHeight = subtitleY + (int) Math.ceil(renderer.fontHeight * subtitleScale) + 1;
        int top = headingHeight + 1;
        int bottom = panelHeight - 2;
        int padding = Math.min(3, Math.max(1, panelWidth / 20));
        int innerWidth = Math.max(1, panelWidth - padding * 2);
        int slots = Math.max(0, (bottom - top) / line);
        List<PlaytimeClient.DisplayEntry> online = entries.stream().filter(PlaytimeClient.DisplayEntry::online).toList();
        List<PlaytimeClient.DisplayEntry> offline = entries.stream().filter(entry -> !entry.online()).toList();
        List<Row> rows = new ArrayList<>();
        int remaining = slots;
        int shownOnline = 0;
        int shownOffline = 0;
        int onlineColor = 0xFF000000 | (options.onlineColor & 0xFFFFFF);
        int offlineColor = 0xFF000000 | (options.offlineColor & 0xFFFFFF);
        // Online players always consume the available rows first. Offline records use the remainder.
        if (!online.isEmpty() && remaining > 0) {
            rows.add(Row.heading(Text.translatable("playtime_together.hud.online", online.size()).getString(), onlineColor));
            remaining--;
            shownOnline = Math.min(online.size(), remaining);
            for (int i = 0; i < shownOnline; i++) rows.add(Row.player(i + 1, online.get(i), onlineColor));
            remaining -= shownOnline;
        }
        if (!offline.isEmpty() && remaining > 0) {
            rows.add(Row.heading(Text.translatable("playtime_together.hud.offline", offline.size()).getString(), offlineColor));
            remaining--;
            shownOffline = Math.min(offline.size(), remaining);
            for (int i = 0; i < shownOffline; i++) rows.add(Row.player(i + 1, offline.get(i), offlineColor));
        }
        List<DrawRow> prepared = new ArrayList<>(rows.size());
        int rowY = top;
        for (Row row : rows) {
            if (row.entry == null) {
                prepared.add(new DrawRow(fit(renderer, row.heading, (int) (innerWidth / scale)), null,
                    0, row.color, rowY, scale, scale, 0, 0));
            } else {
                String time = options.timeFormat.format(row.entry.totalMillis());
                int gap = Math.max(2, (int) Math.ceil(scale) + 1);
                float reservedName = Math.min(24F * Math.min(scale, 1F), innerWidth * 0.25F);
                float timeScale = Math.min(scale, Math.max(1F, innerWidth - reservedName - gap) / Math.max(1, renderer.getWidth(time)));
                int timeWidth = (int) Math.ceil(renderer.getWidth(time) * timeScale);
                int timeX = panelWidth - padding - timeWidth;
                String rank = row.rank + ". ";
                int availableName = Math.max(0, timeX - padding - gap);
                float nameScale = Math.min(scale, availableName / (float) Math.max(1, renderer.getWidth(rank + "…")));
                String name = nameScale <= 0 ? "" : fit(renderer, rank + row.entry.name(), (int) (availableName / nameScale));
                int leftOffset = Math.max(0, Math.round(renderer.fontHeight * (scale - nameScale) / 2));
                int rightOffset = Math.max(0, Math.round(renderer.fontHeight * (scale - timeScale) / 2));
                prepared.add(new DrawRow(name, time, timeX, row.color, rowY, Math.max(0.01F, nameScale), timeScale, leftOffset, rightOffset));
            }
            rowY += line;
        }
        int contentRows = Math.max(entries.isEmpty() ? 1 : 0, prepared.size());
        int actualHeight = Math.min(panelHeight, top + contentRows * line + 2);
        String empty = entries.isEmpty() ? Text.translatable("playtime_together.hud.empty").getString() : "";
        float emptyScale = Math.min(scale, innerWidth / (float) Math.max(1, renderer.getWidth(empty)));
        cachedLayout = new PanelLayout(entries, panelWidth, panelHeight, options.fontScale, options.timeFormat, language, paused,
            options.onlineColor, options.offlineColor,
            headingHeight, actualHeight, padding, titleScale, subtitleScale, subtitleY,
            List.copyOf(prepared), Text.translatable("playtime_together.hud.title").getString(), empty, emptyScale);
        return cachedLayout;
    }

    private static String fit(TextRenderer renderer, String text, int width) {
        if (renderer.getWidth(text) <= width) return text;
        String ellipsis = "…";
        return width < renderer.getWidth(ellipsis) ? "" : renderer.trimToWidth(text, width - renderer.getWidth(ellipsis)) + ellipsis;
    }

    public static void drawScaled(DrawContext context, TextRenderer renderer, String text, int x, int y, float scale, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawTextWithShadow(renderer, text, 0, 0, color);
        matrices.popMatrix();
    }

    public static void drawScaled(DrawContext context, TextRenderer renderer, Text text, int x, int y, float scale, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawTextWithShadow(renderer, text, 0, 0, color);
        matrices.popMatrix();
    }

    private static void drawScaledCentered(DrawContext context, TextRenderer renderer, String text, int centerX, int y, float scale, int color) {
        drawScaled(context, renderer, text, centerX - Math.round(renderer.getWidth(text) * scale / 2), y, scale, color);
    }

    private static void drawScaledCentered(DrawContext context, TextRenderer renderer, Text text, int centerX, int y, float scale, int color) {
        drawScaled(context, renderer, text, centerX - Math.round(renderer.getWidth(text) * scale / 2), y, scale, color);
    }

    private record Row(String heading, PlaytimeClient.DisplayEntry entry, int rank, int color) {
        static Row heading(String text, int color) { return new Row(text, null, 0, color); }
        static Row player(int rank, PlaytimeClient.DisplayEntry entry, int color) { return new Row(null, entry, rank, color); }
    }
    public record PanelBounds(int x, int y, int width, int maximumHeight, int height) { }
    private record DrawRow(String left, String right, int rightX, int color, int y,
                            float leftScale, float rightScale, int leftOffset, int rightOffset) { }
    private record PanelLayout(List<PlaytimeClient.DisplayEntry> source, int width, int height, double fontScale,
                               DurationFormat format, Object language, boolean paused, int onlineColor, int offlineColor,
                               int headingHeight, int actualHeight,
                               int padding, float titleScale, float subtitleScale, int subtitleY,
                               List<DrawRow> rows, String title, String empty, float emptyScale) { }
}
