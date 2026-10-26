package net.wiknzylix.playtimetogether;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import com.mojang.authlib.GameProfile;
import net.wiknzylix.playtimetogether.client.DurationFormat;
import net.wiknzylix.playtimetogether.client.ClientConfig;
import net.wiknzylix.playtimetogether.client.PlaytimeClient;
import net.wiknzylix.playtimetogether.client.PlaytimeConfigScreen;
import net.wiknzylix.playtimetogether.client.PlaytimeColorsScreen;
import net.wiknzylix.playtimetogether.client.PlaytimeRulesScreen;
import net.wiknzylix.playtimetogether.client.PlaytimeHud;
import net.wiknzylix.playtimetogether.client.PlaytimePositionScreen;
import net.wiknzylix.playtimetogether.client.PlaytimePreviewScreen;
import net.wiknzylix.playtimetogether.client.PlaytimeScrollbar;
import org.lwjgl.glfw.GLFW;

/** Runs in Loom's isolated development game, never in the user's launcher instance. */
@SuppressWarnings("UnstableApiUsage")
public final class ODCClientGameTest implements FabricClientGameTest {
    private static final List<String> SCREENSHOTS = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        SCREENSHOTS.clear();
        try {
            Files.deleteIfExists(successSentinel());
        } catch (IOException exception) {
            throw new AssertionError("Could not clear the previous ODC test sentinel", exception);
        }
        context.runOnClient(client -> {
            client.options.pauseOnLostFocus = false;
            client.skipGameRender = false;
        });
        context.waitFor(client -> client.isFinishedLoading() && client.getOverlay() == null, 1200);
        var languageReload = context.computeOnClient(client -> {
            client.options.language = "zh_cn";
            client.getLanguageManager().setLanguage("zh_cn");
            return client.reloadResources();
        });
        context.waitFor(client -> languageReload.isDone() && client.getOverlay() == null, 1200);
        languageReload.join();
        configurationRegression(context);
        context.runOnClient(client -> {
            PlaytimeClient.config().restoreDisplayDefaults();
            client.setScreen(new PlaytimeConfigScreen(client.currentScreen));
        });
        context.waitForScreen(PlaytimeConfigScreen.class);
        screenshot(context, "odc-settings-display");
        displayScrollbarRegression(context);

        context.clickScreenButton("playtime_together.config.tab.players");
        screenshot(context, "odc-settings-players-no-world");
        demoPlayerListRegression(context);
        onlineOfflineRuleRegression(context);
        identityOverridesRegression(context);
        context.clickScreenButton("playtime_together.config.tab.display");
        clickVisibleButton(context, "playtime_together.config.color_settings");
        context.waitForScreen(PlaytimeColorsScreen.class);
        screenshot(context, "odc-settings-colors");
        shortWindowScrollbarRegression(context);

        context.setScreen(() -> new PlaytimePreviewScreen(null));
        context.waitForScreen(PlaytimePreviewScreen.class);
        screenshot(context, "odc-preview-default");
        context.runOnClient(client -> {
            PlaytimeClient.config().backgroundOpacity = 0;
            PlaytimeClient.config().changed();
        });
        screenshot(context, "odc-preview-opacity-0");
        context.runOnClient(client -> {
            PlaytimeClient.config().backgroundOpacity = 100;
            PlaytimeClient.config().changed();
        });
        screenshot(context, "odc-preview-opacity-100");
        previewRegression(context);

