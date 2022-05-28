package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.net.RateTracker;
import eelfloat.replcraft.strategies.FuelStrategy;
import io.javalin.websocket.WsMessageContext;
import org.eclipse.jetty.util.ajax.JSON;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

public class FuelInfo implements WebsocketActionHandler {
    @Override
    public String route() {
        return "fuelinfo";
    }

    @Override
    public String permission() {
        return "replcraft.api.fuelinfo";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.None;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public void execute(Client currentClient, WsMessageContext ctx, JSONObject request, JSONObject response) throws InvalidStructure, ApiError {
        JSONArray connections = new JSONArray();
        for (Client client: ReplCraft.plugin.websocketServer.clients.values()) {
            Structure current = currentClient.getStructure();
            Structure structure = client.getStructure();
            if (structure == null) continue;
            if (!current.getPlayer().getUniqueId().equals(structure.getPlayer().getUniqueId())) continue;

            JSONObject connection = new JSONObject();
            connection.put("structure", structure.toString());
            connection.put("x", structure.min_x);
            connection.put("y", structure.min_y);
            connection.put("z", structure.min_z);

            JSONObject apiCalls = new JSONObject();
            for (Map.Entry<String, RateTracker> tracker : client.rateTrackers.entrySet()) {
                JSONObject apiCall = new JSONObject();
                for (Map.Entry<String, RateTracker.RateTrackTimeContainer> container: tracker.getValue().containers.entrySet())
                    apiCall.put(container.getKey(), container.getValue().fuelUsed());
                apiCalls.put(tracker.getKey(), apiCall);
            }
            connection.put("fuelUsage", apiCalls);

            connections.put(connection);
        }
        response.put("connections", connections);

        JSONObject apis = new JSONObject();
        for (WebsocketActionHandler api: ReplCraft.plugin.websocketServer.apis.values()) {
            JSONObject apiJson = new JSONObject();
            apiJson.put("baseFuelCost", api.cost().toDouble());
            apiJson.put("fuelCost", api.cost().toDouble() * currentClient.getStructure().material.fuelMultiplier);
            apis.put(api.route(), apiJson);
        }
        response.put("apis", apis);

        JSONArray strategies = new JSONArray();
        for (FuelStrategy strategy: currentClient.strategies) {
            JSONObject strategyJson = new JSONObject();
            strategyJson.put("strategy", strategy.name());
            strategyJson.put("spareFuel", strategy.getSpareFuel());
            strategies.put(strategyJson);
        }
        response.put("strategies", strategies);
    }
}
