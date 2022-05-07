package eelfloat.replcraft.permissions;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Provides permissions using the default bukkit API
 * Doesn't work for offline players
 */
public class DefaultPermissionProvider implements PermissionProvider {
    @Override
    public boolean hasPermission(OfflinePlayer player, World world, String permission) throws ApiError {
        if (!(player instanceof Player)) throw ApiError.OFFLINE;
        return ((Player) player).hasPermission(permission);
    }
}
