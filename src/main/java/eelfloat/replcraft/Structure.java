package eelfloat.replcraft;

import eelfloat.replcraft.net.StructureContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

public abstract class Structure {
    public final StructureMaterial material;
    public String replit_username;
    public String replit_repl_id;
    public String minecraft_username;
    public UUID minecraft_uuid;
    public World world;
    public int min_x;
    public int min_y;
    public int min_z;
    public int max_x;
    public int max_y;
    public int max_z;
    private boolean loaded = false;

    public Structure(
        StructureMaterial material,
        String replit_username,
        String replit_repl_id,
        String minecraft_username,
        UUID minecraft_uuid,
        World world,
        int min_x, int min_y, int min_z,
        int max_x, int max_y, int max_z
    ) {
        this.material = material;
        this.replit_username = replit_username;
        this.replit_repl_id = replit_repl_id;
        this.minecraft_username = minecraft_username;
        this.minecraft_uuid = minecraft_uuid;
        this.world = world;
        this.min_x = min_x;
        this.min_y = min_y;
        this.min_z = min_z;
        this.max_x = max_x;
        this.max_y = max_y;
        this.max_z = max_z;
    }

    /**
     * Loads all chunks specified by this structure
     */
    public void chunkLoad() {
        loaded = true;
        // add -2/+2 to account for the frame, and signs/chests connected to the frame
        int min_x = (this.inner_min_x() - 2) / 16;
        int max_x = (int) Math.ceil((this.inner_min_x() + this.inner_size_x() + 2) / 16.0);
        int min_z = (this.inner_min_z() - 2) / 16;
        int max_z = (int) Math.ceil((this.inner_min_z() + this.inner_size_z() + 2) / 16.0);
        for (int x = min_x; x < max_x; x++) {
            for (int z = min_z; z < max_z; z++) {
                if (this.world.addPluginChunkTicket(x, z, ReplCraft.plugin)) {
                    ReplCraft.plugin.logger.info("Adding ticket for chunk " + x + ", " + z);
                }
            }
        }
    }

    /**
     * Unloads all chunks covered by this structure, so long as no other structure contains them
     */
    public void unchunkLoad() {
        loaded = false;
        // add -2/+2 to account for the frame, and signs/chests connected to the frame
        int min_x = (this.inner_min_x() - 2) / 16;
        int max_x = (int) Math.ceil((this.inner_min_x() + this.inner_size_x() + 2) / 16.0);
        int min_z = (this.inner_min_z() - 2) / 16;
        int max_z = (int) Math.ceil((this.inner_min_z() + this.inner_size_z() + 2) / 16.0);
        for (int x = min_x; x < max_x; x++) {
            for (int z = min_z; z < max_z; z++) {
                int finalX = x, finalZ = z;
                boolean loadedElsewhere = ReplCraft.plugin.websocketServer.clients.values().stream()
                        .flatMap(client -> client.getContexts().stream())
                        .anyMatch(ctx -> {
                            Structure structure = ctx.getStructure();
                            return structure.loaded && structure.containsChunk(finalX, finalZ);
                        });
                if (!loadedElsewhere) {
                    this.world.removePluginChunkTicket(x, z, ReplCraft.plugin);
                    ReplCraft.plugin.logger.info("Removing ticket for chunk " + x + ", " + z);
                } else {
                    ReplCraft.plugin.logger.info("Not removing ticket for chunk " + x + ", " + z + ", loaded elsewhere.");
                }
            }
        }
    }

    /**
     * Check if this structure covers the given chunk
     */
    private boolean containsChunk(int x, int z) {
        int min_x = (this.inner_min_x() - 2) / 16;
        int max_x = (int) Math.ceil((this.inner_min_x() + this.inner_size_x() + 2) / 16.0);
        int min_z = (this.inner_min_z() - 2) / 16;
        int max_z = (int) Math.ceil((this.inner_min_z() + this.inner_size_z() + 2) / 16.0);
        return (x >= min_x && x < max_x && z >= min_z && z < max_z);
    }

    /**
     * Retrieves the inner size of this structure along the x axis
     */
    public int inner_size_x() {
        return this.max_x - this.min_x - 1;
    }

