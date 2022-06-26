package eelfloat.replcraft.command;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.util.ReplizedTool;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DereplizeToolExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player");
            return true;
        }

        ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
        if (stack.getType() == Material.AIR) {
            sender.sendMessage("Please hold a tool to dereplize");
            return true;
        }

        ReplizedTool tool = ReplizedTool.parse(stack);
        if (tool == null) {
            sender.sendMessage("Tool is not replized");
            return true;
        }

        tool.dereplize();
        if (tool.itemMeta instanceof Damageable && ReplCraft.plugin.replizePrice > 0) {
            String world = ((Player) sender).getWorld().getName();
            short maxDurability = stack.getType().getMaxDurability();
            int damage = ((Damageable) tool.itemMeta).getDamage();
            double price = (maxDurability - damage) * ReplCraft.plugin.replizePrice;
            EconomyResponse tx = ReplCraft.plugin.economy.depositPlayer((Player) sender, world, price);
            if (tx.transactionSuccess()) {
                sender.sendMessage(String.format(
                    "%sYou have been refunded %s$%s%s for dereplizing this tool",
                    ChatColor.DARK_GREEN,
                    ChatColor.GREEN,
                    price,
                    ChatColor.DARK_GREEN
                ));
            }
        }
        sender.sendMessage("Tool dereplized");
        return true;
    }
}
