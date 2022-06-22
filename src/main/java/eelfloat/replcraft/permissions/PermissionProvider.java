package eelfloat.replcraft.permissions;

import eelfloat.replcraft.exceptions.ApiError;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

/** Defines permissions for a given client */
public interface PermissionProvider {
    /**
     * Checks if the offline player has the given permission
     * @param player the player to look up the permission for
     * @param world the world to look the permission up in
     * @param permission the name of the permission
     * @return if the player has the permission
     * @throws ApiError if looking up the permission failed for some reason
     */
    boolean hasPermission(OfflinePlayer player, World world, String permission) throws ApiError;
}

