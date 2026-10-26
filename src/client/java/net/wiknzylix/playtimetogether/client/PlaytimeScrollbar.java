package net.wiknzylix.playtimetogether.client;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

/** Shared pointer handling for a narrow vertical scrollbar, in pixels or list rows. */
public final class PlaytimeScrollbar {
    private boolean dragging;
    private double grab;

    public record Geometry(int x, int top, int bottom, int offset, int content, int visible) {
        public Geometry {
            bottom = Math.max(top, bottom);
            content = Math.max(0, content);
            visible = Math.max(0, visible);
            offset = Math.clamp(offset, 0, Math.max(0, content - visible));
        }
        public int maximumOffset() { return Math.max(0, content - visible); }
        public int height() { return bottom - top; }
        public boolean scrollable() { return height() > 0 && visible > 0 && maximumOffset() > 0; }
        public int thumbHeight() {
            return Math.min(height(), Math.max(7, Math.round(height() * Math.min(1F, visible / (float) Math.max(1, content)))));
        }
        public int thumbTop() { return top + Math.round((height() - thumbHeight()) * (offset / (float) Math.max(1, maximumOffset()))); }
        public boolean contains(double mouseX, double mouseY) {
            return scrollable() && mouseX >= x - 3 && mouseX < x + 6 && mouseY >= top && mouseY < bottom;
        }
    }

    public boolean mouseClicked(Click click, Geometry geometry, IntConsumer setter) {
        if (click.button() != 0 || !geometry.contains(click.x(), click.y())) return false;
        dragging = true;
        boolean thumb = click.y() >= geometry.thumbTop() && click.y() < geometry.thumbTop() + geometry.thumbHeight();
        grab = thumb ? click.y() - geometry.thumbTop() : geometry.thumbHeight() / 2.0;
        if (!thumb) move(click.y(), geometry, setter);
        return true;
    }

    public boolean mouseDragged(Click click, Geometry geometry, IntConsumer setter) {
        if (!dragging || click.button() != 0) return false;
        if (geometry.scrollable()) move(click.y(), geometry, setter);
        else cancel();
        return true;
    }

    private void move(double mouseY, Geometry geometry, IntConsumer setter) {
        int travel = geometry.height() - geometry.thumbHeight();
        double fraction = travel <= 0 ? 0.0 : (mouseY - grab - geometry.top()) / travel;
        setter.accept((int) Math.round(Math.clamp(fraction, 0.0, 1.0) * geometry.maximumOffset()));
    }

    public boolean mouseReleased(Click click) {
        if (click.button() != 0 || !dragging) return false;
        cancel(); return true;
    }
    public void cancel() { dragging = false; grab = 0; }
    public boolean isDragging() { return dragging; }

    public static void draw(DrawContext context, Geometry geometry) {
        if (!geometry.scrollable()) return;
        context.fill(geometry.x(), geometry.top(), geometry.x() + 3, geometry.bottom(), 0xFF232728);
        context.fill(geometry.x(), geometry.thumbTop(), geometry.x() + 3,
            geometry.thumbTop() + geometry.thumbHeight(), 0xFF687171);
    }
}