        context.runOnClient(client -> PlaytimeClient.config().restoreDisplayDefaults());
        context.setScreen(() -> null);
        singleplayerLifecycle(context);
        try {
            Files.writeString(successSentinel(), "ODC_CLIENT_SMOKE_PASSED\n"
                + "completed_at=" + java.time.Instant.now() + "\n"
                + "language=zh_cn\n"
                + "screenshots=" + SCREENSHOTS.size() + "; all PNGs readable and non-black\n"
                + "screens=" + String.join(",", SCREENSHOTS) + "\n"
                + "schema_v3_defaults=true; legacy_defaults_migrated=true; custom_legacy_values_preserved=true; configuration_ranges=true\n"
                + "configuration_default_y=true; inplace_key_capture=true; actual_keyboard_opens_configuration=true\n"
                + "synthetic_demo_player_list=20; demo_select_all_clear_invert_counts=true; demo_mixed_selection_screenshot=true\n"
                + "mouse_scrollbar_display_and_players_drag_and_track=true; mouse_scrollbar_rules_and_colors_short_window=true\n"
                + "multiplayer_preview=true; font_extremes=true; custom_four_colors_rendered_and_saved=true; color_reset_preserves_layout=true\n"
                + "position_four_corners_in_screen=true; oversized_hud_in_screen=true; position_editor_mouse_drag_persisted=true; edge_and_corner_resize_saved=true\n"
                + "rule_fields_and_invalid_regex=true; rule_auto_block_and_matching_selection=true; local_filters_keep_authoritative_records=true\n"
                + "fake_numeric_keyword_regex_hide_online_and_offline=true; disabling_rules_restores_both_groups=true\n"
                + "all_filtered_position_editor_stays_empty=true; menu_without_snapshot_still_previews_examples=true\n"
                + "identity_override_actual_ui_cycle_saved_reloaded=true; identity_override_keeps_keyword_rules_and_original_data=true\n"
                + "pause_frozen=true; unpause=true; ledger_saved=true; reopen=true; disconnect_cache_cleared=true\n"
                + "real_qb_backup_export_and_ledger_restore=" + FabricLoader.getInstance().isModLoaded("quickbackupmulti_reforged") + "\n"
                + "real_carpet_fake_metadata_and_local_filter=" + FabricLoader.getInstance().isModLoaded("carpet") + "\n"
                + "real_carpet_hidden_clock_continues_offline_freezes_same_uuid_reconnects=" + FabricLoader.getInstance().isModLoaded("carpet") + "\n"
                + "real_carpet_offline_hidden_until_rule_disabled=" + FabricLoader.getInstance().isModLoaded("carpet") + "\n"
                + "base_real_entity_then_carpet_same_uuid_offline_real_protection=" + FabricLoader.getInstance().isModLoaded("carpet") + "\n");
        } catch (IOException exception) {
            throw new AssertionError("Could not write the ODC test success sentinel", exception);
        }
    }

    private static Path successSentinel() {
        return FabricLoader.getInstance().getGameDir().resolve("odc-client-smoke-passed.txt");
    }

    private static void configurationRegression(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ClientConfig defaults = new ClientConfig();
            require(defaults.schemaVersion == 3 && defaults.width == 60 && defaults.height == 142
                && defaults.fontScale == 0.35 && defaults.positionX == 1.0 && defaults.positionY == 1.0,
                "Fresh configuration uses the compact v3 bottom-right defaults");

            ClientConfig legacy = new ClientConfig();
            legacy.schemaVersion = 1;
            legacy.width = 240;
            legacy.height = 220;
            legacy.fontScale = 1.0;
            legacy.sanitize();
            require(legacy.schemaVersion == 3 && legacy.width == 60 && legacy.height == 142 && legacy.fontScale == 0.35,
                "The unchanged v1 defaults migrate to the compact v3 defaults");

            ClientConfig customized = new ClientConfig();
            customized.schemaVersion = 1;
            customized.width = 300;
            customized.height = 400;
            customized.fontScale = 1.25;
            customized.sanitize();
            require(customized.schemaVersion == 3 && customized.width == 300 && customized.height == 400
                && customized.fontScale == 1.25, "Legacy customized dimensions and text size survive migration");

            ClientConfig extremes = new ClientConfig();
            extremes.width = 1;
            extremes.height = 1;
            extremes.fontScale = 0.1;
            extremes.positionX = -1;
            extremes.positionY = 2;
            extremes.sanitize();
            require(extremes.width == 60 && extremes.height == 40 && extremes.fontScale == 0.35
                && extremes.positionX == 0 && extremes.positionY == 1, "The lower bounds and normalized coordinates are enforced");
            extremes.width = 9_999;
            extremes.height = 9_999;
            extremes.fontScale = 9;
            extremes.sanitize();
            require(extremes.width == 1_200 && extremes.height == 1_200 && extremes.fontScale == 3.0,
                "The expanded upper bounds are accepted and clamped");

            PlaytimeClient.config().restoreDisplayDefaults();
            PlaytimeClient.config().save();
            require(PlaytimeClient.configurationKey().getDefaultKey().getCode() == GLFW.GLFW_KEY_Y,
                "The configuration screen opens with Y by default");
        });
    }

    private static void previewRegression(ClientGameTestContext context) {
        context.runOnClient(client -> {
            require(PlaytimePreviewScreen.previewEntries().size() >= 4
                    && PlaytimePreviewScreen.previewEntries().stream().anyMatch(entry -> !entry.online())
                    && PlaytimePreviewScreen.previewEntries().stream().anyMatch(PlaytimeClient.DisplayEntry::online),
                "The preview contains representative online and offline players");
            PlaytimeClient.config().restoreDisplayDefaults();
        });
        screenshot(context, "odc-preview-compact-60");
        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            config.width = 60;
            config.height = 40;
            config.fontScale = 0.35;
            config.changed();
        });
        screenshot(context, "odc-preview-font-min-dimensions-min");
        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            config.width = 1_200;
            config.height = 1_200;
            config.fontScale = 3.0;
            config.changed();
        });
        screenshot(context, "odc-preview-font-max-dimensions-max");
        colorRegression(context);
    }

    private static void displayScrollbarRegression(ClientGameTestContext context) {
        dragScrollbar(context, true);
        context.runOnClient(client -> {
            require(buttonVisible(client.currentScreen, "playtime_together.config.open_config_key"),
                "Dragging the display-page scrollbar exposes the configuration shortcut control");
            require(buttonVisible(client.currentScreen, "playtime_together.config.defaults"),
                "Dragging the display-page scrollbar exposes its final restore-defaults control");
        });
        context.getInput().setCursorPos(1, 1);
        screenshot(context, "odc-settings-display-scrollbar-bottom");
        clickScrollbarTrack(context, false);
        dragScrollbar(context, false);
        context.runOnClient(client -> require(buttonVisible(client.currentScreen, "playtime_together.config.enabled"),
            "Dragging the display-page thumb back to the top reveals the first setting again"));
    }

    private static void playerScrollbarRegression(ClientGameTestContext context) {
        dragScrollbar(context, true);
        context.runOnClient(client -> require(client.currentScreen.children().stream()
                .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .anyMatch(widget -> widget.visible && widget.getMessage().getString().startsWith("示例 Demo20")),
            "Dragging the real player-list scrollbar reaches the final demo player"));
        context.getInput().setCursorPos(1, 1);
        screenshot(context, "odc-settings-players-demo20-scrollbar-bottom");
        clickScrollbarTrack(context, false);
        clickScrollbarTrack(context, true);
        dragScrollbar(context, false);
        context.runOnClient(client -> require(client.currentScreen.children().stream()
                .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .anyMatch(widget -> widget.visible && widget.getMessage().getString().startsWith("示例 Demo01")),
            "Dragging the player-list thumb back to the top restores the first demo row"));
    }

    private static void shortWindowScrollbarRegression(ClientGameTestContext context) {
        int[] original = context.computeOnClient(client -> new int[] { client.getWindow().getWidth(), client.getWindow().getHeight() });
        UUID demoRules = UUID.fromString("00000000-0000-0000-0000-00000000d002");
        try {
            // The test input API preserves this virtual size even when the physical window has a larger minimum.
            context.getInput().resizeWindow(640, 200);
            context.waitTicks(2);
            context.waitForScreen(PlaytimeColorsScreen.class);
            dragScrollbar(context, true);
            context.runOnClient(client -> require(buttonVisible(client.currentScreen, "playtime_together.colors.defaults")
                    && textFieldVisible(client.currentScreen, "playtime_together.colors.offline"),
                "A short color page can drag its scrollbar to the final color and reset control"));
            context.getInput().setCursorPos(1, 1);
            screenshot(context, "odc-settings-colors-short-scrollbar-bottom");
            clickScrollbarTrack(context, false);
            dragScrollbar(context, false);
            context.runOnClient(client -> require(textFieldVisible(client.currentScreen, "playtime_together.colors.title"),
                "The color page scrollbar returns to its first text input"));

            context.setScreen(() -> new PlaytimeRulesScreen(null, demoRules));
            context.waitForScreen(PlaytimeRulesScreen.class);
            dragScrollbar(context, true);
            context.runOnClient(client -> require(buttonVisible(client.currentScreen, "playtime_together.rules.select_matching")
                    && buttonVisible(client.currentScreen, "playtime_together.rules.deselect_matching"),
                "A short rules page can drag its scrollbar to both final selection controls"));
            context.getInput().setCursorPos(1, 1);
            screenshot(context, "odc-settings-rules-short-scrollbar-bottom");
            clickScrollbarTrack(context, false);
            dragScrollbar(context, false);
            context.runOnClient(client -> require(buttonVisible(client.currentScreen, "playtime_together.rules.auto_block")
                    && textFieldVisible(client.currentScreen, "playtime_together.rules.keywords"),
                "The rules page scrollbar returns to the first controls and keyword input"));
        } finally {
            context.getInput().resizeWindow(original[0], original[1]);
            context.waitTicks(2);
            context.setScreen(() -> new PlaytimeColorsScreen(null));
            context.runOnClient(client -> {
                PlaytimeClient.config().worlds.remove(demoRules.toString());
                PlaytimeClient.config().changed();
                PlaytimeClient.config().save();
            });
        }
    }

    private static PlaytimeScrollbar.Geometry scrollbar(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            if (client.currentScreen instanceof PlaytimeConfigScreen screen) return screen.scrollbarGeometry();
            if (client.currentScreen instanceof PlaytimeRulesScreen screen) return screen.scrollbarGeometry();
            if (client.currentScreen instanceof PlaytimeColorsScreen screen) return screen.scrollbarGeometry();
            throw new AssertionError("The active screen does not expose a testable scrollbar");
        });
    }

    private static void dragScrollbar(ClientGameTestContext context, boolean bottom) {
        var geometry = scrollbar(context);
        require(geometry.scrollable(), "The tested page has overflowing content and an active scrollbar");
        double start = geometry.thumbTop() + geometry.thumbHeight() / 2.0;
        double end = bottom ? geometry.bottom() + 20.0 : geometry.top() - 20.0;
        dragOnScreen(context, geometry.x() + 1.0, start, 0, end - start);
        var result = scrollbar(context);
        require(result.offset() == (bottom ? result.maximumOffset() : 0),
            "Dragging the captured scrollbar thumb past the track reaches its " + (bottom ? "bottom" : "top"));
    }

    private static void clickScrollbarTrack(ClientGameTestContext context, boolean bottom) {
        var before = scrollbar(context);
        require(before.scrollable(), "Track clicking is tested on an overflowing page");
        double[] point = context.computeOnClient(client -> new double[] {
            (before.x() + 1.0) * client.getWindow().getWidth() / client.getWindow().getScaledWidth(),
            (bottom ? before.bottom() - 1.0 : before.top() + 1.0) * client.getWindow().getHeight() / client.getWindow().getScaledHeight()
        });
        context.getInput().setCursorPos(point[0], point[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        var after = scrollbar(context);
        require(bottom ? after.offset() > before.offset() : after.offset() < before.offset(),
            "Clicking the scrollbar track jumps toward its " + (bottom ? "bottom" : "top"));
    }

    private static boolean buttonVisible(net.minecraft.client.gui.screen.Screen screen, String key) {
        return screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
            .anyMatch(widget -> widget.visible && widget.active && widget.getMessage().getContent() instanceof TranslatableTextContent label
                && label.getKey().equals(key));
    }

    private static boolean textFieldVisible(net.minecraft.client.gui.screen.Screen screen, String key) {
        return screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast)
            .anyMatch(widget -> widget.visible && widget.active && widget.getMessage().getContent() instanceof TranslatableTextContent label
                && label.getKey().equals(key));
    }

    private static void demoPlayerListRegression(ClientGameTestContext context) {
        UUID demoWorld = UUID.fromString("00000000-0000-0000-0000-00000000d001");
        List<PlaytimeSnapshot.Entry> examples = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            String name = String.format(java.util.Locale.ROOT, "示例 Demo%02d", index);
            if (index == 3) name += "_LongDisplayName";
            examples.add(new PlaytimeSnapshot.Entry(new UUID(0xDD00L, index), name,
                (21L - index) * 3_600_000L, index <= 12, index % 5 == 0));
        }
        try {
            context.runOnClient(client -> {
                try {
                    // Explicit demo data only: no MinecraftServer or saved ledger is involved.
                    var receive = PlaytimeClient.class.getDeclaredMethod("receive", PlaytimeSnapshot.class);
                    receive.setAccessible(true);
                    receive.invoke(null, new PlaytimeSnapshot(demoWorld, 1L, true, examples));
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not inject the test-only 20-player demo", exception); }
            });
            context.waitTicks(2);
            playerScrollbarRegression(context);
            clickVisibleButton(context, "playtime_together.config.select_all");
            context.runOnClient(client -> require(PlaytimeClient.config().filter(demoWorld).selected.size() == 20
                    && PlaytimeClient.entries(true).size() == 20,
                "Select all includes all 20 demo players"));
            clickVisibleButton(context, "playtime_together.config.select_none");
            context.runOnClient(client -> require(PlaytimeClient.config().filter(demoWorld).selected.isEmpty()
                    && PlaytimeClient.entries(true).isEmpty(),
                "Clear removes all demo selections"));
            clickVisibleButton(context, "playtime_together.config.select_inverse");
            context.runOnClient(client -> require(PlaytimeClient.config().filter(demoWorld).selected.size() == 20
                    && PlaytimeClient.entries(true).size() == 20,
                "Invert selects all 20 previously cleared demo players"));
            for (int index = 1; index <= 3; index++) {
                String prefix = String.format(java.util.Locale.ROOT, "示例 Demo%02d", index);
                double[] point = context.computeOnClient(client -> client.currentScreen.children().stream()
                    .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(widget -> widget.visible && widget.active && widget.getMessage().getString().startsWith(prefix))
                    .map(widget -> new double[] {
                        (widget.getX() + widget.getWidth() / 2.0) * client.getWindow().getWidth() / client.getWindow().getScaledWidth(),
                        (widget.getY() + widget.getHeight() / 2.0) * client.getWindow().getHeight() / client.getWindow().getScaledHeight()
                    }).findFirst().orElseThrow());
                context.getInput().setCursorPos(point[0], point[1]);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            context.runOnClient(client -> require(PlaytimeClient.config().filter(demoWorld).selected.size() == 17
                    && PlaytimeClient.entries(true).size() == 17,
                "Actual row clicks produce a mixed checked and unchecked 20-player demo"));
            context.getInput().setCursorPos(1, 1);
            context.waitTick();
            screenshot(context, "odc-settings-players-demo20-mixed");
        } finally {
            context.runOnClient(client -> {
                try {
                    var clear = PlaytimeClient.class.getDeclaredMethod("clear");
                    clear.setAccessible(true);
                    clear.invoke(null);
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not clear the test-only player demo", exception); }
                PlaytimeClient.config().worlds.remove(demoWorld.toString());
                PlaytimeClient.config().changed();
            });
            context.waitTicks(2);
        }
    }

    private static void onlineOfflineRuleRegression(ClientGameTestContext context) {
        UUID worldId = UUID.fromString("00000000-0000-0000-0000-00000000d003");
        var parentScreen = context.computeOnClient(client -> client.currentScreen);
        String[] names = { "ExampleFakeOnline", "ExampleFakeOffline", "120001", "120002",
            "Example_BLOCK_Online", "Example_block_Offline", "RuleTarget_Online_7", "RuleTarget_Offline_8",
            "KeepPlayerOnline", "KeepPlayerOffline" };
        List<PlaytimeSnapshot.Entry> source = new ArrayList<>();
        for (int index = 0; index < names.length; index++) source.add(new PlaytimeSnapshot.Entry(
            new UUID(0xEE00L, index + 1L), names[index], 1_000_000L + index * 1_000L, index % 2 == 0, index < 2));
        try {
            context.runOnClient(client -> {
                try {
                    var receive = PlaytimeClient.class.getDeclaredMethod("receive", PlaytimeSnapshot.class);
                    receive.setAccessible(true);
                    receive.invoke(null, new PlaytimeSnapshot(worldId, 1L, true, source));
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not inject paired online/offline rule examples", exception); }
                var filter = PlaytimeClient.config().filter(worldId);
                filter.showAll = true;
                filter.autoBlockRules = true;
                for (int rule = 0; rule < 4; rule++) {
                    filter.blockFakePlayers = rule == 0;
                    filter.blockNumericIds = rule == 1;
                    filter.blockedKeywords = rule == 2 ? "block" : "";
                    filter.blockedRegex = rule == 3 ? "^RuleTarget_(Online|Offline)_\\d+$" : "";
                    PlaytimeClient.config().changed();
                    String label = new String[] { "fake-player", "numeric-ID", "keyword", "regular-expression" }[rule];
                    assertRuleDisplay(source, Set.of(source.get(rule * 2).uuid(), source.get(rule * 2 + 1).uuid()), label);
                }
                filter.blockFakePlayers = true;
                filter.blockNumericIds = true;
                filter.blockedKeywords = "block";
                filter.blockedRegex = "^RuleTarget_(Online|Offline)_\\d+$";
                PlaytimeClient.config().changed();
                Set<UUID> allBlocked = source.subList(0, 8).stream().map(PlaytimeSnapshot.Entry::uuid)
                    .collect(java.util.stream.Collectors.toSet());
                assertRuleDisplay(source, allBlocked, "combined rules");
                require(PlaytimeClient.entries(true).size() == 2
                        && PlaytimeClient.entries(true).stream().filter(PlaytimeClient.DisplayEntry::online).count() == 1,
                    "Combining all four rules leaves one nonmatching online player and one nonmatching offline player");
                filter.autoBlockRules = false;
                PlaytimeClient.config().changed();
                assertRuleDisplay(source, Set.of(), "disabled rules");
                filter.autoBlockRules = true;
                filter.blockedRegex = ".*";
                PlaytimeClient.config().changed();
                assertRuleDisplay(source, source.stream().map(PlaytimeSnapshot.Entry::uuid)
                    .collect(java.util.stream.Collectors.toSet()), "all players matched");
                require(PlaytimeClient.entries(true).isEmpty(), "The HUD's actual filtered data is empty when every player matches a rule");
                client.setScreen(new PlaytimePositionScreen(parentScreen));
            });
            context.waitForScreen(PlaytimePositionScreen.class);
            requirePositionEntries(context, true);
            context.getInput().setCursorPos(1, 1);
            screenshot(context, "odc-position-editor-all-rules-hidden");
        } finally {
            context.runOnClient(client -> {
                try {
                    var clear = PlaytimeClient.class.getDeclaredMethod("clear");
                    clear.setAccessible(true);
                    clear.invoke(null);
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not clear the paired rule examples", exception); }
                PlaytimeClient.config().worlds.remove(worldId.toString());
                PlaytimeClient.config().changed();
                client.setScreen(parentScreen);
            });
            context.waitTicks(2);
        }
        context.setScreen(() -> new PlaytimePositionScreen(parentScreen));
        context.waitForScreen(PlaytimePositionScreen.class);
        requirePositionEntries(context, false);
        context.setScreen(() -> parentScreen);
    }

    private static void requirePositionEntries(ClientGameTestContext context, boolean empty) {
        context.runOnClient(client -> {
            require(client.currentScreen instanceof PlaytimePositionScreen, "The position editor is active for its data-source check");
            try {
                var method = PlaytimePositionScreen.class.getDeclaredMethod("entries");
                method.setAccessible(true);
                var entries = (List<?>) method.invoke(client.currentScreen);
                require(empty ? entries.isEmpty() : !entries.isEmpty(), empty
                    ? "A known snapshot with every player filtered out stays empty in the position editor"
                    : "The main menu without a snapshot can still use example players in the position editor");
            } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not inspect the position editor's actual player source", exception); }
        });
    }

    /** Called on the client thread; examples are paused so duration equality is exact. */
    private static void assertRuleDisplay(List<PlaytimeSnapshot.Entry> source, Set<UUID> excluded, String label) {
        var expected = source.stream().map(PlaytimeSnapshot.Entry::uuid).filter(uuid -> !excluded.contains(uuid))
            .collect(java.util.stream.Collectors.toSet());
        var displayed = PlaytimeClient.entries(true).stream().map(PlaytimeClient.DisplayEntry::uuid)
            .collect(java.util.stream.Collectors.toSet());
        require(displayed.equals(expected), label + " filters the exact matching online and offline UUIDs");
        require(PlaytimeClient.entries(false).size() == source.size() && PlaytimeClient.snapshot().entries().equals(source),
            label + " preserves every unfiltered entry and the complete incoming snapshot");
        for (var original : source) {
            var retained = PlaytimeClient.entries(false).stream().filter(entry -> entry.uuid().equals(original.uuid())).findFirst().orElseThrow();
            require(retained.totalMillis() == original.totalMillis() && retained.online() == original.online()
                    && retained.fakePlayer() == original.fakePlayer(),
                label + " keeps unfiltered duration and metadata intact for " + original.name());
        }
    }

    private static void identityOverridesRegression(ClientGameTestContext context) {
        UUID worldId = UUID.fromString("00000000-0000-0000-0000-00000000d004");
        UUID unknownId = new UUID(0xEF00L, 1), knownFakeId = new UUID(0xEF00L, 2), controlId = new UUID(0xEF00L, 3);
        var parentScreen = context.computeOnClient(client -> client.currentScreen);
        List<PlaytimeSnapshot.Entry> source = List.of(
            new PlaytimeSnapshot.Entry(unknownId, "UnknownHuman", 12_345_000L, false, false),
            new PlaytimeSnapshot.Entry(knownFakeId, "KnownPilot", 9_876_000L, false, true),
            new PlaytimeSnapshot.Entry(controlId, "ControlHuman", 3_456_000L, true, false));
        try {
            context.runOnClient(client -> {
                try {
                    var receive = PlaytimeClient.class.getDeclaredMethod("receive", PlaytimeSnapshot.class);
                    receive.setAccessible(true);
                    receive.invoke(null, new PlaytimeSnapshot(worldId, 1L, true, source));
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not inject offline identity examples", exception); }
                var filter = PlaytimeClient.config().filter(worldId);
                filter.showAll = true;
                filter.autoBlockRules = true;
                filter.blockFakePlayers = true;
                PlaytimeClient.config().changed();
                require(filter.identity(unknownId) == ClientConfig.IdentityOverride.AUTO,
                    "An offline player without prior evidence starts in automatic identity mode");
                assertRuleDisplay(source, Set.of(knownFakeId), "automatic offline identities");
                client.setScreen(new PlaytimeConfigScreen(null));
            });
            context.clickScreenButton("playtime_together.config.tab.players");
            clickIdentityButton(context, unknownId);
            context.runOnClient(client -> require(PlaytimeClient.config().filter(worldId).identity(unknownId) == ClientConfig.IdentityOverride.REAL,
                "The actual identity button cycles automatic to real"));
            clickIdentityButton(context, unknownId);
            context.runOnClient(client -> {
                var filter = PlaytimeClient.config().filter(worldId);
                require(filter.identity(unknownId) == ClientConfig.IdentityOverride.FAKE && filter.showAll,
                    "The actual identity button marks the unknown offline player fake without changing selection");
                assertRuleDisplay(source, Set.of(unknownId, knownFakeId), "manual offline fake identity");
            });
            context.getInput().setCursorPos(1, 1);
            screenshot(context, "odc-identity-offline-marked-fake");
            context.setScreen(() -> new PlaytimeConfigScreen(null));
            context.runOnClient(client -> {
                var reloaded = ClientConfig.load();
                require(reloaded.filter(worldId).identity(unknownId) == ClientConfig.IdentityOverride.FAKE
                        && !reloaded.includes(worldId, unknownId, "UnknownHuman", false),
                    "Closing and reloading settings retains the per-world offline fake override");
                require(reloaded.includes(new UUID(0xEF01L, 1), unknownId, "UnknownHuman", false),
                    "The same UUID in another world does not inherit this world's manual identity");
            });
            context.clickScreenButton("playtime_together.config.tab.players");
            clickIdentityButton(context, unknownId); // FAKE -> AUTO
            clickIdentityButton(context, unknownId); // AUTO -> REAL
            clickIdentityButton(context, knownFakeId); // AUTO -> REAL
            context.runOnClient(client -> {
                var filter = PlaytimeClient.config().filter(worldId);
                require(filter.identity(unknownId) == ClientConfig.IdentityOverride.REAL
                        && filter.identity(knownFakeId) == ClientConfig.IdentityOverride.REAL && filter.showAll,
                    "Both offline records can be explicitly treated as real without toggling their selection");
                assertRuleDisplay(source, Set.of(), "manual offline real identities");
                filter.blockedKeywords = "KnownPilot";
                PlaytimeClient.config().changed();
                assertRuleDisplay(source, Set.of(knownFakeId), "manual real identity with a matching keyword");
                filter.blockedKeywords = "";
                PlaytimeClient.config().changed();
                assertRuleDisplay(source, Set.of(), "manual real identity after removing the keyword");
            });
            context.getInput().setCursorPos(1, 1);
            screenshot(context, "odc-identity-offline-marked-real");
            context.setScreen(() -> new PlaytimeConfigScreen(null));
            context.runOnClient(client -> {
                var reloaded = ClientConfig.load();
                require(reloaded.filter(worldId).identity(knownFakeId) == ClientConfig.IdentityOverride.REAL
                        && reloaded.includes(worldId, knownFakeId, "KnownPilot", true),
                    "Reloaded manual real identity overrides only the fake-player rule, preserving the server's original flag");
            });
            context.clickScreenButton("playtime_together.config.tab.players");
            clickIdentityButton(context, unknownId); // REAL -> FAKE
            clickIdentityButton(context, unknownId); // FAKE -> AUTO
            clickIdentityButton(context, knownFakeId); // REAL -> FAKE
            clickIdentityButton(context, knownFakeId); // FAKE -> AUTO
            context.runOnClient(client -> {
                var filter = PlaytimeClient.config().filter(worldId);
                require(filter.identity(unknownId) == ClientConfig.IdentityOverride.AUTO
                        && filter.identity(knownFakeId) == ClientConfig.IdentityOverride.AUTO,
                    "Actual identity-button clicks can return both offline records to automatic classification");
                assertRuleDisplay(source, Set.of(knownFakeId), "restored automatic offline identities");
            });
            context.setScreen(() -> new PlaytimeConfigScreen(null));
            context.runOnClient(client -> require(ClientConfig.load().filter(worldId).identity(unknownId) == ClientConfig.IdentityOverride.AUTO
                    && ClientConfig.load().filter(worldId).identity(knownFakeId) == ClientConfig.IdentityOverride.AUTO,
                "Returning to automatic identity is also saved and reloaded"));
        } finally {
            context.runOnClient(client -> {
                try {
                    var clear = PlaytimeClient.class.getDeclaredMethod("clear");
                    clear.setAccessible(true);
                    clear.invoke(null);
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not clear offline identity examples", exception); }
                PlaytimeClient.config().worlds.remove(worldId.toString());
                PlaytimeClient.config().changed();
                client.setScreen(parentScreen);
            });
            context.waitTicks(2);
        }
    }

    private static void clickIdentityButton(ClientGameTestContext context, UUID playerId) {
        double[] point = context.computeOnClient(client -> client.currentScreen.children().stream()
            .filter(PlaytimeConfigScreen.IdentityButton.class::isInstance).map(PlaytimeConfigScreen.IdentityButton.class::cast)
            .filter(button -> button.visible && button.active && button.playerId().equals(playerId))
            .map(button -> new double[] {
                (button.getX() + button.getWidth() / 2.0) * client.getWindow().getWidth() / client.getWindow().getScaledWidth(),
                (button.getY() + button.getHeight() / 2.0) * client.getWindow().getHeight() / client.getWindow().getScaledHeight()
            }).findFirst().orElseThrow());
        context.getInput().setCursorPos(point[0], point[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void colorRegression(ClientGameTestContext context) {
        int[] colors = { 0xCCBBAA, 0xAAAACC, 0xAACCBB, 0xBBAACC };
        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            config.width = 200;
            config.height = 200;
            config.fontScale = 1.0;
            config.backgroundOpacity = 100;
            config.changed();
            client.setScreen(new PlaytimeColorsScreen(null));
        });
        context.waitForScreen(PlaytimeColorsScreen.class);
        String[] keys = { "title", "subtitle", "online", "offline" };
        for (int i = 0; i < keys.length; i++) setTextField(context, "playtime_together.colors." + keys[i], String.format("#%06X", colors[i]));
        screenshot(context, "odc-settings-colors-edited");
        context.setScreen(() -> new PlaytimePreviewScreen(null));
        context.runOnClient(client -> {
            var loaded = ClientConfig.load();
            require(loaded.titleColor == colors[0] && loaded.subtitleColor == colors[1]
                    && loaded.onlineColor == colors[2] && loaded.offlineColor == colors[3],
                "All four custom HUD colors persist independently");
        });
        Path custom = screenshot(context, "odc-preview-custom-four-colors");
        try {
            var image = ImageIO.read(custom.toFile());
            for (int color : colors) {
                boolean found = false;
                for (int y = 0; y < image.getHeight() && !found; y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) & 0xFFFFFF) == color) { found = true; break; }
                    }
                }
                require(found, "The rendered preview uses custom text color #" + Integer.toHexString(color));
            }
        } catch (IOException exception) { throw new AssertionError("Could not inspect the custom color preview", exception); }
        context.setScreen(() -> new PlaytimeColorsScreen(null));
        context.waitForScreen(PlaytimeColorsScreen.class);
        clickVisibleButton(context, "playtime_together.colors.defaults");
        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            require(config.titleColor == 0xF1F1F1 && config.subtitleColor == 0xB5B5B5
                    && config.onlineColor == 0x9DB79B && config.offlineColor == 0x909090,
                "The color-only reset restores all four neutral default colors");
            require(config.width == 200 && config.height == 200 && config.fontScale == 1.0,
                "Resetting colors preserves the chosen dimensions and text size");
            config.save();
        });
        context.setScreen(() -> new PlaytimePreviewScreen(null));
    }

    private static void singleplayerLifecycle(ClientGameTestContext context) {
        TestWorldSave save;
        SessionBaseline baseline;
        UUID carpetFake = null;
        PlaytimeSnapshot.Entry realIdentityHistory = null;
        QbCheckpoint checkpoint = null;
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            save = singleplayer.getWorldSave();
            context.waitFor(client -> PlaytimeClient.snapshot() != null && !PlaytimeClient.snapshot().entries().isEmpty());
            baseline = context.computeOnClient(client -> {
                require(client.player != null, "The test player joined the integrated server");
                var snapshot = PlaytimeClient.snapshot();
                var own = snapshot.entries().stream().filter(entry -> entry.uuid().equals(client.player.getUuid())).findFirst().orElseThrow();
                require(own.online(), "The server snapshot marks the test player online");
                require(!snapshot.paused(), "The open world starts unpaused");
                require(PlaytimeClient.config().timeFormat == DurationFormat.HOURS_MINUTES, "The default format is hours and minutes");
                return new SessionBaseline(snapshot.worldId(), own.uuid(), own.totalMillis());
            });
            screenshot(context, "odc-world-hud-default");
            keyboardBindingRegression(context);
            rulesRegression(context, baseline.playerId);
            if (FabricLoader.getInstance().isModLoaded("carpet")) {
                carpetFake = carpetFakeRegression(context, singleplayer.getServer());
                realIdentityHistory = realThenCarpetIdentityRegression(context, singleplayer.getServer());
            }
            positionRegression(context);
            context.runOnClient(client -> {
                PlaytimeClient.config().backgroundOpacity = 0;
                PlaytimeClient.config().changed();
            });
            screenshot(context, "odc-world-hud-opacity-0");
            context.runOnClient(client -> {
                PlaytimeClient.config().backgroundOpacity = 100;
                PlaytimeClient.config().changed();
            });
            screenshot(context, "odc-world-hud-opacity-100");
            context.runOnClient(client -> client.setScreen(new GameMenuScreen(true)));
            context.waitFor(client -> PlaytimeClient.snapshot() != null && PlaytimeClient.snapshot().paused());
            long pausedMillis = context.computeOnClient(client -> ownMillis(baseline.playerId));
            context.waitTicks(25);
            context.runOnClient(client -> {
                require(PlaytimeClient.snapshot().paused(), "The integrated server advertises the paused state");
                require(ownMillis(baseline.playerId) == pausedMillis, "The displayed online duration remains frozen while paused");
            });
            screenshot(context, "odc-world-paused");
            context.setScreen(() -> null);
            context.waitFor(client -> PlaytimeClient.snapshot() != null && !PlaytimeClient.snapshot().paused());
            if (FabricLoader.getInstance().isModLoaded("quickbackupmulti_reforged")) {
                checkpoint = qbCheckpoint(context, singleplayer.getServer(), save, baseline.playerId);
                long playUntil = System.nanoTime() + 2_000_000_000L;
                context.waitFor(client -> System.nanoTime() >= playUntil, 2400);
            }
        }
        context.waitFor(client -> client.world == null && PlaytimeClient.snapshot() == null);
        require(Files.isRegularFile(save.getSaveDirectory().resolve("data/odc-online-duration.json")), "Closing the world persisted the ODC ledger");
        Path liveLedger = save.getSaveDirectory().resolve("data/odc-online-duration.json");
        long closedTotal = readLedger(liveLedger).players().stream()
            .filter(entry -> entry.uuid().equals(baseline.playerId)).findFirst().orElseThrow().totalMillis();
        if (carpetFake != null) require(Boolean.TRUE.equals(readLedger(liveLedger).fakePlayers().get(carpetFake)),
            "Closing the world persists the real Carpet player's fake metadata beside the time ledger");
        if (realIdentityHistory != null) require(readLedger(liveLedger).realPlayers().contains(realIdentityHistory.uuid()),
            "Closing the world persists the distinct UUID's previous real-player identity evidence");
        if (checkpoint != null) {
            require(closedTotal > checkpoint.totalMillis, "The world accumulated future time after its real QB backup");
            try {
                // Reproduce QB's close -> restore files -> reopen sequence using the real exported backup.
                // This deliberately does not invoke QB's automatic screen/reconnect workflow.
                Files.copy(checkpoint.ledgerFile, liveLedger, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(liveLedger.resolveSibling(liveLedger.getFileName() + ".bak"));
            } catch (IOException exception) {
                throw new AssertionError("Could not restore the exported QB ledger into the closed test world", exception);
            }
        }
        QbCheckpoint restoredCheckpoint = checkpoint;
        UUID savedCarpetFake = carpetFake;
        PlaytimeSnapshot.Entry savedRealIdentity = realIdentityHistory;

        try (TestSingleplayerContext reopened = save.open()) {
            context.waitFor(client -> PlaytimeClient.snapshot() != null && !PlaytimeClient.snapshot().entries().isEmpty());
            context.runOnClient(client -> {
                var snapshot = PlaytimeClient.snapshot();
                require(snapshot.worldId().equals(baseline.worldId), "Reopening restores the same world identity");
                var own = snapshot.entries().stream().filter(entry -> entry.uuid().equals(baseline.playerId)).findFirst().orElseThrow();
                require(own.online(), "Reopening starts a new online session");
                require(own.totalMillis() >= baseline.totalMillis, "Reopening retains the previously saved playtime");
                if (savedCarpetFake != null) require(snapshot.entries().stream().anyMatch(entry -> entry.uuid().equals(savedCarpetFake)
                        && entry.fakePlayer() && !entry.online()),
                    "Reopening the world retains the real Carpet player's offline history and fake flag");
                if (savedRealIdentity != null) require(snapshot.entries().stream().anyMatch(entry -> entry.uuid().equals(savedRealIdentity.uuid())
                        && !entry.online() && !entry.fakePlayer() && entry.totalMillis() == savedRealIdentity.totalMillis()),
                    "Reopening or QB restoration preserves real-player protection and the same finalized duration for the mixed-history UUID");
                if (restoredCheckpoint != null) {
                    require(snapshot.worldId().equals(restoredCheckpoint.worldId), "The real QB backup retains the world's identity");
                    require(own.totalMillis() >= restoredCheckpoint.totalMillis && own.totalMillis() < closedTotal,
                        "Reopening uses the real QB checkpoint rather than retaining future playtime");
                }
            });
            screenshot(context, "odc-world-reopened");
        }
        context.waitFor(client -> client.world == null && PlaytimeClient.snapshot() == null);
        context.runOnClient(client -> {
            PlaytimeClient.config().restoreDisplayDefaults();
            PlaytimeClient.config().save();
        });
    }

    private static long ownMillis(UUID playerId) {
        return PlaytimeClient.entries(false).stream().filter(entry -> entry.uuid().equals(playerId)).findFirst().orElseThrow().totalMillis();
    }

    private static void rulesRegression(ClientGameTestContext context, UUID playerId) {
        UUID worldId = context.computeOnClient(client -> PlaytimeClient.snapshot().worldId());
        String name = context.computeOnClient(client -> client.player.getName().getString());
        context.setScreen(() -> new PlaytimeConfigScreen(null));
        context.clickScreenButton("playtime_together.config.tab.players");
        screenshot(context, "odc-settings-players-world");
        clickVisibleButton(context, "playtime_together.config.select_none");
        context.runOnClient(client -> require(PlaytimeClient.entries(true).isEmpty(), "Select none hides the known player locally"));
        clickVisibleButton(context, "playtime_together.config.select_inverse");
        context.runOnClient(client -> require(PlaytimeClient.entries(true).stream().anyMatch(entry -> entry.uuid().equals(playerId)),
            "Invert selection restores the previously unselected player"));
        clickVisibleButton(context, "playtime_together.config.select_all");
        clickVisibleButton(context, "playtime_together.config.rules");
        context.waitForScreen(PlaytimeRulesScreen.class);
        setTextField(context, "playtime_together.rules.keywords", name.toUpperCase(java.util.Locale.ROOT));
        context.runOnClient(client -> {
            require(PlaytimeClient.entries(true).stream().noneMatch(entry -> entry.uuid().equals(playerId)),
                "Typing a case-insensitive keyword immediately filters the matching player");
            require(PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(playerId) && entry.online()),
                "Display rules leave the authoritative online record intact");
        });
        setTextField(context, "playtime_together.rules.regex", "[");
        context.runOnClient(client -> require(PlaytimeClient.config().filter(worldId).regexError() != null,
            "An invalid regular expression is reported without crashing or discarding other matching rules"));
        screenshot(context, "odc-rules-invalid-regex");
        setTextField(context, "playtime_together.rules.regex", "^__ODC_NO_MATCH__$");
        clickVisibleButton(context, "playtime_together.rules.numeric_ids");
        clickVisibleButton(context, "playtime_together.rules.fake_players");
        context.runOnClient(client -> {
            var filter = PlaytimeClient.config().filter(worldId);
            require(filter.regexError() == null && filter.blockNumericIds && filter.blockFakePlayers,
                "The valid regex and both special rule switches update the current world's rules");
            require(filter.matches("123456", false) && filter.matches("OtherHumanName", true),
                "Numeric IDs and server-provided fake flags participate in matching");
        });
        clickVisibleButton(context, "playtime_together.rules.auto_block");
        context.runOnClient(client -> require(PlaytimeClient.entries(true).stream().anyMatch(entry -> entry.uuid().equals(playerId)),
            "Turning off automatic blocking leaves the manual selection visible"));
        clickVisibleButton(context, "playtime_together.rules.select_matching");
        context.runOnClient(client -> require(PlaytimeClient.config().filter(worldId).selected.contains(playerId.toString()),
            "Selecting matching players applies the rules to the manual whitelist"));
        clickVisibleButton(context, "playtime_together.rules.deselect_matching");
        context.runOnClient(client -> require(!PlaytimeClient.config().filter(worldId).selected.contains(playerId.toString()),
            "Deselecting matching players removes them from the manual whitelist"));
        screenshot(context, "odc-rules-selection-applied");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            PlaytimeClient.config().worlds.remove(worldId.toString());
            PlaytimeClient.config().changed();
            PlaytimeClient.config().save();
        });
    }

    private static UUID carpetFakeRegression(ClientGameTestContext context, TestServerContext serverContext) {
        UUID fakeId = createCarpetFake(serverContext);
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(fakeId)
            && entry.fakePlayer() && entry.online()));
        context.runOnClient(client -> {
            var filter = PlaytimeClient.config().filter(PlaytimeClient.snapshot().worldId());
            filter.blockFakePlayers = true;
            PlaytimeClient.config().changed();
            require(PlaytimeClient.entries(true).stream().noneMatch(entry -> entry.uuid().equals(fakeId)),
                "The fake-player rule filters a real non-numeric Carpet player using server metadata");
            require(PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(fakeId)),
                "Filtering the real Carpet player retains its server record");
        });
        var hiddenBefore = authoritativeEntry(serverContext, fakeId);
        waitRealSecond(context);
        var hiddenAfter = authoritativeEntry(serverContext, fakeId);
        require(hiddenBefore.online() && hiddenAfter.online() && hiddenAfter.fakePlayer()
                && hiddenAfter.totalMillis() >= hiddenBefore.totalMillis() + 1_000L,
            "The authoritative server keeps accumulating the hidden real Carpet player's online time");
        context.runOnClient(client -> {
            require(PlaytimeClient.entries(true).stream().noneMatch(entry -> entry.uuid().equals(fakeId)),
                "The growing real Carpet record stays hidden until the local rule is disabled");
        });
        removeCarpetFake(serverContext, fakeId);
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(fakeId)
            && entry.fakePlayer() && !entry.online()));
        var offlineBefore = authoritativeEntry(serverContext, fakeId);
        context.runOnClient(client -> require(PlaytimeClient.config().filter(PlaytimeClient.snapshot().worldId()).blockFakePlayers
                && PlaytimeClient.entries(true).stream().noneMatch(entry -> entry.uuid().equals(fakeId)),
            "The still-enabled fake-player rule also hides the real Carpet record immediately after it goes offline"));
        waitRealSecond(context);
        var offlineAfter = authoritativeEntry(serverContext, fakeId);
        require(!offlineBefore.online() && !offlineAfter.online() && offlineAfter.fakePlayer()
                && offlineAfter.totalMillis() == offlineBefore.totalMillis(),
            "The real Carpet player's authoritative total stays exactly frozen for a second after kill");
        context.runOnClient(client -> {
            require(PlaytimeClient.entries(true).stream().noneMatch(entry -> entry.uuid().equals(fakeId)),
                "The offline real Carpet record stays hidden throughout the freeze check");
            require(PlaytimeClient.entries(false).stream().anyMatch(entry -> entry.uuid().equals(fakeId)
                    && !entry.online() && entry.totalMillis() == offlineAfter.totalMillis()),
                "The complete client cache retains the finalized frozen offline Carpet total");
            var filter = PlaytimeClient.config().filter(PlaytimeClient.snapshot().worldId());
            filter.blockFakePlayers = false;
            PlaytimeClient.config().changed();
            require(PlaytimeClient.entries(true).stream().anyMatch(entry -> entry.uuid().equals(fakeId)
                    && !entry.online() && entry.fakePlayer() && entry.totalMillis() == offlineAfter.totalMillis()),
                "Disabling the rule reveals the same retained offline Carpet record and exact finalized total");
        });

        require(createCarpetFake(serverContext).equals(fakeId), "The real Carpet player reconnects with the same UUID");
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(fakeId)
            && entry.fakePlayer() && entry.online()));
        var resumedBefore = authoritativeEntry(serverContext, fakeId);
        require(resumedBefore.online() && resumedBefore.totalMillis() >= offlineAfter.totalMillis(),
            "Reconnecting a real Carpet player preserves the previously finalized total");
        waitRealSecond(context);
        var resumedAfter = authoritativeEntry(serverContext, fakeId);
        require(resumedAfter.online() && resumedAfter.fakePlayer() && resumedAfter.totalMillis() >= resumedBefore.totalMillis() + 1_000L,
            "The same UUID continues accumulating from its saved total after reconnecting");
        removeCarpetFake(serverContext, fakeId);
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(fakeId)
            && entry.fakePlayer() && !entry.online()));
        var finalized = authoritativeEntry(serverContext, fakeId);
        require(!finalized.online() && finalized.fakePlayer() && finalized.totalMillis() >= resumedAfter.totalMillis(),
            "The second real Carpet departure finalizes the resumed total and retains the offline record");
        return fakeId;
    }

    private static PlaytimeSnapshot.Entry realThenCarpetIdentityRegression(ClientGameTestContext context, TestServerContext serverContext) {
        String name = "ODCTestHuman";
        UUID realId = serverContext.computeOnServer(server -> {
            try {
                var profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
                // A distinct base player entity with an in-memory test connection; the local host UUID is never reused.
                var real = new ServerPlayerEntity(server, server.getOverworld(), profile, SyncedClientOptions.createDefault());
                require(real.getClass() == ServerPlayerEntity.class, "The real-history sample is a normal player entity rather than a Carpet fake");
                server.getPlayerManager().onPlayerConnect(carpetTestConnection(), real, ConnectedClientData.createDefault(profile, false));
                return real.getUuid();
            } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not connect the independent real-entity identity sample", exception); }
        });
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(realId)
            && entry.online() && !entry.fakePlayer()));
        context.runOnClient(client -> {
            require(!realId.equals(client.player.getUuid()), "The mixed-history sample cannot replace or disconnect the actual test host");
            var filter = PlaytimeClient.config().filter(PlaytimeClient.snapshot().worldId());
            filter.blockFakePlayers = true;
            PlaytimeClient.config().changed();
            require(PlaytimeClient.entries(true).stream().anyMatch(entry -> entry.uuid().equals(realId)),
                "The fake-player rule leaves the independent real entity visible");
        });
        serverContext.runOnServer(server -> {
            var real = server.getPlayerManager().getPlayer(realId);
            require(real != null && real.getClass() == ServerPlayerEntity.class, "The base real entity is present before normal manager removal");
            server.getPlayerManager().remove(real);
        });
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(realId)
            && !entry.online() && !entry.fakePlayer()));
        var previousReal = authoritativeEntry(serverContext, realId);
        require(createCarpetFake(serverContext, name).equals(realId), "Carpet reconnects using the independent real entity's exact UUID");
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(realId)
            && entry.online() && entry.fakePlayer()));
        context.runOnClient(client -> require(PlaytimeClient.entries(true).stream().noneMatch(entry -> entry.uuid().equals(realId)),
            "While Carpet is connected, current fake identity still hides the mixed-history UUID"));
        var connectedFake = authoritativeEntry(serverContext, realId);
        require(connectedFake.online() && connectedFake.fakePlayer() && connectedFake.totalMillis() >= previousReal.totalMillis(),
            "The temporary fake session keeps the same UUID's real-session accumulated duration");
        waitRealSecond(context);
        removeCarpetFake(serverContext, realId);
        context.waitFor(client -> PlaytimeClient.snapshot().entries().stream().anyMatch(entry -> entry.uuid().equals(realId)
            && !entry.online() && !entry.fakePlayer()));
        var finalized = authoritativeEntry(serverContext, realId);
        require(!finalized.online() && !finalized.fakePlayer() && finalized.totalMillis() >= connectedFake.totalMillis() + 1_000L,
            "After Carpet leaves, the UUID's real-player evidence protects its offline identity without losing elapsed time");
        context.runOnClient(client -> {
            require(PlaytimeClient.entries(true).stream().anyMatch(entry -> entry.uuid().equals(realId)
                    && !entry.online() && !entry.fakePlayer() && entry.totalMillis() == finalized.totalMillis()),
                "The still-enabled fake-player rule allows the protected offline real UUID and its finalized duration");
            PlaytimeClient.config().filter(PlaytimeClient.snapshot().worldId()).blockFakePlayers = false;
            PlaytimeClient.config().changed();
        });
        return finalized;
    }

    private static UUID createCarpetFake(TestServerContext serverContext) {
        return createCarpetFake(serverContext, "ODCTestBot");
    }

    private static UUID createCarpetFake(TestServerContext serverContext, String name) {
        return serverContext.computeOnServer(server -> {
            try {
                var profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
                Class<?> fakeClass = Class.forName("carpet.patches.EntityPlayerMPFake");
                require(fakeClass.getSuperclass() == ServerPlayerEntity.class, "The integration test loaded the actual Carpet player class");
                var fake = (ServerPlayerEntity) fakeClass.getMethod("respawnFake", MinecraftServer.class, ServerWorld.class,
                    GameProfile.class, SyncedClientOptions.class).invoke(null, server, server.getOverworld(), profile, SyncedClientOptions.createDefault());
                server.getPlayerManager().onPlayerConnect(carpetTestConnection(), fake, ConnectedClientData.createDefault(profile, false));
                return fake.getUuid();
            } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not create the real offline Carpet test player", exception); }
        });
    }

    private static ClientConnection carpetTestConnection() throws ReflectiveOperationException {
        return (ClientConnection) Class.forName("carpet.patches.FakeClientConnection")
            .getConstructor(NetworkSide.class).newInstance(NetworkSide.SERVERBOUND);
    }

    private static void removeCarpetFake(TestServerContext serverContext, UUID fakeId) {
        serverContext.runOnServer(server -> {
            var fake = server.getPlayerManager().getPlayer(fakeId);
            require(fake != null, "The real Carpet player is still on the server");
            try { fake.getClass().getMethod("kill", Text.class).invoke(fake, Text.literal("ODC integration test complete")); }
            catch (ReflectiveOperationException exception) { throw new AssertionError("Could not remove the real Carpet player through its normal lifecycle", exception); }
        });
    }

    private static PlaytimeSnapshot.Entry authoritativeEntry(TestServerContext serverContext, UUID playerId) {
        return serverContext.computeOnServer(server -> {
            var service = PlaytimeService.get(server);
            require(service != null, "The real test server has an authoritative playtime service");
            try {
                // Test-only observation on the server thread; no network/broadcast or production API change.
                var snapshotMethod = PlaytimeService.class.getDeclaredMethod("snapshot");
                snapshotMethod.setAccessible(true);
                var snapshot = (PlaytimeSnapshot) snapshotMethod.invoke(service);
                return snapshot.entries().stream().filter(entry -> entry.uuid().equals(playerId)).findFirst().orElseThrow();
            } catch (ReflectiveOperationException exception) { throw new AssertionError("Could not inspect the authoritative test-server snapshot", exception); }
        });
    }

    private static void waitRealSecond(ClientGameTestContext context) {
        long deadline = System.nanoTime() + 1_100_000_000L;
        context.waitFor(client -> System.nanoTime() >= deadline, 2400);
    }

    private static void setTextField(ClientGameTestContext context, String key, String value) {
        TextFieldWidget field = null;
        var input = context.getInput();
        context.waitTicks(2);
        for (int attempt = 0; attempt < 12 && field == null; attempt++) {
            field = context.computeOnClient(client -> client.currentScreen.children().stream()
                .filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast)
                .filter(widget -> widget.visible && widget.active)
                .filter(widget -> widget.getMessage().getContent() instanceof TranslatableTextContent label && label.getKey().equals(key))
                .findFirst().orElse(null));
            if (field == null) {
                double[] center = context.computeOnClient(client ->
                    new double[] { client.getWindow().getWidth() / 2.0, client.getWindow().getHeight() / 2.0 });
                input.setCursorPos(center[0], center[1]); input.scroll(-2); context.waitTick();
            }
        }
        require(field != null, "The labeled text field is reachable: " + key);
        TextFieldWidget target = field;
        double[] center = context.computeOnClient(client -> new double[] {
            (target.getX() + target.getWidth() / 2.0) * client.getWindow().getWidth() / client.getWindow().getScaledWidth(),
            (target.getY() + target.getHeight() / 2.0) * client.getWindow().getHeight() / client.getWindow().getScaledHeight()
        });
        input.setCursorPos(center[0], center[1]);
        for (int attempt = 0; attempt < 3; attempt++) {
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            if (context.computeOnClient(client -> target.isFocused())) break;
        }
        context.runOnClient(client -> require(target.isFocused(), "The actual mouse click focused the text field: " + key));
        int previousLength = context.computeOnClient(client -> target.getText().length());
        // Fabric's 1.21.11 TestInput sends KeyInput(modifiers=0), including while Control/Shift is held.
        // Use unmodified real key events instead of depending on an unsupported Ctrl+A chord.
        input.pressKey(GLFW.GLFW_KEY_HOME);
        for (int index = 0; index < previousLength; index++) input.pressKey(GLFW.GLFW_KEY_DELETE);
        context.runOnClient(client -> require(target.getText().isEmpty(),
            "Home and actual Delete key events cleared the previous field value: " + key + " (remaining: " + target.getText() + ")"));
        input.typeChars(value);
        context.waitTick();
        context.runOnClient(client -> require(target.getText().equals(value),
            "The actual text events entered the complete requested value: " + key + " (actual: " + target.getText() + ")"));
    }

    private static void keyboardBindingRegression(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var key = PlaytimeClient.configurationKey();
            key.setBoundKey(key.getDefaultKey());
            KeyBinding.updateKeysByCode();
        });
        context.setScreen(() -> null);
        var input = context.getInput();
        input.pressKey(GLFW.GLFW_KEY_Y);
        context.waitForScreen(PlaytimeConfigScreen.class);
        clickVisibleButton(context, "playtime_together.config.open_config_key");
        input.pressKey(GLFW.GLFW_KEY_F8);
        context.runOnClient(client -> {
            require(PlaytimeClient.configurationKey().getBoundKeyTranslationKey().equals("key.keyboard.f8"),
                "The settings button captures the actual F8 key input");
            require(client.currentScreen instanceof PlaytimeConfigScreen && PlaytimeClient.config().enabled,
                "Capturing the configuration key keeps the current screen and sidebar enabled state");
        });
        screenshot(context, "odc-settings-key-bound-f8");
        context.setScreen(() -> null);
        input.pressKey(GLFW.GLFW_KEY_F8);
        context.waitForScreen(PlaytimeConfigScreen.class);
        context.runOnClient(client -> require(PlaytimeClient.config().enabled && ClientConfig.load().enabled,
            "The real rebound F8 key opens configuration without toggling sidebar visibility"));
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            var binding = PlaytimeClient.configurationKey();
            binding.setBoundKey(binding.getDefaultKey());
            binding.setPressed(false);
            while (binding.wasPressed()) { }
            KeyBinding.updateKeysByCode();
            client.options.write();
        });
    }

    private static void clickVisibleButton(ClientGameTestContext context, String key) {
        var input = context.getInput();
        double[] button = null;
        for (int attempt = 0; attempt < 12 && button == null; attempt++) {
            button = context.computeOnClient(client -> client.currentScreen.children().stream()
                .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(widget -> widget.visible && widget.active)
                .filter(widget -> widget.getMessage().getContent() instanceof TranslatableTextContent content
                    && content.getKey().equals(key))
                .map(widget -> new double[] {
                    (widget.getX() + widget.getWidth() / 2.0) * client.getWindow().getWidth() / client.getWindow().getScaledWidth(),
                    (widget.getY() + widget.getHeight() / 2.0) * client.getWindow().getHeight() / client.getWindow().getScaledHeight()
                }).findFirst().orElse(null));
            if (button == null) {
                double[] center = context.computeOnClient(client ->
                    new double[] { client.getWindow().getWidth() / 2.0, client.getWindow().getHeight() / 2.0 });
                input.setCursorPos(center[0], center[1]);
                input.scroll(-2);
                context.waitTick();
            }
        }
        require(button != null, "The settings button is reachable by scrolling: " + key);
        input.setCursorPos(button[0], button[1]);
        input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void positionRegression(ClientGameTestContext context) {
        String[] corners = { "top-left", "top-right", "bottom-left", "bottom-right" };
        for (int corner = 0; corner < corners.length; corner++) {
            double x = corner % 2;
            double y = corner / 2;
            context.runOnClient(client -> {
                var config = PlaytimeClient.config();
                config.restoreDisplayDefaults();
                config.backgroundOpacity = 100;
                config.positionX = x;
                config.positionY = y;
                config.changed();
            });
            var bounds = checkedWorldBounds(context);
            var image = screenshot(context, "odc-world-hud-" + corners[corner]);
            requirePanelDrawn(context, image, bounds);
        }
        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            config.width = 1_200;
            config.height = 1_200;
            config.fontScale = 3.0;
            config.changed();
        });
        var oversizedBounds = checkedWorldBounds(context);
        requirePanelDrawn(context, screenshot(context, "odc-world-hud-oversized-clamped"), oversizedBounds);

        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            config.restoreDisplayDefaults();
            config.positionX = 0.5;
            config.positionY = 0.5;
            config.changed();
            client.setScreen(new PlaytimePositionScreen(null));
        });
        context.waitForScreen(PlaytimePositionScreen.class);
        var start = checkedWorldBounds(context);
        double[] cursor = context.computeOnClient(client -> {
            var window = client.getWindow();
            return new double[] {
                (start.x() + start.width() / 2.0) * window.getWidth() / window.getScaledWidth(),
                (start.y() + start.height() / 2.0) * window.getHeight() / window.getScaledHeight(),
                (window.getScaledWidth() - 1.0) * window.getWidth() / window.getScaledWidth(),
                1.0 * window.getHeight() / window.getScaledHeight()
            };
        });
        var input = context.getInput();
        input.setCursorPos(cursor[0], cursor[1]);
        input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
        input.setCursorPos(cursor[2], cursor[3]);
        context.waitTicks(2);
        input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
        context.runOnClient(client -> require(PlaytimeClient.config().positionX > 0.95
                && PlaytimeClient.config().positionY < 0.05,
            "Dragging the panel to the upper-right corner changes its normalized coordinates"));
        screenshot(context, "odc-position-editor-dragged");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            var loaded = ClientConfig.load();
            require(loaded.positionX > 0.95 && loaded.positionY < 0.05,
                "Closing the position editor persists the dragged location");
            PlaytimeClient.config().restoreDisplayDefaults();
        });
        resizeRegression(context);
    }

    private static void resizeRegression(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var config = PlaytimeClient.config();
            config.width = 100;
            config.height = 142;
            config.fontScale = 0.8;
            config.positionX = 0.2;
            config.positionY = 0.2;
            config.changed();
            client.setScreen(new PlaytimePositionScreen(null));
        });
        context.waitForScreen(PlaytimePositionScreen.class);
        var frame = editorFrame(context);
        require(frame.height() == 142, "The edit frame shows the full configured 142-pixel maximum height");
        dragOnScreen(context, frame.x() + frame.width() - 1, frame.y() + frame.height() / 2.0, 20, 0);
        context.runOnClient(client -> require(PlaytimeClient.config().width == 120 && PlaytimeClient.config().height == 142,
            "Dragging the right edge changes only the HUD width"));
        frame = editorFrame(context);
        dragOnScreen(context, frame.x() + frame.width() / 2.0, frame.y() + frame.height() - 1, 0, 20);
        context.runOnClient(client -> require(PlaytimeClient.config().height == 162,
            "The empty maximum-height frame can be resized without changing automatic content height"));
        frame = editorFrame(context);
        dragOnScreen(context, frame.x(), frame.y(), 8, 8);
        context.runOnClient(client -> require(PlaytimeClient.config().width == 112 && PlaytimeClient.config().height == 154,
            "Dragging the upper-left corner resizes both axes from a stable original frame"));
        screenshot(context, "odc-position-editor-edge-corner-resized");
        frame = editorFrame(context);
        dragOnScreen(context, frame.x() + frame.width() - 1, frame.y() + frame.height() - 1, 9_999, 9_999);
        checkedWorldBounds(context);
        context.runOnClient(client -> require(PlaytimeClient.config().width <= client.getWindow().getScaledWidth()
                && PlaytimeClient.config().height <= client.getWindow().getScaledHeight(),
            "Corner resizing is limited to the actual edit viewport"));
        frame = editorFrame(context);
        dragOnScreen(context, frame.x() + frame.width() - 1, frame.y() + frame.height() - 1, -9_999, -9_999);
        context.runOnClient(client -> require(PlaytimeClient.config().width == 60 && PlaytimeClient.config().height == 40,
            "Dragging through the opposite edges clamps to the minimum supported dimensions"));
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            var loaded = ClientConfig.load();
            require(loaded.width == 60 && loaded.height == 40, "The resized maximum dimensions persist when the editor closes");
            PlaytimeClient.config().restoreDisplayDefaults();
        });
    }

    private static PlaytimePositionScreen.ResizeFrame editorFrame(ClientGameTestContext context) {
        var bounds = checkedWorldBounds(context);
        return context.computeOnClient(client -> PlaytimePositionScreen.resizeFrame(
            client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), bounds));
    }

    private static void dragOnScreen(ClientGameTestContext context, double x, double y, double dx, double dy) {
        double[] coordinates = context.computeOnClient(client -> {
            var window = client.getWindow();
            double ratioX = window.getWidth() / (double) window.getScaledWidth();
            double ratioY = window.getHeight() / (double) window.getScaledHeight();
            return new double[] { x * ratioX, y * ratioY, (x + dx) * ratioX, (y + dy) * ratioY };
        });
        var input = context.getInput();
        input.setCursorPos(coordinates[0], coordinates[1]);
        input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
        input.setCursorPos(coordinates[2], coordinates[3]);
        context.waitTicks(2);
        input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    private static PlaytimeHud.PanelBounds checkedWorldBounds(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            var window = client.getWindow();
            var bounds = PlaytimeHud.bounds(window.getScaledWidth(), window.getScaledHeight(), PlaytimeClient.config(),
                PlaytimeClient.entries(true), PlaytimeClient.snapshot().paused());
            require(bounds.x() >= 0 && bounds.y() >= 0 && bounds.width() > 0 && bounds.height() > 0
                    && bounds.x() + bounds.width() <= window.getScaledWidth()
                    && bounds.y() + bounds.height() <= window.getScaledHeight(),
                "The real world HUD panel remains inside the scaled screen bounds");
            return bounds;
        });
    }

    private static void requirePanelDrawn(ClientGameTestContext context, Path file, PlaytimeHud.PanelBounds bounds) {
        int[] scaledWindow = context.computeOnClient(client ->
            new int[] { client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight() });
        try {
            var image = ImageIO.read(file.toFile());
            int left = Math.clamp((int) Math.floor((bounds.x() + 1.0) * image.getWidth() / scaledWindow[0]), 0, image.getWidth() - 1);
            int top = Math.clamp((int) Math.floor((bounds.y() + 1.0) * image.getHeight() / scaledWindow[1]), 0, image.getHeight() - 1);
            int rgb = image.getRGB(left, top);
            int red = (rgb >>> 16) & 255;
            int green = (rgb >>> 8) & 255;
            int blue = rgb & 255;
            require(red <= 40 && green <= 40 && blue <= 40 && red == green && green == blue,
                "The opaque HUD background is drawn at the calculated corner rather than outside the viewport: " + file.getFileName());
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect the positioned HUD screenshot " + file, exception);
        }
    }

    private static LedgerStore.Document readLedger(Path file) {
        try (var store = new LedgerStore(file)) {
            return store.load();
        } catch (IOException exception) {
            throw new AssertionError("Could not read the test ledger " + file, exception);
        }
    }

    private static QbCheckpoint qbCheckpoint(ClientGameTestContext context, TestServerContext serverContext,
                                              TestWorldSave save, UUID playerId) {
        var source = serverContext.computeOnServer(server -> server.getCommandSource());
        String name = "odc-compat-" + System.currentTimeMillis();
        Path output = save.getSaveDirectory().getParent().resolve("odc-qb-export-" + System.nanoTime());
        var work = CompletableFuture.supplyAsync(() -> {
            try {
                Class<?> manager = Class.forName("io.github.skydynamic.quickbackupmulti.utils.BackupManager");
                Class<?> main = Class.forName("io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged");
                var make = Arrays.stream(manager.getMethods())
                    .filter(method -> method.getName().equals("makeBackup") && method.getParameterCount() == 3).findFirst().orElseThrow();
                make.invoke(null, source, name, "ODC integration test");
                Object database = main.getMethod("getDatabase").invoke(null);
                require((boolean) database.getClass().getMethod("storageExists", String.class).invoke(database, name), "QB created the requested backup");
                require((boolean) manager.getMethod("exportBackup", String.class, Path.class).invoke(null, name, output), "QB exported its actual backup");
                Path ledger = output.resolve("data/odc-online-duration.json");
                require(Files.isRegularFile(ledger), "The actual QB backup contains the ODC ledger");
                var document = readLedger(ledger);
                long millis = document.players().stream().filter(entry -> entry.uuid().equals(playerId)).findFirst().orElseThrow().totalMillis();
                return new QbCheckpoint(ledger, document.worldId(), millis);
            } catch (ReflectiveOperationException exception) {
                throw new CompletionException(exception);
            }
        });
        context.waitFor(client -> work.isDone(), 2400);
        return work.join();
    }

    private static Path screenshot(ClientGameTestContext context, String name) {
        // takeScreenshot already renders one frame. A hidden/minimized development window can
        // leave vanilla's skipGameRender flag set, causing that render call to return immediately.
        context.runOnClient(client -> client.skipGameRender = false);
        context.waitFor(client -> client.isFinishedLoading() && client.getOverlay() == null, 1200);
        context.waitTicks(2);
        context.runOnClient(client -> client.skipGameRender = false);
        var file = context.takeScreenshot(name);
        try {
            var image = ImageIO.read(file.toFile());
            require(image != null, "Screenshot is a readable PNG: " + name);
            boolean visibleUi = false;
            for (int y = 0; y < image.getHeight() && !visibleUi; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    if ((rgb & 0xFF) > 100 || ((rgb >>> 8) & 0xFF) > 100 || ((rgb >>> 16) & 0xFF) > 100) {
                        visibleUi = true;
                        break;
                    }
                }
            }
            require(visibleUi, "Screenshot contains rendered UI rather than a black framebuffer: " + name);
            SCREENSHOTS.add(name);
            return file;
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect screenshot " + file, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record SessionBaseline(UUID worldId, UUID playerId, long totalMillis) { }
    private record QbCheckpoint(Path ledgerFile, UUID worldId, long totalMillis) { }
}
