package eelfloat.replcraft.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiUtil {
    private static final RegionQuery worldguardQuery = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    private static final Pattern blockStates = Pattern.compile("\\[(\\w+)=(\\w+)]");

    /**
     * Validates and returns a container inventory
     * @param container the block that should be a container
     * @param term a human-readable name for the block that's used for the error message
     * @return the block's inventory
     * @throws ApiError if the block isn't a container
     */
    public static Inventory getContainer(Block container, String term) throws ApiError {
        BlockState sourceState = container.getState();
        if (!(sourceState instanceof Container)) {
            throw new ApiError("invalid operation", term + " isn't a container");
        }
        return ((Container) sourceState).getInventory();
    }

    /**
     * Validates and returns a non-null itemstack in the given container at the given index
     * @param container the block that should be a container
     * @param term a human-readable name for the block that's used for the error message
     * @param index the index of the itemstack
     * @return the item
     * @throws ApiError if the block isn't a container or doesn't have an item at the given index
     */
    public static ItemStack getItem(Block container, String term, int index) throws ApiError {
        ItemStack item = getContainer(container, term).getItem(index);
        if (item == null) throw new ApiError("invalid operation", "no item at specified index");
        return item;
    }

    /**
     * Checks if the player with the given UUID is allowed to build in the claim at the given location
     * @param player the uuid of the player who owns this region
     * @param location the location of the block being modified
     * @throws ApiError if any protection plugin denied the block modification
     */
    public static void checkProtectionPlugins(UUID player, Location location) throws ApiError {
        if (ReplCraft.plugin.grief_prevention) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claim != null && !claim.hasExplicitPermission(player, ClaimPermission.Build))
                throw new ApiError("invalid operation", "This block is protected by GriefPrevention.");
        }
        if (ReplCraft.plugin.world_guard) {
            ApplicableRegionSet set = worldguardQuery.getApplicableRegions(BukkitAdapter.adapt(location));
            if (set.queryState(null, ReplCraft.plugin.worldGuard.replcraft_enabled) != StateFlag.State.ALLOW) {
                throw new ApiError("invalid operation", "This block is protected by WorldGuard.");
            }
        }
    }

    /**
     * Check if a WorldGuard flag is set on a given structure. The flag is checked at the structure's sign.
     * @param structure the structure to check
     * @param flag the flag to check for
     * @return true if the flag is ALLOW
     */
    public static boolean checkFlagOnStructure(Structure structure, StateFlag flag) {
        ApplicableRegionSet set = worldguardQuery.getApplicableRegions(BukkitAdapter.adapt(structure.sign.getLocation()));
        return set.queryState(null, flag) == StateFlag.State.ALLOW;
    }

    /**
     * Validates the BlockStates in a block string
     * @param block a block string
     * @throws ApiError if any disallowed block states are present
     */
    public static void validateBlockData(String block) throws ApiError {
        Matcher matcher = blockStates.matcher(block);
        while (matcher.find()) {
            String name = matcher.group(1);
            switch (name) {
                case "facing":
                case "rotation":
                case "axis":
                case "attachment":
                case "face":
                case "hinge": // doors
                case "hanging": // lanterns
                case "shape": // rails, stairs
                case "mode": // comparators
                case "delay": // repeater
                case "type": // slabs (todo: potential duplication?)
                case "open": // trapdoors
                // case "east": // maybe, for fences, walls and redstone
                // case "north":
                // case "south":
                // case "west":
                // case "up":
                // case "down":
                // case "half": // maybe, for doors, beds, stairs, tall grass, etc.
                // case "color": // maybe
                    continue;

                default:
                    throw new ApiError("bad request", String.format("Disallowed block state tag \"%s\"", name));
            }
        }
    }

    /**
     * Remaps a material based on what item is necessary to place it (e.g. wheat -> seeds)
     * @param material the block material
     * @return the item material
     */
    public static Material remapBlockMaterialToItemMaterial(Material material) {
        // todo: I'm sure there's more
        // Beds and doors may also be problematic
        switch (material) {
            case WHEAT:        return Material.WHEAT_SEEDS;
            case PUMPKIN_STEM: return Material.PUMPKIN_SEEDS;
            case MELON_STEM:   return Material.MELON_SEEDS;
            case BEETROOTS:    return Material.BEETROOT_SEEDS;
            case COCOA:        return Material.COCOA_BEANS;
            case CARROTS:      return Material.CARROT;
            case POTATOES:     return Material.POTATO;
            default:           return material;
        }
    }

    public static Block getBlock(Client client, JSONObject request) throws ApiError {
        return getBlock(client, request, "x", "y", "z");
    }

    public static OfflinePlayer getTargetPlayer(Client client, JSONObject request) throws ApiError {
        String targetId = request.getString("target");
        try {
            UUID uuid = UUID.fromString(targetId);
            return Bukkit.getOfflinePlayer(uuid);
        } catch(IllegalArgumentException ex) {
            Player target = ReplCraft.plugin.getServer().getPlayer(targetId);
            if (target == null) {
                throw new ApiError(
                    "bad request",
                    "Player with given name is not online. Use a UUID to pay to offline players."
                );
            }
            return target;
        }
    }

    public static Block getBlock(Client client, JSONObject request, String label_x, String label_y, String label_z) throws ApiError {
        int x = request.getInt(label_x);
        int y = request.getInt(label_y);
        int z = request.getInt(label_z);
        Block block = client.getStructure().getBlock(x, y, z);
        if (block == null) throw new ApiError("bad request", "block out of bounds");
        return block;
    }
}
