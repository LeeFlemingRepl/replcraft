package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.entity.Player;

import java.util.UUID;

public class Tell implements WebsocketActionHandler {
    @Override
    public String route() {
        return "tell";
    }

    @Override
    public String permission() {
        return "replcraft.api.tell";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.Expensive.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        String target = ctx.request.getString("target");

        Player player = ReplCraft.plugin.getServer().getPlayerExact(target);
        if (player == null) {
            try {
                player = ReplCraft.plugin.getServer().getPlayer(UUID.fromString(target));
            } catch(IllegalArgumentException ignored) {}
        }
        if (player == null)
            throw new ApiError(ApiError.BAD_REQUEST, "Unknown player");

        if (!ctx.structureContext.getStructure().contains(player.getLocation()))
            throw new ApiError(ApiError.BAD_REQUEST, "Player is not inside the structure");

        String message = ctx.request.getString("message");
        if (message.length() > 1000)
            throw new ApiError(ApiError.BAD_REQUEST, "Message too long");

        ReplCraft.plugin.logger.info(String.format(
            "%s told %s via script: %s",
                ctx.structureContext.getStructure().getPlayer().getName(),
            player.getName(),
            message
        ));
        player.sendMessage(String.format(
            "[Replcraft] %s sent you a message via script: %s",
                ctx.structureContext.getStructure().getPlayer().getName(),
            message
        ));
        return null;
    }
}
