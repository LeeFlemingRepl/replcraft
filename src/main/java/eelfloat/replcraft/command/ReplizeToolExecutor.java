package eelfloat.replcraft.command;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.StructureMaterial;
import eelfloat.replcraft.StructureType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class ReplizeToolExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player");
            return true;
        }

        ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
        if (stack.getType() == Material.AIR) {
            sender.sendMessage("Please hold a tool to replize");
            return true;
        }

        Optional<StructureMaterial> material = ReplCraft.plugin.frame_materials.stream()
            .filter(mats -> (
                mats.validMaterials.stream().anyMatch(mat -> stack.getType() == mat) &&
                mats.type == StructureType.Item
            ))
            .findAny();
        if (!material.isPresent()) {
            sender.sendMessage("This tool cannot be replized. There's no structure type definition with matching material.");
            return true;
        }

        Random random = new Random();
        StringBuilder key = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            key.append(Integer.toString(random.nextInt(16), 16));
        }

        Claims claims = Jwts.claims();
        claims.put("host", ReplCraft.plugin.public_address);
        claims.put("uuid", ((Player) sender).getUniqueId().toString());
        claims.put("username", sender.getName());
        claims.put("scope", "item");
        claims.put("item", key.toString());
        String jws = Jwts.builder().setClaims(claims).signWith(ReplCraft.plugin.key).compact();

        ItemMeta itemMeta = stack.getItemMeta();
        List<String> lore = itemMeta.getLore();
        if (lore == null) lore = new ArrayList<>();
        lore.removeIf(str -> str.startsWith("Replized: "));
        lore.add("Replized: " + key);
        itemMeta.setLore(lore);
        stack.setItemMeta(itemMeta);

        sender.sendMessage("Your token (click to copy): " + jws);
        sender.sendMessage("Keep it secret!");
        return true;
    }
}
