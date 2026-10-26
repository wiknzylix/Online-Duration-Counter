package net.wiknzylix.playtimetogether.client;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.wiknzylix.playtimetogether.PlaytimeSnapshot;

/** Flat native controls. Preferences stay on this client; recording continues on the server. */
public final class PlaytimeConfigScreen extends Screen {
    private enum Tab { DISPLAY, PLAYERS }
    private static final int TOP = 62;
    private final Screen parent;
    private Tab tab = Tab.DISPLAY;
    private int displayScroll, displayContent, playerScroll;
    private boolean waitingForConfigurationKey;
    private UUID displayedWorld;
    private long displayedRevision;
    private PlaytimeSnapshot playerSnapshot;
    private List<PlaytimeClient.DisplayEntry> players = List.of();
    private final List<Label> labels = new ArrayList<>();
    private final PlaytimeScrollbar scrollbar = new PlaytimeScrollbar();
    private int layoutWidth = -1, layoutHeight = -1;
    public PlaytimeConfigScreen(Screen parent) { super(Text.translatable("playtime_together.config.title")); this.parent = parent; }
    private int panelWidth() { return Math.min(940, Math.max(220, width - 24)); }
    private int left() { return (width - panelWidth()) / 2; }
    private int bottom() { return height - 34; }
    private boolean columns() { return panelWidth() >= 360; }

