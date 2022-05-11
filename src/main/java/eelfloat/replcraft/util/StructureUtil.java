package eelfloat.replcraft.util;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
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

        Structure structure = verifyStructure(block.getRelative(((WallSign) data).getFacing().getOppositeFace()));
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
    public static Structure verifyStructure(Block starting_block) throws InvalidStructure {
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

        int explored = 0;
        while (!to_explore.isEmpty()) {
            explored += 1;
            if (explored > 1000) {
                throw new InvalidStructure("Too many connected " + ReplCraft.plugin.frame_material + " blocks.");
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
                if (relative.getType() == ReplCraft.plugin.frame_material && !seen.contains(location)) {
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

        // See cube.png

        // Top edges
        check_axis(new Location(starting_block.getWorld(), min_x, max_y, max_z), 1, 0, 0, max_x - min_x); // 1
        check_axis(new Location(starting_block.getWorld(), max_x, max_y, max_z), 0, 0, -1, max_z - min_z); // 2
        check_axis(new Location(starting_block.getWorld(), max_x, max_y, min_z), -1, 0, 0, max_x - min_x); // 3
        check_axis(new Location(starting_block.getWorld(), min_x, max_y, min_z), 0, 0, 1, max_z - min_z); // 4

        // Side edges
        check_axis(new Location(starting_block.getWorld(), max_x, min_y, max_z), 0, 1, 0, max_y - min_y); // 5
        check_axis(new Location(starting_block.getWorld(), max_x, min_y, min_z), 0, 1, 0, max_y - min_y); // 6
        check_axis(new Location(starting_block.getWorld(), min_x, min_y, min_z), 0, 1, 0, max_y - min_y); // 7
        check_axis(new Location(starting_block.getWorld(), min_x, min_y, max_z), 0, 1, 0, max_y - min_y); // 8

        // Bottom edges
        check_axis(new Location(starting_block.getWorld(), min_x, min_y, max_z), 1, 0, 0, max_x - min_x); // 9
        check_axis(new Location(starting_block.getWorld(), max_x, min_y, max_z), 0, 0, -1, max_z - min_z); // 10
        check_axis(new Location(starting_block.getWorld(), max_x, min_y, min_z), -1, 0, 0, max_x - min_x); // 11
        check_axis(new Location(starting_block.getWorld(), min_x, min_y, min_z), 0, 0, 1, max_z - min_z); // 12

        Structure structure = new Structure(
            null, null, null, null,
            null, seen, chests,
            min_x, min_y, min_z, max_x, max_y, max_z
        );

        if (structure.inner_size_x() < 1 || structure.inner_size_y() < 1 || structure.inner_size_z() < 1) {
            throw new InvalidStructure("Structure must start on an iron block and have a nonzero interior size.");
        }
        return structure;
    }

    private static void check_axis(Location start, int dx, int dy, int dz, int length) throws InvalidStructure {
        for (int i = 0; i < length; i++) {
            Location location = start.clone().add(dx * i, dy * i, dz * i);
            if (location.getBlock().getType() != ReplCraft.plugin.frame_material) {
                throw new InvalidStructure("Missing " + ReplCraft.plugin.frame_material + " at " + location);
            }
        }
    }

    /**
     * Verifies a token and the associated structure
     * @param json_web_token the jwt to verify
     * @throws InvalidStructure if the structure is invalid or missing
     * @return a validated structure
     */
    public static Structure verifyToken(String json_web_token) throws InvalidStructure {
        try {
            Claims body = Jwts.parserBuilder()
                .setSigningKey(ReplCraft.plugin.key).build()
                .parseClaimsJws(json_web_token).getBody();
            String worldName = body.get("world", String.class);
            World world = ReplCraft.plugin.getServer().getWorld(worldName);
            if (world == null) throw new InvalidStructure("Invalid world");
            int x = body.get("x", Integer.class);
            int y = body.get("y", Integer.class);
            int z = body.get("z", Integer.class);
            String username = body.get("username", String.class);
            UUID uuid = UUID.fromString(body.get("uuid", String.class));

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

            String permission = String.format("replcraft.auth.%s", body.get("permission", String.class));
            if (!ReplCraft.plugin.permissionProvider.hasPermission(offlinePlayer, world, permission)) {
                String issue = String.format("This token was issued when you held the `%s` permission, but you no longer have it.", permission);
                throw new ApiError("unauthenticated", issue);
            }

            return verifySign(world.getBlockAt(x, y, z), uuid, username::equals);
        } catch(JwtException ex) {
            throw new InvalidStructure("Token is invalid: " + ex.getMessage(), ex);
        } catch (ApiError e) {
            throw new InvalidStructure("Token is invalid: " + e.message);
        }
    }
}