    /**
     * Retrieves the inner size of this structure along the y axis
     */
    public int inner_size_y() {
        return this.max_y - this.min_y - 1;
    }

    /**
     * Retrieves the inner size of this structure along the z axis
     */
    public int inner_size_z() {
        return this.max_z - this.min_z - 1;
    }

    /**
     * Retrieves the minimum x coordinate on the inside of this structure
     */
    public int inner_min_x() {
        return this.min_x + 1;
    }

    /**
     * Retrieves the minimum y coordinate on the inside of this structure
     */
    public int inner_min_y() {
        return this.min_y + 1;
    }

    /**
     * Retrieves the minimum z coordinate on the inside of this structure
     */
    public int inner_min_z() {
        return this.min_z + 1;
    }

    /**
     * Obtains the player this structure belongs to
     */
    public OfflinePlayer getPlayer() {
        return ReplCraft.plugin.getServer().getOfflinePlayer(minecraft_uuid);
    }

    /**
     * Retrieves the world this structure is in
     *
     * @return the world this structure is in
     */
    public World getWorld() {
        return this.world;
    }

    /**
     * Retrieves a block relative to the structure
     *
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @return the block, or null if out of bounds
     */
    public Block getBlock(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0) return null;
        if (x >= inner_size_x() || y >= inner_size_y() || z >= inner_size_z()) return null;
        return this.world.getBlockAt(x + inner_min_x(), y + inner_min_y(), z + inner_min_z());
    }

    public abstract Iterator<Inventory> getStructureInventory();

    /**
     * Obtains an ItemStack by material type in any of the structure's connected chests
     *
     * @param mat the material to filter by
     * @return a matching item stack, or null
     */
    public ItemStack findMaterial(Material mat) {
        Iterator<Inventory> iter = this.getStructureInventory();
        while (iter.hasNext()) {
            Inventory inventory = iter.next();
            int i = inventory.first(mat);
            if (i != -1) return inventory.getItem(i);
        }
        return null;
    }

    public int countMaterial(Material mat) {
        Iterator<Inventory> iter = this.getStructureInventory();
        int count = 0;
        while (iter.hasNext()) {
            Inventory inventory = iter.next();
            for (ItemStack content : inventory.getContents())
                if (content.getType() == mat)
                    count += content.getAmount();
        }
        return count;
    }

    /**
     * Attempts to deposit an ItemStack into any attached inventory
     *
     * @param stack the stack to deposit
     * @return any leftover portion of the stack which didn't fit anywhere, null if completely inserted.
     */
    public ItemStack deposit(ItemStack stack) {
        Iterator<Inventory> iter = this.getStructureInventory();
        while (iter.hasNext()) {
            stack = iter.next().addItem(stack).values().stream().findFirst().orElse(null);
            if (stack == null) break;
        }
        return stack;
    }

    public boolean contains(Location location) {
        return (
            Objects.equals(location.getWorld(), this.getWorld()) &&
            location.getX() >= this.min_x &&
            location.getY() >= this.min_y &&
            location.getZ() >= this.min_z &&
            location.getX() <= this.max_x &&
            location.getY() <= this.max_y &&
            location.getZ() <= this.max_z
        );
    }

    public boolean location_equals(Structure structure) {
        return (
            structure != null &&
            getWorld().equals(structure.getWorld()) &&
            min_x == structure.min_x &&
            min_y == structure.min_y &&
            min_z == structure.min_z &&
            max_x == structure.max_x &&
            max_y == structure.max_y &&
            max_z == structure.max_z
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Structure structure = (Structure) o;
        return (
            location_equals(structure) &&
            Objects.equals(material, structure.material) &&
            Objects.equals(minecraft_uuid, structure.minecraft_uuid)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(min_x, min_y, min_z, max_x, max_y, max_z, material, minecraft_uuid, world);
    }

    @Override
    public String toString() {
        return String.format(
            "Structure (%s, @ %s %d,%d,%d %dx%dx%d%s)",
            this.minecraft_username,
            world.getName(),
            min_x, min_y, min_z,
            max_x - min_x, max_y - min_y, max_z - min_z,
            this.loaded ? " loaded" : ""
        );
    }

    public abstract Location getPrimaryLocation();
}
