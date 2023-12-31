package eelfloat.replcraft.command;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.StructureContext;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

public class TransactExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player");
            return true;
        }

        Location location = ((Player) sender).getLocation();
        List<StructureContext> structureContexts = ReplCraft.plugin.websocketServer.clients.values()
            .parallelStream()
            .flatMap(client -> client.getContexts().stream())
            .filter(ctx -> {
                Structure structure = ctx.getStructure();
                if (structure == null) return false;
                return structure.contains(location);
            })
            .limit(2)
            .collect(Collectors.toList());

        if (structureContexts.size() == 0) {
            sender.sendMessage("You must use this command inside an active structure.");
            return true;
        }
        if (structureContexts.size() == 2) {
            sender.sendMessage("This command must be used inside exactly one active structure. Overlapping structures are not allowed.");
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0]);
        } catch(NumberFormatException ignored) {
            return false;
        }

        StringBuilder string = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            string.append(args[i]);
            string.append(" ");
        }
        String trim = string.toString().trim();

        StructureContext structureContext = structureContexts.get(0);
        if (ReplCraft.plugin.economy == null) {
            sender.sendMessage("This command requires Vault to be installed.");
            return true;
        }

        OfflinePlayer offp = (OfflinePlayer) sender;
        String world = location.getWorld().getName();
        EconomyResponse econResponse = ReplCraft.plugin.economy.withdrawPlayer(offp, world, amount);
        if (!econResponse.transactionSuccess()) {
            sender.sendMessage("Insufficient funds");
            return true;
        }

        JSONObject query = new JSONObject();
        query.put("type", "transact");
        query.put("player", offp.getName());
        query.put("player_uuid", offp.getUniqueId());
        query.put("query", trim);
        query.put("amount", amount);
        sender.sendMessage("Transaction opened...");
        structureContext.sendQuery(query, (status, response) -> {
            switch(status) {
                case Success:
                    if (response.getBoolean("accept")) {
                        sender.sendMessage(String.format(
                            "Transaction accepted by %s's script!",
                            structureContext.getStructure().getPlayer().getName()
                        ));
                        ReplCraft.plugin.economy.depositPlayer(structureContext.getStructure().getPlayer(), amount);
                    } else {
                        sender.sendMessage(String.format(
                            "Transaction was denied by %s's script. You have been refunded.",
                            structureContext.getStructure().getPlayer().getName()
                        ));
                        ReplCraft.plugin.economy.depositPlayer(offp, world, amount);
                    }
                    break;

                case TimedOut:
                    sender.sendMessage("Request timed out, you have been refunded.");
                    ReplCraft.plugin.economy.depositPlayer(offp, world, amount);
                    break;
            }
            return null;
        });
        return true;
    }
}
