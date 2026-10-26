package net.wiknzylix.playtimetogether.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Four independent text colors; invalid edits leave the last valid saved color intact. */
public final class PlaytimeColorsScreen extends Screen {
    private static final int TOP = 42, RESET_Y = 165;
    private final Screen parent;
    private final List<ColorInput> fields = new ArrayList<>();
    private final PlaytimeScrollbar scrollbar = new PlaytimeScrollbar();
    private ClickableWidget resetButton;
    private int scroll, layoutWidth = -1, layoutHeight = -1;
    public PlaytimeColorsScreen(Screen parent) { super(Text.translatable("playtime_together.colors.screen_title")); this.parent = parent; }
    private int areaWidth() { return Math.min(600, Math.max(230, width - 28)); }
    private int left() { return (width - areaWidth()) / 2; }
    private int bottom() { return height - 35; }
    @Override protected void init() {
        if (width != layoutWidth || height != layoutHeight) scrollbar.cancel();
        layoutWidth = width; layoutHeight = height;
        var pending = new HashMap<String, String>();
        for (ColorInput input : fields) pending.put(input.key, input.widget.getText());
        fields.clear(); var c = PlaytimeClient.config();
        addColor("title", 52, () -> c.titleColor, value -> c.titleColor = value);
        addColor("subtitle", 80, () -> c.subtitleColor, value -> c.subtitleColor = value);
        addColor("online", 108, () -> c.onlineColor, value -> c.onlineColor = value);
        addColor("offline", 136, () -> c.offlineColor, value -> c.offlineColor = value);
        for (ColorInput input : fields) if (pending.containsKey(input.key)) input.widget.setText(pending.get(input.key));
        resetButton = addDrawableChild(PlaytimeConfigScreen.flatButton(left() + 5, RESET_Y, areaWidth() - 18, 21,
            Text.translatable("playtime_together.colors.defaults"), button -> {
                c.restoreColorDefaults();
                for (ColorInput input : fields) input.widget.setText(hex(input.getter.getAsInt()));
                c.changed();
            }));
        reposition();
        addDrawableChild(PlaytimeConfigScreen.flatButton(width / 2 - 65, height - 26, 130, 20, Text.translatable("gui.done"), button -> close()));
    }
    private void addColor(String key, int y, IntSupplier getter, IntConsumer setter) {
        var field = new PlaytimeConfigScreen.FlatTextField(textRenderer, left() + areaWidth() - 140, y, 100, Text.translatable("playtime_together.colors." + key));
        field.setMaxLength(7); field.setText(hex(getter.getAsInt()));
        field.setChangedListener(value -> {
            Integer color = parse(value);
            field.setEditableColor(color == null ? 0xFFC49690 : 0xFFE0E6E5);
            if (color != null) { setter.accept(color); PlaytimeClient.config().changed(); }
            reposition();
        });
        addDrawableChild(field); fields.add(new ColorInput(key, y, field, getter));
    }
    private static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }
    private static Integer parse(String text) {
        String value = text.startsWith("#") ? text.substring(1) : text;
        return value.matches("[0-9a-fA-F]{6}") ? Integer.parseInt(value, 16) : null;
    }
    private boolean invalid() {
        for (ColorInput input : fields) if (parse(input.widget.getText()) == null) return true;
        return false;
    }
    private int contentHeight() {
        return invalid() ? 190 + textRenderer.wrapLines(Text.translatable("playtime_together.colors.invalid"), areaWidth() - 22).size() * 9 + 8 - TOP
            : RESET_Y + 18 + 10 - TOP;
    }
    public PlaytimeScrollbar.Geometry scrollbarGeometry() {
        return new PlaytimeScrollbar.Geometry(left() + areaWidth() - 3, TOP, bottom(), scroll, contentHeight(), bottom() - TOP);
    }
    private void reposition() {
        scroll = Math.clamp(scroll, 0, scrollbarGeometry().maximumOffset());
        for (ColorInput input : fields) {
            input.widget.setY(input.y + 4 - scroll);
            input.widget.visible = input.y - scroll >= TOP && input.y + 20 - scroll <= bottom();
        }
        if (resetButton != null) {
            resetButton.setY(RESET_Y - scroll);
            resetButton.visible = resetButton.getY() >= TOP && resetButton.getY() + resetButton.getHeight() <= bottom();
        }
    }
    private void setScroll(int value) { scroll = value; reposition(); }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (scrollbar.mouseClicked(click, scrollbarGeometry(), this::setScroll)) return true;
        return super.mouseClicked(click, doubled);
    }
    @Override public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (scrollbar.mouseDragged(click, scrollbarGeometry(), this::setScroll)) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }
    @Override public boolean mouseReleased(Click click) {
        if (scrollbar.mouseReleased(click)) return true;
        return super.mouseReleased(click);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= TOP && y <= bottom() && vertical != 0) { scrollbar.cancel(); setScroll(scroll - (int) (vertical * 22)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PlaytimeConfigScreen.paintBackground(context, width, height, title);
        context.fill(left(), TOP, left() + areaWidth(), bottom(), 0xFF1B2021);
        context.enableScissor(left(), TOP, left() + areaWidth(), bottom());
        for (ColorInput input : fields) {
            int y = input.y - scroll;
            context.drawTextWithShadow(textRenderer, Text.translatable("playtime_together.colors." + input.key), left() + 8, y + 6, 0xFFD1D9D6);
            int x = left() + areaWidth() - 33;
            context.fill(x - 1, y + 1, x + 17, y + 19, 0xFF69736E);
            context.fill(x, y + 2, x + 16, y + 18, 0xFF000000 | input.getter.getAsInt());
        }
        if (invalid()) context.drawWrappedTextWithShadow(textRenderer, Text.translatable("playtime_together.colors.invalid"),
            left() + 7, 190 - scroll, areaWidth() - 22, 0xFFC49690);
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
        PlaytimeScrollbar.draw(context, scrollbarGeometry());
    }
    @Override public void close() { PlaytimeClient.config().save(); client.setScreen(parent); }
    @Override public void removed() { scrollbar.cancel(); PlaytimeClient.config().save(); }
    @Override public boolean shouldPause() { return false; }
    private record ColorInput(String key, int y, PlaytimeConfigScreen.FlatTextField widget, IntSupplier getter) { }
}
