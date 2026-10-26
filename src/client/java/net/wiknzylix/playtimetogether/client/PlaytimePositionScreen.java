package net.wiknzylix.playtimetogether.client;

import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** A local editor for the HUD position and its maximum dimensions. */
public final class PlaytimePositionScreen extends Screen {
    private static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;
    private static final int EDGE_REACH = 4;
    private final Screen parent;
    private boolean moving;
    private int resizeEdges;
    private double grabX;
    private double grabY;
    private double startMouseX;
    private double startMouseY;
    private ResizeFrame startFrame;

    public PlaytimePositionScreen(Screen parent) {
        super(Text.translatable("playtime_together.position.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int buttonWidth = Math.max(80, Math.min(150, (width - 24) / 2));
        addDrawableChild(ButtonWidget.builder(Text.translatable("playtime_together.position.reset"), button -> {
            var options = PlaytimeClient.config();
            options.positionX = 1.0;
            options.positionY = 1.0;
            options.changed();
        }).dimensions(width / 2 - buttonWidth - 3, height - 26, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
            .dimensions(width / 2 + 3, height - 26, buttonWidth, 20).build());
    }

    private List<PlaytimeClient.DisplayEntry> entries() {
        return PlaytimePreviewScreen.previewEntries();
    }

    private boolean paused() {
        return PlaytimeClient.snapshot() != null && PlaytimeClient.snapshot().paused();
    }

    private PlaytimeHud.PanelBounds panel() {
        return PlaytimeHud.bounds(width, height, PlaytimeClient.config(), entries(), paused());
    }

    /** Visible maximum-height frame; the filled HUD continues to shrink to its content. */
    public static ResizeFrame resizeFrame(int screenWidth, int screenHeight, PlaytimeHud.PanelBounds panel) {
        int frameHeight = Math.min(panel.maximumHeight(), Math.max(1, screenHeight));
        int contentTravel = screenHeight - panel.height();
        double verticalAnchor = contentTravel <= 0 ? 0.0 : panel.y() / (double) contentTravel;
        int frameY = (int) Math.round(Math.max(0, screenHeight - frameHeight) * Math.clamp(verticalAnchor, 0.0, 1.0));
        return new ResizeFrame(panel.x(), frameY, panel.width(),
            Math.max(1, frameHeight));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, client.world == null ? 0xF0101010 : 0x28000000);
        var bounds = panel();
        PlaytimeHud.drawPanel(context, bounds.x(), bounds.y(), bounds.width(), bounds.maximumHeight(),
            PlaytimeClient.config(), entries(), paused());
        var frame = resizeFrame(width, height, bounds);
        int hoverEdges = edgesAt(frame, mouseX, mouseY);
        int border = moving || resizeEdges != 0 || hoverEdges != 0 ? 0xFFD0D0D0 : 0xFF888888;
        context.enableScissor(0, 0, width, height);
        dashedFrame(context, frame.x() + 1, frame.y() + 1, frame.width(), frame.height(), 0x99000000);
        dashedFrame(context, frame.x(), frame.y(), frame.width(), frame.height(), border);
        int[] handleX = { frame.x(), frame.x() + frame.width() / 2, frame.x() + frame.width() - 1 };
        int[] handleY = { frame.y(), frame.y() + frame.height() / 2, frame.y() + frame.height() - 1 };
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                if (column != 1 || row != 1) context.fill(handleX[column] - 1, handleY[row] - 1,
                    handleX[column] + 2, handleY[row] + 2, border);
            }
        }
        context.disableScissor();
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFE0E0E0);
        String help = Text.translatable("playtime_together.position.help").getString();
        float helpScale = Math.min(0.7F, (width - 12F) / Math.max(1, textRenderer.getWidth(help)));
        PlaytimeHud.drawScaled(context, textRenderer, help,
            Math.round((width - textRenderer.getWidth(help) * helpScale) / 2F), 22, helpScale, 0xFFB0B0B0);
        var options = PlaytimeClient.config();
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("playtime_together.position.coordinates",
            Math.round(options.positionX * 100), Math.round(options.positionY * 100)), width / 2, height - 49, 0xFFBBBBBB);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("playtime_together.position.dimensions",
            options.width, options.height), width / 2, height - 39, 0xFFAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        var bounds = panel();
        var frame = resizeFrame(width, height, bounds);
        int edges = edgesAt(frame, click.x(), click.y());
        // Edge handles win over moving the content, including each two-axis corner.
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && edges != 0) {
            resizeEdges = edges;
            moving = false;
            startMouseX = click.x();
            startMouseY = click.y();
            startFrame = frame;
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.x() >= bounds.x() && click.y() >= bounds.y()
            && click.x() < bounds.x() + bounds.width() && click.y() < bounds.y() + bounds.height()) {
            moving = true;
            resizeEdges = 0;
            grabX = click.x() - bounds.x();
            grabY = click.y() - bounds.y();
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && (moving || resizeEdges != 0)) {
            if (resizeEdges != 0) resizeTo(click.x(), click.y());
            else moveTo(click.x() - grabX, click.y() - grabY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(Click click) {
        if ((moving || resizeEdges != 0) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            moving = false;
            resizeEdges = 0;
            return true;
        }
        return super.mouseReleased(click);
    }

    private static int edgesAt(ResizeFrame frame, double x, double y) {
        int right = frame.x() + frame.width() - 1;
        int bottom = frame.y() + frame.height() - 1;
        if (x < frame.x() - EDGE_REACH || x > right + EDGE_REACH
            || y < frame.y() - EDGE_REACH || y > bottom + EDGE_REACH) return 0;
        int result = 0;
        if (Math.abs(x - frame.x()) <= EDGE_REACH) result |= LEFT;
        else if (Math.abs(x - right) <= EDGE_REACH) result |= RIGHT;
        if (Math.abs(y - frame.y()) <= EDGE_REACH) result |= TOP;
        else if (Math.abs(y - bottom) <= EDGE_REACH) result |= BOTTOM;
        return result;
    }

    private void resizeTo(double mouseX, double mouseY) {
        // Every drag is based on the original rectangle, never on the changing content height.
        double dx = mouseX - startMouseX;
        double dy = mouseY - startMouseY;
        int minimumWidth = Math.min(60, width);
        int minimumHeight = Math.min(40, height);
        int left = startFrame.x(), top = startFrame.y();
        int right = left + startFrame.width(), bottom = top + startFrame.height();
        if ((resizeEdges & LEFT) != 0) left = (int) Math.round(Math.clamp(left + dx,
            Math.max(0, right - 1200), right - minimumWidth));
        if ((resizeEdges & RIGHT) != 0) right = (int) Math.round(Math.clamp(right + dx,
            left + minimumWidth, Math.min(width, left + 1200)));
        if ((resizeEdges & TOP) != 0) top = (int) Math.round(Math.clamp(top + dy,
            Math.max(0, bottom - 1200), bottom - minimumHeight));
        if ((resizeEdges & BOTTOM) != 0) bottom = (int) Math.round(Math.clamp(bottom + dy,
            top + minimumHeight, Math.min(height, top + 1200)));
        var options = PlaytimeClient.config();
        if ((resizeEdges & (LEFT | RIGHT)) != 0) options.width = Math.clamp(right - left, 60, 1200);
        if ((resizeEdges & (TOP | BOTTOM)) != 0) options.height = Math.clamp(bottom - top, 40, 1200);
        // Resizing anchors the full maximum-height frame. Moving separately uses actual content height.
        int horizontalTravel = width - Math.min(width, options.width);
        int verticalTravel = height - Math.min(height, options.height);
        if ((resizeEdges & (LEFT | RIGHT)) != 0 && horizontalTravel > 0)
            options.positionX = Math.clamp(left / (double) horizontalTravel, 0.0, 1.0);
        if ((resizeEdges & (TOP | BOTTOM)) != 0 && verticalTravel > 0)
            options.positionY = Math.clamp(top / (double) verticalTravel, 0.0, 1.0);
        options.changed();
    }

    private static void dashedFrame(DrawContext context, int x, int y, int width, int height, int color) {
        for (int offset = 0; offset < width; offset += 7) {
            int end = Math.min(width, offset + 4);
            context.fill(x + offset, y, x + end, y + 1, color);
            context.fill(x + offset, y + height - 1, x + end, y + height, color);
        }
        for (int offset = 0; offset < height; offset += 7) {
            int end = Math.min(height, offset + 4);
            context.fill(x, y + offset, x + 1, y + end, color);
            context.fill(x + width - 1, y + offset, x + width, y + end, color);
        }
    }

    private void moveTo(double x, double y) {
        var bounds = panel();
        var options = PlaytimeClient.config();
        int travelX = width - bounds.width();
        int travelY = height - bounds.height();
        // When the panel currently spans the whole axis, retain the user's anchor for later resizes.
        if (travelX > 0) options.positionX = Math.clamp(x / travelX, 0.0, 1.0);
        if (travelY > 0) options.positionY = Math.clamp(y / travelY, 0.0, 1.0);
        options.changed();
    }

    @Override public boolean keyPressed(KeyInput input) {
        int step = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        var bounds = panel();
        switch (input.key()) {
            case GLFW.GLFW_KEY_LEFT -> moveTo(bounds.x() - step, bounds.y());
            case GLFW.GLFW_KEY_RIGHT -> moveTo(bounds.x() + step, bounds.y());
            case GLFW.GLFW_KEY_UP -> moveTo(bounds.x(), bounds.y() - step);
            case GLFW.GLFW_KEY_DOWN -> moveTo(bounds.x(), bounds.y() + step);
            default -> { return super.keyPressed(input); }
        }
        return true;
    }

    @Override public void close() { PlaytimeClient.config().save(); client.setScreen(parent); }
    @Override public void removed() { moving = false; resizeEdges = 0; PlaytimeClient.config().save(); }
    @Override public boolean shouldPause() { return false; }

    public record ResizeFrame(int x, int y, int width, int height) { }
}