    @Override protected void init() {
        if (width != layoutWidth || height != layoutHeight) scrollbar.cancel();
        layoutWidth = width; layoutHeight = height;
        labels.clear();
        int tabWidth = Math.min(150, (panelWidth() - 4) / 2);
        for (Tab candidate : Tab.values()) {
            FlatButton button = flatButton(left() + candidate.ordinal() * (tabWidth + 4), 34, tabWidth, 21,
                Text.translatable("playtime_together.config.tab." + candidate.name().toLowerCase(Locale.ROOT)), clicked -> {
                    if (tab != candidate) { scrollbar.cancel(); waitingForConfigurationKey = false; tab = candidate; clearAndInit(); }
                });
            button.selected = () -> tab == candidate; addDrawableChild(button);
        }
        if (tab == Tab.DISPLAY) {
            createDisplay();
            int clamped = Math.clamp(displayScroll, 0, Math.max(0, displayContent - (bottom() - TOP)));
            if (clamped != displayScroll) { displayScroll = clamped; clearAndInit(); return; }
        } else createPlayers();
        addDrawableChild(flatButton(width / 2 - 65, height - 26, 130, 20, Text.translatable("gui.done"), button -> close()));
        var snapshot = PlaytimeClient.snapshot(); displayedWorld = snapshot == null ? null : snapshot.worldId();
        displayedRevision = snapshot == null ? Long.MIN_VALUE : snapshot.revision();
    }
    private void createDisplay() {
        ClientConfig c = PlaytimeClient.config(); int x = left(), w = panelWidth() - 8, col = columns() ? (w - 6) / 2 : w, y = TOP - displayScroll;
        section("display", y); y += 15;
        addDisplay(flatButton(x, y, col, 21, enabledText(), b -> { c.enabled = !c.enabled; c.changed(); b.setMessage(enabledText()); }));
        addDisplay(flatButton(columns() ? x + col + 6 : x, columns() ? y : y + 25, col, 21, formatText(), b -> {
            c.timeFormat = c.timeFormat.next(); c.changed(); b.setMessage(formatText()); })); y += columns() ? 29 : 54;
        section("layout", y); y += 15;
        addDisplay(new FlatSlider(x, y, col, "width", 60, 1200, 1, () -> c.width, v -> c.width = (int) v, false));
        addDisplay(new FlatSlider(columns() ? x + col + 6 : x, columns() ? y : y + 25, col, "height", 40, 1200, 1, () -> c.height, v -> c.height = (int) v, false));
        y += columns() ? 25 : 50;
        addDisplay(new FlatSlider(x, y, col, "font_scale", 0.35, 3, 0.05, () -> c.fontScale, v -> c.fontScale = v, true));
        addDisplay(new FlatSlider(columns() ? x + col + 6 : x, columns() ? y : y + 25, col, "background_opacity", 0, 100, 1, () -> c.backgroundOpacity, v -> c.backgroundOpacity = (int) v, false));
        y += columns() ? 29 : 54;
        addDisplay(flatButton(x, y, col, 21, Text.translatable("playtime_together.config.position"), b -> client.setScreen(new PlaytimePositionScreen(this))));
        addDisplay(flatButton(columns() ? x + col + 6 : x, columns() ? y : y + 25, col, 21,
            Text.translatable("playtime_together.config.color_settings"), b -> client.setScreen(new PlaytimeColorsScreen(this)))); y += columns() ? 29 : 54;
        section("controls", y); y += 15;
        addDisplay(flatButton(x, y, w, 21, configurationKeyText(), b -> { waitingForConfigurationKey = true; b.setMessage(configurationKeyText()); })); y += 26;
        labels.add(new Label("", x + 4, y, w - 8, false)); y += Math.max(21, textRenderer.wrapLines(keyHint(), w - 8).size() * 9 + 6);
        addDisplay(flatButton(x, y, w, 21, Text.translatable("playtime_together.config.defaults"), b -> {
            c.restoreDisplayDefaults(); c.save(); displayScroll = 0; clearAndInit(); }));
        displayContent = y + 21 + displayScroll - TOP;
    }
    private void section(String key, int y) { labels.add(new Label("playtime_together.config.section." + key, left() + 5, y + 1, panelWidth() - 10, true)); }
    private void addDisplay(ClickableWidget widget) { widget.visible = widget.getY() >= TOP && widget.getY() + widget.getHeight() <= bottom(); addDrawableChild(widget); }
    private Text enabledText() { return Text.translatable("playtime_together.config.enabled", Text.translatable(PlaytimeClient.config().enabled ? "options.on" : "options.off")); }
    private Text formatText() { return Text.translatable("playtime_together.config.format", PlaytimeClient.config().timeFormat.label()); }
    private int controlsWidth() { return Math.clamp(panelWidth() / 4, 90, 164); }
    private int listLeft() { return left() + controlsWidth() + 8; }
    private int listWidth() { return panelWidth() - controlsWidth() - 8; }
    private int listTop() { return TOP + 18; }
    private int playerRows() { return Math.max(1, (bottom() - listTop()) / 16); }
    private List<PlaytimeClient.DisplayEntry> allPlayers() {
        var snapshot = PlaytimeClient.snapshot();
        if (snapshot != playerSnapshot) {
            playerSnapshot = snapshot;
            players = PlaytimeClient.entries(false).stream().sorted(Comparator.comparing(PlaytimeClient.DisplayEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.uuid().toString())).toList();
        }
        return players;
    }
    private void createPlayers() {
        var snapshot = PlaytimeClient.snapshot(); var filter = snapshot == null ? null : PlaytimeClient.config().filter(snapshot.worldId());
        List<PlaytimeClient.DisplayEntry> known = allPlayers(); int y = TOP + 18;
        String[] keys = {"filter_all", "select_all", "select_inverse", "select_none", "rules"};
        for (int i = 0; i < keys.length; i++) {
            final int action = i;
            FlatButton button = flatButton(left() + 4, y, controlsWidth() - 8, 20, Text.translatable("playtime_together.config." + keys[i]), b -> {
                if (action == 4) { client.setScreen(new PlaytimeRulesScreen(this, snapshot == null ? null : snapshot.worldId())); return; }
                if (filter == null) return;
                if (action == 0) filter.showAll = !filter.showAll;
                else if (action == 1) { filter.showAll = false; filter.selected.clear(); for (var e : known) filter.selected.add(e.uuid().toString()); }
                else if (action == 2) {
                    var inverse = new HashSet<String>(); for (var e : known) if (!(filter.showAll || filter.selected.contains(e.uuid().toString()))) inverse.add(e.uuid().toString());
                    filter.showAll = false; filter.selected.clear(); filter.selected.addAll(inverse);
                } else { filter.showAll = false; filter.selected.clear(); }
                PlaytimeClient.config().changed(); clearAndInit();
            });
            button.active = filter != null || action == 4; if (action == 0) button.selected = () -> filter != null && filter.showAll;
            addDrawableChild(button); y += 24;
        }
        playerScroll = Math.clamp(playerScroll, 0, Math.max(0, known.size() - playerRows()));
        int identityWidth = 30;
        for (var type : ClientConfig.IdentityOverride.values()) identityWidth = Math.max(identityWidth, textRenderer.getWidth(identityLabel(type)) + 10);
        for (int row = 0; row < playerRows() && row + playerScroll < known.size(); row++) {
            var e = known.get(row + playerScroll);
            int rowX = listLeft() + 3, rowY = listTop() + row * 16, rowWidth = listWidth() - 10;
            int statusWidth = textRenderer.getWidth(Text.translatable(e.online() ? "playtime_together.player.online" : "playtime_together.player.offline"));
            int identityX = rowX + rowWidth - statusWidth - identityWidth - 8;
            var player = addDrawableChild(new PlayerButton(rowX, rowY, rowWidth, identityX, identityWidth, e, filter, () -> {
                if (filter.showAll) { filter.showAll = false; filter.selected.clear(); for (var p : known) filter.selected.add(p.uuid().toString()); }
                if (!filter.selected.remove(e.uuid().toString())) filter.selected.add(e.uuid().toString()); PlaytimeClient.config().changed();
            }));
            addDrawableChild(new IdentityButton(identityX, rowY, identityWidth, e, filter, player::refreshTooltip));
        }
    }
    private static Text identityLabel(ClientConfig.IdentityOverride type) {
        return Text.translatable("playtime_together.identity." + type.name().toLowerCase(Locale.ROOT));
    }
    @Override public void tick() {
        var snapshot = PlaytimeClient.snapshot(); UUID world = snapshot == null ? null : snapshot.worldId(); long rev = snapshot == null ? Long.MIN_VALUE : snapshot.revision();
        if (!Objects.equals(displayedWorld, world) || (tab == Tab.PLAYERS && rev != displayedRevision)) clearAndInit();
    }
    private KeyBinding keyConflict() {
        KeyBinding key = PlaytimeClient.configurationKey(); if (key == null || key.isUnbound()) return null;
        for (KeyBinding other : client.options.allKeys) if (other != key && key.equals(other)) return other; return null;
    }
    private Text configurationKeyText() {
        KeyBinding key = PlaytimeClient.configurationKey(); return Text.translatable("playtime_together.config.open_config_key", waitingForConfigurationKey
            ? Text.translatable("playtime_together.config.key_waiting") : key == null ? Text.literal("—") : key.getBoundKeyLocalizedText());
    }
    private Text keyHint() {
        if (waitingForConfigurationKey) return Text.translatable("playtime_together.config.key_listening");
        KeyBinding conflict = keyConflict(); return conflict == null ? Text.translatable("playtime_together.config.key_help")
            : Text.translatable("playtime_together.config.key_conflict", Text.translatable(conflict.getId()));
    }
    private void bindConfigurationKey(InputUtil.Key key) {
        KeyBinding binding = PlaytimeClient.configurationKey();
        if (binding != null) { binding.setBoundKey(key); binding.setPressed(false); while (binding.wasPressed()) { } KeyBinding.updateKeysByCode(); client.options.write(); }
        waitingForConfigurationKey = false; clearAndInit();
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (!waitingForConfigurationKey) return super.keyPressed(input);
        if (input.key() == InputUtil.GLFW_KEY_ESCAPE) { waitingForConfigurationKey = false; clearAndInit(); }
        else if (input.key() == InputUtil.GLFW_KEY_DELETE || input.key() == InputUtil.GLFW_KEY_BACKSPACE) bindConfigurationKey(InputUtil.UNKNOWN_KEY);
        else bindConfigurationKey(InputUtil.fromKeyCode(input)); return true;
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (waitingForConfigurationKey) { if (click.button() >= 0) bindConfigurationKey(InputUtil.Type.MOUSE.createFromCode(click.button())); return true; }
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
    public PlaytimeScrollbar.Geometry scrollbarGeometry() {
        return tab == Tab.DISPLAY
            ? new PlaytimeScrollbar.Geometry(left() + panelWidth() - 3, TOP, bottom(), displayScroll, displayContent, bottom() - TOP)
            : new PlaytimeScrollbar.Geometry(listLeft() + listWidth() - 4, listTop(), bottom(), playerScroll, allPlayers().size(), playerRows());
    }
    private void setScroll(int value) {
        int clamped = Math.clamp(value, 0, scrollbarGeometry().maximumOffset());
        if (tab == Tab.DISPLAY) { if (displayScroll == clamped) return; displayScroll = clamped; }
        else { if (playerScroll == clamped) return; playerScroll = clamped; }
        clearAndInit();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (waitingForConfigurationKey) return true;
        if (y >= TOP && y <= bottom() && vertical != 0) {
            scrollbar.cancel();
            setScroll((tab == Tab.DISPLAY ? displayScroll : playerScroll) - (int) (vertical * (tab == Tab.DISPLAY ? 22 : 3)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        paintBackground(context, width, height, title); context.enableScissor(left(), TOP, left() + panelWidth(), bottom());
        if (tab == Tab.DISPLAY) {
            context.fill(left(), TOP, left() + panelWidth(), bottom(), 0xFF1A1C1D);
            for (Label l : labels) {
                if (l.heading) context.fill(l.x - 5, l.y - 2, l.x + l.width + 5, l.y + 11, 0xFF272A2B);
                context.drawWrappedTextWithShadow(textRenderer, l.key.isEmpty() ? keyHint() : Text.translatable(l.key), l.x, l.y, l.width, l.heading ? 0xFFE0E0E0 : 0xFF9FA6A7);
            }
        } else {
            context.fill(left(), TOP, left() + controlsWidth(), bottom(), 0xFF1D2021); context.fill(listLeft(), TOP, listLeft() + listWidth(), bottom(), 0xFF171A1B);
            context.drawTextWithShadow(textRenderer, Text.translatable("playtime_together.config.section.selection"), left() + 7, TOP + 4, 0xFFB8BEBF);
            context.drawTextWithShadow(textRenderer, Text.translatable("playtime_together.config.player_count", allPlayers().size()), listLeft() + 5, TOP + 4, 0xFFB8BEBF);
            if (PlaytimeClient.snapshot() == null || allPlayers().isEmpty()) context.drawWrappedTextWithShadow(textRenderer,
                Text.translatable(PlaytimeClient.snapshot() == null ? "playtime_together.config.no_world" : "playtime_together.config.no_players"), listLeft() + 8, listTop() + 7, listWidth() - 16, 0xFF9BA1A2);
        }
        context.disableScissor(); super.render(context, mouseX, mouseY, delta);
        PlaytimeScrollbar.draw(context, scrollbarGeometry());
    }
    @Override public void close() { PlaytimeClient.config().save(); client.setScreen(parent); }
    @Override public void removed() { scrollbar.cancel(); waitingForConfigurationKey = false; PlaytimeClient.config().save(); }
    @Override public boolean shouldPause() { return false; }

    public static void paintBackground(DrawContext c, int w, int h, Text title) { c.fill(0, 0, w, h, 0xF0111415); c.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, title, w / 2, 13, 0xFFE7EAEA); }
    public static FlatButton flatButton(int x, int y, int w, int h, Text text, ButtonWidget.PressAction action) { return new FlatButton(x, y, w, Math.min(h, 18), text, action); }
    public static String fit(TextRenderer r, String text, int width) { return r.getWidth(text) <= width ? text : width < r.getWidth("…") ? "" : r.trimToWidth(text, width - r.getWidth("…")) + "…"; }
    public static void drawScrollBar(DrawContext c, int x, int top, int bottom, int offset, int content, int visible) {
        PlaytimeScrollbar.draw(c, new PlaytimeScrollbar.Geometry(x, top, bottom, offset, content, visible));
    }
    public static class FlatButton extends ButtonWidget {
        public BooleanSupplier selected = () -> false;
        public FlatButton(int x, int y, int w, int h, net.minecraft.text.Text text, PressAction action) { super(x, y, w, h, text, action, DEFAULT_NARRATION_SUPPLIER); }
        @Override protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean marked = selected.getAsBoolean(); c.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                !active ? 0xFF222627 : isHovered() || isFocused() ? 0xFF414849 : marked ? 0xFF35423B : 0xFF2C3233);
            if (marked) c.fill(getX(), getY(), getX() + 2, getY() + getHeight(), 0xFF8BA38C);
            TextRenderer r = MinecraftClient.getInstance().textRenderer; c.drawCenteredTextWithShadow(r, net.minecraft.text.Text.literal(fit(r, getMessage().getString(), getWidth() - 10)),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, active ? 0xFFE1E5E5 : 0xFF747D7E);
        }
    }
    public static final class FlatTextField extends TextFieldWidget {
        public FlatTextField(TextRenderer r, int x, int y, int w, Text label) {
            super(r, x + 4, y + 4, w - 8, 12, label); setDrawsBackground(false); setTextShadow(false); setEditableColor(0xFFE0E6E5); setMaxLength(2048);
        }
        @Override public void renderWidget(DrawContext c, int x, int y, float delta) {
            c.fill(getX() - 4, getY() - 4, getX() + getWidth() + 4, getY() + 16, isFocused() ? 0xFF363F40 : 0xFF262C2D);
            if (isFocused()) c.fill(getX() - 4, getY() + 15, getX() + getWidth() + 4, getY() + 16, 0xFF91A69A); super.renderWidget(c, x, y, delta);
        }
    }
    private static final class PlayerButton extends FlatButton {
        private final PlaytimeClient.DisplayEntry entry; private final ClientConfig.WorldFilter filter;
        private final int identityX, identityWidth;
        PlayerButton(int x, int y, int w, int identityX, int identityWidth, PlaytimeClient.DisplayEntry e, ClientConfig.WorldFilter filter, Runnable toggle) {
            super(x, y, w, 15, net.minecraft.text.Text.literal(e.name()), b -> toggle.run()); entry = e; this.filter = filter;
            this.identityX = identityX; this.identityWidth = identityWidth; refreshTooltip();
        }
        private void refreshTooltip() {
            setTooltip(Tooltip.of(net.minecraft.text.Text.literal(entry.name() + "\n" + entry.uuid() + "\n" + net.minecraft.text.Text.translatable(entry.online() ? "playtime_together.player.online" : "playtime_together.player.offline").getString()
                + (filter.effectiveFake(entry.uuid(), entry.fakePlayer()) ? " · " + net.minecraft.text.Text.translatable("playtime_together.player.fake").getString() : ""))));
        }
        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return super.isMouseOver(mouseX, mouseY) && (mouseX < identityX || mouseX >= identityX + identityWidth);
        }
        @Override public boolean mouseClicked(Click click, boolean doubled) {
            if (click.x() >= identityX && click.x() < identityX + identityWidth) return false;
            return super.mouseClicked(click, doubled);
        }
        @Override protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean checked = filter.showAll || filter.selected.contains(entry.uuid().toString()), blocked = filter.isBlocked(entry.uuid(), entry.name(), entry.fakePlayer());
            c.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHovered() || isFocused() ? checked ? 0xFF405046 : 0xFF343D40 : checked ? 0xFF303D35 : 0xFF22292B);
            TextRenderer r = MinecraftClient.getInstance().textRenderer; c.fill(getX() + 4, getY() + 4, getX() + 11, getY() + 11, checked ? 0xFF97B39A : 0xFF576164);
            if (checked) c.drawTextWithShadow(r, "✓", getX() + 4, getY() + 3, 0xFF152119);
            String status = net.minecraft.text.Text.translatable(entry.online() ? "playtime_together.player.online" : "playtime_together.player.offline").getString();
            String name = blocked ? "⊘ " + entry.name() : entry.name();
            int sw = r.getWidth(status); c.drawTextWithShadow(r, fit(r, name, Math.max(0, identityX - getX() - 20)), getX() + 16, getY() + 3, blocked ? 0xFF7C8583 : 0xFFD5DDDA);
            c.drawTextWithShadow(r, status, getX() + getWidth() - sw - 4, getY() + 3, entry.online() ? 0xFF9DB79B : 0xFF909B9D);
        }
    }
    public static final class IdentityButton extends FlatButton {
        private final PlaytimeClient.DisplayEntry entry;
        private final ClientConfig.WorldFilter filter;
        private IdentityButton(int x, int y, int w, PlaytimeClient.DisplayEntry entry, ClientConfig.WorldFilter filter, Runnable updateRow) {
            super(x, y, w, 15, identityLabel(filter.identity(entry.uuid())), button -> {
                filter.setIdentity(entry.uuid(), filter.identity(entry.uuid()).next());
                PlaytimeClient.config().changed();
                ((IdentityButton) button).refresh(); updateRow.run();
            });
            this.entry = entry; this.filter = filter;
            selected = () -> identity() != ClientConfig.IdentityOverride.AUTO;
            refresh();
        }
        public UUID playerId() { return entry.uuid(); }
        public ClientConfig.IdentityOverride identity() { return filter.identity(entry.uuid()); }
        private void refresh() {
            setMessage(identityLabel(identity()));
            setTooltip(Tooltip.of(net.minecraft.text.Text.translatable("playtime_together.identity.tooltip", entry.name(), entry.uuid().toString(), identityLabel(identity()))));
        }
        @Override protected net.minecraft.text.MutableText getNarrationMessage() {
            return net.minecraft.text.Text.translatable("playtime_together.identity.narration", entry.name(), identityLabel(identity()), entry.uuid().toString());
        }
    }
    private static final class FlatSlider extends SliderWidget {
        private final String label; private final double min, max, step; private final DoubleSupplier getter; private final DoubleConsumer setter; private final boolean decimal;
        FlatSlider(int x, int y, int w, String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, boolean decimal) {
            super(x, y, w, 18, Text.empty(), (getter.getAsDouble() - min) / (max - min)); this.label = label; this.min = min; this.max = max; this.step = step; this.getter = getter; this.setter = setter; this.decimal = decimal; updateMessage();
        }
        @Override protected void updateMessage() { if (getter != null) setMessage(Text.translatable("playtime_together.config." + label, decimal ? String.format(Locale.ROOT, "%.2fx", getter.getAsDouble()) : Integer.toString((int) getter.getAsDouble()))); }
        @Override protected void applyValue() { setter.accept(Math.clamp(min + Math.round(value * (max - min) / step) * step, min, max)); PlaytimeClient.config().changed(); }
        @Override public void renderWidget(DrawContext c, int x, int y, float delta) {
            c.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHovered() || isFocused() ? 0xFF3B4445 : 0xFF2A3031);
            c.fill(getX() + 2, getY() + getHeight() - 3, getX() + 2 + (int) ((getWidth() - 4) * value), getY() + getHeight() - 1, 0xFF859C8B);
            TextRenderer r = MinecraftClient.getInstance().textRenderer; c.drawCenteredTextWithShadow(r, Text.literal(fit(r, getMessage().getString(), getWidth() - 8)), getX() + getWidth() / 2, getY() + 5, 0xFFE0E6E4);
        }
    }
    private record Label(String key, int x, int y, int width, boolean heading) { }
}
