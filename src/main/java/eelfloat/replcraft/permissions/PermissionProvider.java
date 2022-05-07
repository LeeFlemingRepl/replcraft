package eelfloat.replcraft.permissions;

import eelfloat.replcraft.exceptions.ApiError;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

/** Defines permissions for a given client */
public interface PermissionProvider {
    /**
     * Checks if the offline player has the given permission
     * @param player
     * @param world
     * @param permission
     * @return
     * @throws ApiError
     */
    boolean hasPermission(OfflinePlayer player, World world, String permission) throws ApiError;
}

