package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetPowerLevel implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_power_level";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_power_level";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.Regular;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        response.put("power", getBlock(client, request).getBlockPower());
    }
}
