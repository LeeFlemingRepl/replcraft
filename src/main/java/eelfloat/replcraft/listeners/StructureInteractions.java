package eelfloat.replcraft.listeners;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.util.StructureUtil;
import eelfloat.replcraft.exceptions.InvalidStructure;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class StructureInteractions implements Listener {
    private final HashMap<UUID, Structure> boundStructures = new HashMap<>();

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent evt) {
        boundStructures.remove(evt.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        UUID uuid = player.getUniqueId();
        if (block == null) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.STICK) {
            boolean tryPrint = false;
            try {
                Structure structure = StructureUtil.verifyStructure(block);
                if (structure.location_equals(boundStructures.get(uuid))) {
                    tryPrint = true;
                } else {
                    boundStructures.put(uuid, structure);
                    player.sendMessage(String.format(
                        "%s bound. Shift-right-click with stick again inside to print block info.",
                        structure
                    ));
                }
            } catch (InvalidStructure ex) {
                tryPrint = true;
            }
            if (tryPrint) {
                Structure structure = boundStructures.get(uuid);
                if (structure == null) {
                    player.sendMessage(String.format(
                        "%sNo structure bound%s. Shift-right-click with stick on a valid structure wall first.",
                        ChatColor.RED,
                        ChatColor.WHITE
                    ));
                    return;
                }
                player.sendMessage(String.format(
                    "%s===%s Structure %s===%s\n%sStructure:%s %s\n%sRelative coordinates:%s %d %d %d\n%sBlock string:%s \"%s%s%s\"",
                    ChatColor.GRAY,
                    ChatColor.WHITE,
                    ChatColor.GRAY,
                    ChatColor.WHITE,
                    ChatColor.GRAY,
                    ChatColor.WHITE,
                    structure,
                    ChatColor.GRAY,
                    ChatColor.WHITE,
                    block.getX() - structure.inner_min_x(),
                    block.getY() - structure.inner_min_y(),
                    block.getZ() - structure.inner_min_z(),
                    ChatColor.GRAY,
                    ChatColor.WHITE,
                    ChatColor.GOLD,
                    block.getBlockData().getAsString(),
                    ChatColor.WHITE
                ));
            }
            return;
        }

        if (!(block.getState() instanceof org.bukkit.block.Sign)) return;
        try {
            AtomicReference<String> checkedUsername = new AtomicReference<>();
            AtomicReference<String> usedPermission = new AtomicReference<>();
            boolean permAdmin = player.hasPermission("replcraft.auth.admin");
            boolean permPublic = player.hasPermission("replcraft.auth.public");
            boolean permSelf = player.hasPermission("replcraft.auth.self");
            StructureUtil.verifySign(block, uuid, username -> {
                checkedUsername.set(username);
                if (username.equals("@ADMIN")) {
                    usedPermission.set("admin");
                    return permAdmin;
                }
                if (username.equals("@PUBLIC")) {
                    usedPermission.set("public");
                    return permPublic;
                }
                if (username.equals(player.getName())) {
                    usedPermission.set("player");
                    return permSelf;
                }
                if (username.matches("[A-Za-z0-9]+")) {
                    usedPermission.set("admin");
                    return permAdmin;
                }
                return false;
            });
            Claims claims = Jwts.claims();

            claims.put("host", ReplCraft.plugin.public_address);
            claims.put("world", block.getWorld().getName());
            claims.put("x", block.getX());
            claims.put("y", block.getY());
            claims.put("z", block.getZ());
            claims.put("uuid", uuid.toString());
            claims.put("username", checkedUsername.get());
            claims.put("permission", usedPermission.get());

            String jws = Jwts.builder().setClaims(claims).signWith(ReplCraft.plugin.key).compact();
            player.sendMessage("Your token (click to copy): " + jws);
            player.sendMessage("Keep it secret! To revoke this token, break the sign.");
        } catch (InvalidStructure ex) {
            player.sendMessage(ex.getMessage());
        }
    }
}
