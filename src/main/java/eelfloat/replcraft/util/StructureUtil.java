package eelfloat.replcraft.util;

import eelfloat.replcraft.PhysicalStructure;
import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.StructureMaterial;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class StructureUtil {
    /**
     * Verifies a sign and the attached structure
     * @param block the block containing the sign.
     * @param usernameValidator A function that validates the username on the sign.
     * @throws InvalidStructure if the sign or structure is invalid
     * @return the validated structure
     */
    public static Structure verifySign(Block block, UUID minecraft_uuid, Predicate<String> usernameValidator) throws InvalidStructure {
        BlockState state = block.getState();
        if (!(state instanceof org.bukkit.block.Sign)) {
            throw new InvalidStructure("Block is not a sign.");
        }

        BlockData data = block.getBlockData();
        if (!(data instanceof WallSign)) {
            throw new InvalidStructure("Sign must be on a wall.");
        }

        String[] lines = ((org.bukkit.block.Sign) state).getLines();
        assert lines.length == 4;

        String repl = lines[0];
        // Note: No way to actually enforce these at the moment, that's why we're using auth tokens instead
        String replit_username = lines[1];
        String repl_id = lines[2];

        String minecraft_username = lines[3];

        if (!repl.equals("REPL")) {
            throw new InvalidStructure("Line 1 of sign is not \"REPL\"");
        }
        if (!usernameValidator.test(minecraft_username)) {
            throw new InvalidStructure("Missing permissions for this sign. Does your username match line 4?");
        }

        PhysicalStructure structure = verifyStructure(block.getRelative(((WallSign) data).getFacing().getOppositeFace()));
        structure.sign = block;
        structure.replit_username = replit_username;
        structure.replit_repl_id = repl_id;
        structure.minecraft_username = minecraft_username;
        structure.minecraft_uuid = minecraft_uuid;
        return structure;
    }

    /***
     * Verifies the structure attached to a sign
     * @param starting_block A block on the frame of the structure
     * @throws InvalidStructure if the structure is invalid
     * @return a partially validated structure, missing its sign field
     */
    public static PhysicalStructure verifyStructure(Block starting_block) throws InvalidStructure {
        // A list of all seen frame blocks, attached chests, and signs.
        HashSet<Location> seen = new HashSet<>();
        Stack<Location> to_explore = new Stack<>();
        int min_x = starting_block.getX();
        int min_y = starting_block.getY();
        int min_z = starting_block.getZ();
        int max_x = starting_block.getX();
        int max_y = starting_block.getY();
        int max_z = starting_block.getZ();
        List<Block> chests = new ArrayList<>();

        to_explore.push(starting_block.getLocation());
        StructureMaterial material = null;

        int explored = 0;
        while (!to_explore.isEmpty()) {
            explored += 1;
            if (material == null && explored > 100) {
                throw new InvalidStructure("Too many connected blocks before finding any valid structure material.");
            }
            if (material != null && explored > material.max_size) {
                throw new InvalidStructure(String.format(
                    "Too many connected %s blocks. The limit is %d for this material type.",
                    material.name, material.max_size
                ));
            }
            Block block = to_explore.pop().getBlock();
            for (BlockFace face: BlockFace.values()) {
                if (!face.isCartesian()) continue;
                Block relative = block.getRelative(face);
                Location location = relative.getLocation();
                if (relative.getType() == Material.CHEST) {
                    seen.add(location);
                    chests.add(relative);
                }
                boolean isValidStructureMaterial = material == null
                    ? ReplCraft.plugin.frame_materials.stream()
                        .flatMap(mats -> mats.validMaterials.stream())
                        .anyMatch(mat -> relative.getType() == mat)
                    : material.validMaterials.contains(relative.getType());
                if (isValidStructureMaterial && !seen.contains(location)) {
                    if (material == null) {
                        material = ReplCraft.plugin.frame_materials.stream()
                            .filter(mats -> mats.validMaterials.contains(relative.getType()))
                            .findFirst().orElseThrow(() -> new RuntimeException("unreachable"));
                        ReplCraft.plugin.logger.info("Structure type determined to be " + material.name);
                    }
                    seen.add(location);
                    to_explore.push(location);

                    min_x = Math.min(min_x, location.getBlockX());
                    min_y = Math.min(min_y, location.getBlockY());
                    min_z = Math.min(min_z, location.getBlockZ());
                    max_x = Math.max(max_x, location.getBlockX());
                    max_y = Math.max(max_y, location.getBlockY());
                    max_z = Math.max(max_z, location.getBlockZ());
                }
            }
        }
        ReplCraft.plugin.logger.info(String.format("Structure min [%s %s %s] max [%s %s %s]", min_x, min_y, min_z, max_x, max_y, max_z));

        // See cube.png

        if (material == null) {
            throw new InvalidStructure("Structure must contain at least one valid structure block from any structure type.");
        }

        // Top edges
        check_axis(material, new Location(starting_block.getWorld(), min_x, max_y, max_z), 1, 0, 0, max_x - min_x); // 1
        check_axis(material, new Location(starting_block.getWorld(), max_x, max_y, max_z), 0, 0, -1, max_z - min_z); // 2
        check_axis(material, new Location(starting_block.getWorld(), max_x, max_y, min_z), -1, 0, 0, max_x - min_x); // 3
        check_axis(material, new Location(starting_block.getWorld(), min_x, max_y, min_z), 0, 0, 1, max_z - min_z); // 4

        // Side edges
        check_axis(material, new Location(starting_block.getWorld(), max_x, min_y, max_z), 0, 1, 0, max_y - min_y); // 5
        check_axis(material, new Location(starting_block.getWorld(), max_x, min_y, min_z), 0, 1, 0, max_y - min_y); // 6
        check_axis(material, new Location(starting_block.getWorld(), min_x, min_y, min_z), 0, 1, 0, max_y - min_y); // 7
        check_axis(material, new Location(starting_block.getWorld(), min_x, min_y, max_z), 0, 1, 0, max_y - min_y); // 8

        // Bottom edges
        check_axis(material, new Location(starting_block.getWorld(), min_x, min_y, max_z), 1, 0, 0, max_x - min_x); // 9
        check_axis(material, new Location(starting_block.getWorld(), max_x, min_y, max_z), 0, 0, -1, max_z - min_z); // 10
        check_axis(material, new Location(starting_block.getWorld(), max_x, min_y, min_z), -1, 0, 0, max_x - min_x); // 11
        check_axis(material, new Location(starting_block.getWorld(), min_x, min_y, min_z), 0, 0, 1, max_z - min_z); // 12

        PhysicalStructure structure = new PhysicalStructure(
            material,
            null, null, null, null,
            null, seen, chests,
            min_x, min_y, min_z, max_x, max_y, max_z
        );

        if (structure.inner_size_x() < 1 || structure.inner_size_y() < 1 || structure.inner_size_z() < 1) {
            throw new InvalidStructure("Structure must have a nonzero interior size.");
        }

        if (structure.chests.size() > ReplCraft.plugin.structure_inventory_limit) {
            throw new InvalidStructure(String.format(
                "Structure inventory cannot exceed %d chests. Chests inside the structure and not connected to the " +
                "frame via %s blocks don't count against this limit, and you can use moveItem or setBlock's extended " +
                "form to interact with them.",
                ReplCraft.plugin.structure_inventory_limit,
                material.name
            ));
        }
        return structure;
    }

    private static void check_axis(StructureMaterial material, Location start, int dx, int dy, int dz, int length) throws InvalidStructure {
        for (int i = 0; i < length; i++) {
            Location location = start.clone().add(dx * i, dy * i, dz * i);
            if (!material.validMaterials.contains(location.getBlock().getType())) {
                throw new InvalidStructure(String.format(
                    "Missing any valid block for a(n) %s structure at %s",
                    material.name, location
                ));
            }
        }
    }

    /**
     * Parses and validates the signature, but not the content, of a jwt
     * @param json_web_token the token to parse
     * @return the parsed and validated token's claims
     * @throws ApiError if the token failed to parse
     */
    public static Claims parseToken(String json_web_token) throws ApiError {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(ReplCraft.plugin.key).build()
                .parseClaimsJws(json_web_token).getBody();
        } catch(JwtException ex) {
            throw new InvalidStructure("Token is invalid: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies a token and the associated structure asynchronously. Callbacks will be called on the main server thread.
     * @param json_web_token the jwt to verify
     * @param ok a callback for a successfully validated structure
     * @param err a callback for an unsuccessfully validated structure
     */
    public static void verifyTokenAsync(String json_web_token, Consumer<Structure> ok, Consumer<ApiError> err) {
        try {
            Claims body = parseToken(json_web_token);
            String worldName = body.get("world", String.class);
            World world = ReplCraft.plugin.getServer().getWorld(worldName);
            if (world == null) throw new InvalidStructure("Invalid world");
            int x = body.get("x", Integer.class);
            int y = body.get("y", Integer.class);
            int z = body.get("z", Integer.class);
            String username = body.get("username", String.class);
            UUID uuid = UUID.fromString(body.get("uuid", String.class));
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

            Bukkit.getScheduler().runTaskAsynchronously(ReplCraft.plugin, () -> {
                try {
                    // async required or else luckperms is unhappy
                    String permission = String.format("replcraft.auth.%s", body.get("permission", String.class));
                    if (!ReplCraft.plugin.permissionProvider.hasPermission(offlinePlayer, world, permission)) {
                        String issue = String.format("This token was issued when you held the `%s` permission, but you no longer have it.", permission);
                        throw new ApiError(ApiError.UNAUTHENTICATED, issue);
                    }
                    Bukkit.getScheduler().runTask(ReplCraft.plugin, () -> {
                        try {
                            ok.accept(verifySign(world.getBlockAt(x, y, z), uuid, username::equals));
                        } catch (ApiError ex) {
                            err.accept(ex);
                        }
                    });
                } catch (ApiError ex) {
                    Bukkit.getScheduler().runTask(ReplCraft.plugin, () -> err.accept(ex));
                }
            });
        } catch(JwtException ex) {
            err.accept(new InvalidStructure("Token is invalid: " + ex.getMessage(), ex));
        } catch (ApiError ex) {
            err.accept(ex);
        }
    }
}
