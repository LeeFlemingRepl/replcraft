package eelfloat.replcraft;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class PhysicalStructure extends Structure {
    public Block sign;
    public List<Block> chests;
    /**
     * A list of all frame blocks (iron blocks, the sign, any attached chests, etc.)
     * Whenever a chest or sign block is placed next to these, or whenever an iron block
     * is broken, the frame should be re-explored.
     */
    public HashSet<Location> frameBlocks;

    /**
     *
     * @param replit_username the replit username on the sign
     * @param replit_repl_id the repl id on the sign
     * @param minecraft_username the minecraft username on the sign
     * @param sign the sign identifying this structure
     * @param min_x the lower x coordinate of this structure's AABB
     * @param min_y the lower y coordinate of this structure's AABB
     * @param min_z the lower z coordinate of this structure's AABB
     * @param max_x the upper x coordinate of this structure's AABB
     * @param max_y the upper y coordinate of this structure's AABB
     * @param max_z the upper z coordinate of this structure's AABB
     */
    public PhysicalStructure(
        StructureMaterial material,
        String replit_username, String replit_repl_id, String minecraft_username, UUID minecraft_uuid,
        Block sign, HashSet<Location> frameBlocks, List<Block> chests,
        int min_x, int min_y, int min_z, int max_x, int max_y, int max_z
    ) {
        super(
            material,
            replit_username, replit_repl_id, minecraft_username, minecraft_uuid,
            sign.getWorld(), min_x, min_y, min_z, max_x, max_y, max_z
        );
        this.sign = sign;
        this.frameBlocks = frameBlocks;
        this.chests = chests;
    }

    @Override
    public Iterator<Inventory> getStructureInventory() {
        return this.chests.stream().map(chest -> {
            BlockState state = chest.getState();
            if (!(state instanceof Chest)) return null;
            return ((Chest) state).getInventory();
        }).filter(Objects::nonNull).iterator();
    }

    @Override
    public Location getPrimaryLocation() {
        return sign.getLocation();
    }
}
