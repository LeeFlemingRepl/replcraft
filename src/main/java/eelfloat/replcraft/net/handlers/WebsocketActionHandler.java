package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.Location;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public interface WebsocketActionHandler {
    /** @return the name of the route as called by the client */
    String route();
    /** @return The permission required for this route, or null no permission required */
    String permission();
    /** @return The fuel cost to call this route */
    FuelCost cost();
    /** @return Whether this route requires authentication */
    boolean authenticated();

    void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws InvalidStructure, ApiError;
}