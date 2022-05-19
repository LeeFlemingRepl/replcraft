package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.entity.Player;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;

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
    public FuelCost cost() {
        return FuelCost.Expensive;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws InvalidStructure, ApiError {
        String target = request.getString("target");

        Player player = ReplCraft.plugin.getServer().getPlayerExact(target);
        if (player == null) {
            try {
                player = ReplCraft.plugin.getServer().getPlayer(UUID.fromString(target));
            } catch(IllegalArgumentException ignored) {}
        }
        if (player == null)
            throw new ApiError("bad request", "Unknown player");

        if (!client.getStructure().contains(player.getLocation()))
            throw new ApiError("bad request", "Player is not inside the structure");

        String message = request.getString("message");
        if (message.length() > 100)
            throw new ApiError("bad request", "Message too long");

        ReplCraft.plugin.logger.info(String.format(
            "%s told %s via script: %s",
            client.getStructure().getPlayer().getName(),
            player.getName(),
            message
        ));
        player.sendMessage(String.format(
            "[Replcraft] %s sent you a message via script: %s",
            client.getStructure().getPlayer().getName(),
            message
        ));
    }
}
