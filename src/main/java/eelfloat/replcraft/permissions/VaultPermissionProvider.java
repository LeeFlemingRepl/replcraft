package eelfloat.replcraft.permissions;

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
    public boolean hasPermission(OfflinePlayer player, World world, String permission) {
        return this.permission.playerHas(world.getName(), player, permission);
    }
}
