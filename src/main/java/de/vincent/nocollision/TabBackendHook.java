package de.vincent.nocollision;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import org.bukkit.entity.Player;

/** Only loaded when TAB is installed on this backend server. */
final class TabBackendHook {

    enum Result { APPLIED, NOT_LOADED, UNAVAILABLE }

    TabBackendHook() {
        // Fail fast if the API is missing or incompatible.
        TabAPI.getInstance();
    }

    Result apply(Player player, boolean disabled, boolean changed) {
        NameTagManager nameTags = TabAPI.getInstance().getNameTagManager();
        if (nameTags == null) {
            return Result.UNAVAILABLE;
        }
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
        if (tabPlayer == null || !tabPlayer.isLoaded()) {
            return Result.NOT_LOADED;
        }
        if (changed) {
            // false = no collision, null = back to TAB's configured default.
            nameTags.setCollisionRule(tabPlayer, disabled ? Boolean.FALSE : null);
        }
        return Result.APPLIED;
    }

    void reset(Player player) {
        NameTagManager nameTags = TabAPI.getInstance().getNameTagManager();
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
        if (nameTags != null && tabPlayer != null) {
            nameTags.setCollisionRule(tabPlayer, null);
        }
    }
}
