package eelfloat.replcraft.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.net.StructureContext;
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

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiUtil {
    private static RegionQuery worldguardQuery = null;
    private static final Pattern blockStates = Pattern.compile("\\[(\\w+)=(\\w+)]");

    /**
     * If WorldGuard is present, initializes and returns the worldguardQuery (If worldguard _isn't_ present,
     * referencing it will throw a class loading error, so this helper function explicitly checks for it).
     * @return the initialized worldguardQuery, or null.
     */
    private static RegionQuery getWorldguardQuery() {
        if (ReplCraft.plugin.world_guard && worldguardQuery == null) {
            worldguardQuery = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        }
        return worldguardQuery;
    }

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
            throw new ApiError(ApiError.INVALID_OPERATION, term + " isn't a container");
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
        if (item == null) throw new ApiError(ApiError.INVALID_OPERATION, "no item at specified index");
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
                throw new ApiError(ApiError.INVALID_OPERATION, "This block is protected by GriefPrevention.");
        }
        if (ReplCraft.plugin.world_guard) {
            ApplicableRegionSet set = getWorldguardQuery().getApplicableRegions(BukkitAdapter.adapt(location));
            if (set.queryState(null, ReplCraft.plugin.worldGuard.replcraft_enabled) != StateFlag.State.ALLOW) {
                throw new ApiError(ApiError.INVALID_OPERATION, "This block is protected by WorldGuard.");
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
        ApplicableRegionSet set = getWorldguardQuery().getApplicableRegions(BukkitAdapter.adapt(structure.getPrimaryLocation()));
        return set.queryState(null, flag) == StateFlag.State.ALLOW;
    }

    /**
     * Validates the BlockStates in a block string
     * @param block a block string
     * @throws ApiError if any disallowed block states are present
     */
    public static void validateBlockData(String block) throws ApiError {
        Matcher matcher = blockStates.matcher(block.replaceAll(",", "]["));
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
                case "east": // fences, walls and redstone
                case "north":
                case "south":
                case "west":
                case "up":
                case "down":
                case "half": // doors, beds, stairs, tall grass, etc.
                case "note": // note blocks
                case "instrument": // note blocks
                    continue;

                // case "color": // probably not safe, transmuting wool?
                // case "powered": // whether something is powered by redstone, not safe to expose directly
                default:
                    throw new ApiError(ApiError.BAD_REQUEST, String.format("Disallowed block state tag \"%s\"", name));
            }
        }
    }

    public static void doThing(Block block) {
        block.getLocation().getWorld();
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
            case SWEET_BERRY_BUSH:    return Material.SWEET_BERRIES;
            case REDSTONE_WALL_TORCH: return Material.REDSTONE_TORCH;
            case LAVA: return Material.LAVA_BUCKET;
            case WATER: return Material.WATER_BUCKET;
            default:           return material;
        }
    }

    public static VirtualInventory getInventory(
        RequestContext ctx,
        Function<String, String> keyRemapper,
        boolean fallbackToStructureInventory
    ) throws ApiError {
        return getInventory(ctx.structureContext, ctx.request, keyRemapper, fallbackToStructureInventory);
    }

    public static boolean hasNonNull(JSONObject object, String key) {
        return object.has(key) && !object.isNull(key);
    }

    /**
     * Retrieves a virtual inventory for the reference given in the request.
     * The reference takes the form of `{ x: number, y: number, z: number }` or `{ structure: true }`.
     * @param structureContext the structure context making this request
     * @param request the request containing the reference
     * @param keyRemapper renames keys accessed from the request object (e.g. x -> target_x)
     * @param fallbackToStructureInventory if the structure inventory should be used if neither form of the reference
     *                                     is present.
     * @return a virtual inventory matching the reference
     * @throws ApiError if something goes wrong
     */
    public static VirtualInventory getInventory(
        StructureContext structureContext,
        JSONObject request,
        Function<String, String> keyRemapper,
        boolean fallbackToStructureInventory
    ) throws ApiError {
        if (hasNonNull(request, keyRemapper.apply("structure")) && request.getBoolean(keyRemapper.apply("structure"))) {
            return structureContext.getStructure().getStructureInventory();
        } else if (hasNonNull(request, keyRemapper.apply("x"))) {
            BlockState state = getBlock(
                structureContext,
                request,
                keyRemapper.apply("x"),
                keyRemapper.apply("y"),
                keyRemapper.apply("z")
            ).getState();
            if (!(state instanceof Container)) {
                throw new ApiError(ApiError.INVALID_OPERATION, "block isn't a container");
            }
            InventoryReference ref = new InventoryReference((Container) state);
            return new VirtualInventory(ref);
        } else if (fallbackToStructureInventory) {
            return structureContext.getStructure().getStructureInventory();
        } else {
            throw new ApiError(ApiError.BAD_REQUEST, "no container specified");
        }
    }

    public static Block getBlock(StructureContext structureContext, JSONObject request) throws ApiError {
        return getBlock(structureContext, request, "x", "y", "z");
    }

    public static OfflinePlayer getTargetPlayer(StructureContext structureContext, JSONObject request) throws ApiError {
        String targetId = request.getString("target");
        try {
            UUID uuid = UUID.fromString(targetId);
            return Bukkit.getOfflinePlayer(uuid);
        } catch(IllegalArgumentException ex) {
            Player target = ReplCraft.plugin.getServer().getPlayer(targetId);
            if (target == null) {
                throw new ApiError(
                    ApiError.BAD_REQUEST,
                    "Player with given name is not online. Use a UUID to pay to offline players."
                );
            }
            return target;
        }
    }

    public static Block getBlock(StructureContext structureContext, JSONObject request, String label_x, String label_y, String label_z) throws ApiError {
        int x = request.getInt(label_x);
        int y = request.getInt(label_y);
        int z = request.getInt(label_z);
        Block block = structureContext.getStructure().getBlock(x, y, z);
        if (block == null) throw new ApiError(ApiError.BAD_REQUEST, "block out of bounds");
        return block;
    }
}
