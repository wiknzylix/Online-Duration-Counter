package net.wiknzylix.playtimetogether.client;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** A scaled preview is available even from the main menu or on small GUI sizes. */
public final class PlaytimePreviewScreen extends Screen {
    private final Screen parent;
    private static final List<PlaytimeClient.DisplayEntry> PREVIEW_ENTRIES = List.of(
        new PlaytimeClient.DisplayEntry(new UUID(0, 1), "wiknzylix", 184_320_000L, true),
        new PlaytimeClient.DisplayEntry(new UUID(0, 2), "Alex", 89_160_000L, true),
        new PlaytimeClient.DisplayEntry(new UUID(0, 3), "Steve", 254_580_000L, false),
        new PlaytimeClient.DisplayEntry(new UUID(0, 4), "Player", 45_660_000L, false));

    public PlaytimePreviewScreen(Screen parent) {
        super(Text.translatable("playtime_together.config.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
            .dimensions(width / 2 - 80, height - 28, 160, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 13, 0xFFF0F0F0);
        ClientConfig options = PlaytimeClient.config();
        int previewHeight = PlaytimeHud.measureHeight(options.width, options.height, options, previewEntries(), false);
        float availableWidth = Math.max(1F, width - 28F);
        float availableHeight = Math.max(1F, height - 76F);
        float fit = Math.min(1F, Math.min(availableWidth / Math.max(1, options.width), availableHeight / previewHeight));
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((width - options.width * fit) / 2F, 34F + (availableHeight - previewHeight * fit) / 2F);
        matrices.scale(fit, fit);
        PlaytimeHud.drawPanel(context, 0, 0, options.width, options.height, options, previewEntries(), false);
        matrices.popMatrix();
        super.render(context, mouseX, mouseY, delta);
    }

    public static List<PlaytimeClient.DisplayEntry> previewEntries() {
        // A filtered world can legitimately be empty. Demo players are only for menus without world data.
        return PlaytimeClient.snapshot() == null ? PREVIEW_ENTRIES : PlaytimeClient.entries(true);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
