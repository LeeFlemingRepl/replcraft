package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.*;
import eelfloat.replcraft.strategies.FuelStrategy;
import eelfloat.replcraft.strategies.RatelimitFuelStrategy;
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
    public double cost(RequestContext ctx) {
        return FuelCost.None.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) {
        JSONArray connections = new JSONArray();
        for (Client client: ReplCraft.plugin.websocketServer.clients.values()) {
            for (StructureContext structureContext: client.getContexts()) {
                Structure current = ctx.structureContext.getStructure();
                Structure structure = structureContext.getStructure();
                if (structure == null) continue;
                if (!current.getPlayer().getUniqueId().equals(structure.getPlayer().getUniqueId())) continue;

                JSONObject connection = new JSONObject();
                connection.put("structure", structure.toString());
                connection.put("x", structure.min_x);
                connection.put("y", structure.min_y);
                connection.put("z", structure.min_z);

                JSONObject apiCalls = new JSONObject();
                for (Map.Entry<String, RateTracker> tracker : structureContext.rateTrackers.entrySet()) {
                    JSONObject apiCall = new JSONObject();
                    for (Map.Entry<String, RateTracker.RateTrackTimeContainer> container: tracker.getValue().containers.entrySet())
                        apiCall.put(container.getKey(), container.getValue().fuelUsed());
                    apiCalls.put(tracker.getKey(), apiCall);
                }
                connection.put("fuelUsage", apiCalls);

                connections.put(connection);
            }
        }
        ctx.response.put("connections", connections);

        JSONObject apis = new JSONObject();
        for (WebsocketActionHandler api: ReplCraft.plugin.websocketServer.apis.values()) {
            JSONObject apiJson = new JSONObject();
            apiJson.put("baseFuelCost", api.cost(ctx));
            apiJson.put("fuelCost", api.cost(ctx) * ctx.structureContext.getStructure().material.fuelMultiplier);
            apis.put(api.route(), apiJson);
        }
        ctx.response.put("apis", apis);

        JSONArray strategies = new JSONArray();
        for (FuelStrategy strategy: ctx.structureContext.strategies) {
            if (strategy instanceof RatelimitFuelStrategy)
                ((RatelimitFuelStrategy) strategy).updateSpareFuelNow();
            JSONObject strategyJson = new JSONObject();
            strategyJson.put("name", strategy.configName);
            strategyJson.put("strategy", strategy.getType());
            strategyJson.put("spareFuel", strategy.getSpareFuel());
            double userLimit = ctx.structureContext.getMaxFuel(strategy.configName);
            strategyJson.put("userLimit", userLimit == Double.POSITIVE_INFINITY ? -1 : userLimit);
            strategyJson.put("totalUsed", ctx.structureContext.getRemainingFuel(strategy.configName));
            strategyJson.put("generatableEstimate", strategy.getEstimatedFuelAvailable(ctx.structureContext));
            strategies.put(strategyJson);
        }
        ctx.response.put("strategies", strategies);

        ctx.response.put("fuelPerTick", ctx.structureContext.getStructure().material.fuelPerTick);
        return null;
    }
}
