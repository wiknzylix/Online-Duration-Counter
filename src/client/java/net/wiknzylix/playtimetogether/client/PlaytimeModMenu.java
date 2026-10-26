package net.wiknzylix.playtimetogether.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded only through Mod Menu's optional entrypoint. */
public final class PlaytimeModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PlaytimeConfigScreen::new;
    }
}
