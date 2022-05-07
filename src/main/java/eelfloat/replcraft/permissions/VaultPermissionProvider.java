package eelfloat.replcraft.permissions;

import eelfloat.replcraft.Structure;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

/**
 * Provides permissions using the Vault API
 */
public class VaultPermissionProvider implements PermissionProvider {
    private final Permission permission;

    public VaultPermissionProvider(Permission permission) {
        this.permission = permission;
    }

    @Override
    public boolean hasPermission(OfflinePlayer player, World world, String permission) throws ApiError {
        return this.permission.playerHas(world.getName(), player, permission);
    }
}
