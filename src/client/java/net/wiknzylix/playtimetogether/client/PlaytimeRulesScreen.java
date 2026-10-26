package net.wiknzylix.playtimetogether.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Rule editing keeps the same input widgets alive when snapshots or the scroll position change. */
public final class PlaytimeRulesScreen extends Screen {
    private static final int TOP = 45;
    private static final int CONTENT_HEIGHT = 290;
    private final Screen parent;
    private final UUID worldId;
    private final List<Placement> placements = new ArrayList<>();
    private int scroll;
    private int affected;
    private final PlaytimeScrollbar scrollbar = new PlaytimeScrollbar();
    private int layoutWidth = -1, layoutHeight = -1;

    public PlaytimeRulesScreen(Screen parent, UUID worldId) {
        super(Text.translatable("playtime_together.rules.title")); this.parent = parent; this.worldId = worldId;
    }
    private int areaWidth() { return Math.min(660, Math.max(220, width - 28)); }
    private int left() { return (width - areaWidth()) / 2; }
    private int bottom() { return height - 35; }
    private ClientConfig.WorldFilter filter() { return worldId == null ? null : PlaytimeClient.config().filter(worldId); }

    @Override protected void init() {
        if (width != layoutWidth || height != layoutHeight) scrollbar.cancel();
        layoutWidth = width; layoutHeight = height;
        placements.clear();
        var filter = filter();
        if (filter != null) {
            int x = left() + 5, w = areaWidth() - 18, half = (w - 5) / 2;
            var automatic = PlaytimeConfigScreen.flatButton(x, TOP + 5, w, 21, Text.translatable("playtime_together.rules.auto_block"), b -> {
                filter.autoBlockRules = !filter.autoBlockRules; PlaytimeClient.config().changed();
            });
            automatic.selected = () -> filter.autoBlockRules; place(automatic);
            var numeric = PlaytimeConfigScreen.flatButton(x, TOP + 32, half, 21, Text.translatable("playtime_together.rules.numeric_ids"), b -> {
                filter.blockNumericIds = !filter.blockNumericIds; PlaytimeClient.config().changed();
            });
            numeric.selected = () -> filter.blockNumericIds; place(numeric);
            var fake = PlaytimeConfigScreen.flatButton(x + half + 5, TOP + 32, half, 21, Text.translatable("playtime_together.rules.fake_players"), b -> {
                filter.blockFakePlayers = !filter.blockFakePlayers; PlaytimeClient.config().changed();
            });
            fake.selected = () -> filter.blockFakePlayers; place(fake);
            var keywords = new PlaytimeConfigScreen.FlatTextField(textRenderer, x, TOP + 76, w, Text.translatable("playtime_together.rules.keywords"));
            keywords.setText(filter.blockedKeywords); keywords.setPlaceholder(Text.translatable("playtime_together.rules.keywords_placeholder"));
            keywords.setChangedListener(value -> { filter.blockedKeywords = value; PlaytimeClient.config().changed(); }); place(keywords);
            var regex = new PlaytimeConfigScreen.FlatTextField(textRenderer, x, TOP + 132, w, Text.translatable("playtime_together.rules.regex"));
            regex.setText(filter.blockedRegex); regex.setPlaceholder(Text.translatable("playtime_together.rules.regex_placeholder"));
            regex.setChangedListener(value -> { filter.blockedRegex = value; PlaytimeClient.config().changed(); }); place(regex);
            place(PlaytimeConfigScreen.flatButton(x, TOP + 206, half, 21, Text.translatable("playtime_together.rules.select_matching"), b -> applyRuleSelection(true)));
            place(PlaytimeConfigScreen.flatButton(x + half + 5, TOP + 206, half, 21, Text.translatable("playtime_together.rules.deselect_matching"), b -> applyRuleSelection(false)));
        }
        reposition();
        addDrawableChild(PlaytimeConfigScreen.flatButton(width / 2 - 65, height - 26, 130, 20, Text.translatable("gui.done"), b -> close()));
    }
    private void place(ClickableWidget widget) { placements.add(new Placement(widget, widget.getY())); addDrawableChild(widget); }
    private void reposition() {
        scroll = Math.clamp(scroll, 0, scrollbarGeometry().maximumOffset());
        for (Placement placement : placements) {
            ClickableWidget widget = placement.widget; widget.setY(placement.y - scroll);
            int before = widget instanceof PlaytimeConfigScreen.FlatTextField ? 4 : 0;
            int after = widget instanceof PlaytimeConfigScreen.FlatTextField ? 4 : 0;
            widget.visible = widget.getY() - before >= TOP && widget.getY() + widget.getHeight() + after <= bottom();
        }
    }
    public PlaytimeScrollbar.Geometry scrollbarGeometry() {
        return new PlaytimeScrollbar.Geometry(left() + areaWidth() - 3, TOP, bottom(), scroll,
            filter() == null ? 0 : CONTENT_HEIGHT, bottom() - TOP);
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
    private void applyRuleSelection(boolean select) {
        var filter = filter(); if (filter == null) return;
        var snapshot = PlaytimeClient.snapshot();
        if (snapshot == null || !worldId.equals(snapshot.worldId())) return;
        if (select) filter.selected.clear();
        else if (filter.showAll) { filter.selected.clear(); for (var entry : snapshot.entries()) filter.selected.add(entry.uuid().toString()); }
        affected = 0;
        for (var entry : snapshot.entries()) {
            if (!filter.matches(entry.uuid(), entry.name(), entry.fakePlayer())) continue;
            if (select) filter.selected.add(entry.uuid().toString()); else filter.selected.remove(entry.uuid().toString());
            affected++;
        }
        filter.showAll = false; PlaytimeClient.config().changed();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= TOP && y <= bottom() && vertical != 0) { scrollbar.cancel(); setScroll(scroll - (int) (vertical * 22)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private void wrapped(DrawContext context, Text text, int offset, int color) {
        context.drawWrappedTextWithShadow(textRenderer, text, left() + 6, TOP + offset - scroll, areaWidth() - 20, color);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PlaytimeConfigScreen.paintBackground(context, width, height, title);
        context.fill(left(), TOP, left() + areaWidth(), bottom(), 0xFF1A2021);
        context.enableScissor(left(), TOP, left() + areaWidth(), bottom());
        var filter = filter();
        if (filter == null) wrapped(context, Text.translatable("playtime_together.config.no_world"), 12, 0xFFA8B0AE);
        else {
            wrapped(context, Text.translatable("playtime_together.rules.keywords"), 62, 0xFFD8DEDC);
            wrapped(context, Text.translatable("playtime_together.rules.keywords_help"), 102, 0xFF99A4A0);
            wrapped(context, Text.translatable("playtime_together.rules.regex"), 119, 0xFFD8DEDC);
            String error = filter.regexError();
            wrapped(context, error != null && !error.isBlank() ? Text.translatable("playtime_together.rules.regex_error", error)
                : Text.translatable("playtime_together.rules.regex_help"), 159, error != null && !error.isBlank() ? 0xFFC49690 : 0xFF99A4A0);
            wrapped(context, Text.translatable("playtime_together.rules.selection_help"), 183, 0xFF99A4A0);
            wrapped(context, Text.translatable(filter.autoBlockRules ? "playtime_together.rules.auto_notice" : "playtime_together.rules.local_notice"), 235, 0xFFAFB6A3);
            if (affected > 0) wrapped(context, Text.translatable("playtime_together.rules.affected", affected), 272, 0xFF9DB79B);
        }
        context.disableScissor(); super.render(context, mouseX, mouseY, delta);
        PlaytimeScrollbar.draw(context, scrollbarGeometry());
    }
    @Override public void close() { PlaytimeClient.config().save(); client.setScreen(parent); }
    @Override public void removed() { scrollbar.cancel(); PlaytimeClient.config().save(); }
    @Override public boolean shouldPause() { return false; }
    private record Placement(ClickableWidget widget, int y) { }
}
