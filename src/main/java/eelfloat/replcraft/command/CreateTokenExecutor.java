package eelfloat.replcraft.command;

import eelfloat.replcraft.ReplCraft;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CreateTokenExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player");
            return true;
        }

        Claims claims = Jwts.claims();

        claims.put("host", ReplCraft.plugin.public_address);
        claims.put("uuid", ((Player) sender).getUniqueId().toString());
        claims.put("username", sender.getName());

        String jws = Jwts.builder().setClaims(claims).signWith(ReplCraft.plugin.key).compact();
        sender.sendMessage("Your token (click to copy): " + jws);
        sender.sendMessage("Keep it secret!");

        return true;
    }
}
