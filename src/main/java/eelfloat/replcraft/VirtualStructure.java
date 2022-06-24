package eelfloat.replcraft;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;

public class VirtualStructure extends Structure {
    private final Player player;
    private final ItemStack item;

    public VirtualStructure(
        Player player,
        ItemStack item,
        StructureMaterial material,
        String replit_username, String replit_repl_id, String minecraft_username, UUID minecraft_uuid,
        World world, int min_x, int min_y, int min_z, int max_x, int max_y, int max_z
    ) {
        super(
            material,
            replit_username, replit_repl_id, minecraft_username, minecraft_uuid,
            world, min_x, min_y, min_z, max_x, max_y, max_z
        );
        this.player = player;
        this.item = item;
    }

    @Override
    public Iterator<Inventory> getStructureInventory() {
        return Collections.singleton((Inventory) player.getInventory()).iterator();
    }

    @Override
    public Location getPrimaryLocation() {
        return this.player.getLocation();
    }
}
