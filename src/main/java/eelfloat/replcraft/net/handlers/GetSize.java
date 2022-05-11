package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetSize implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_size";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_size";
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
        response.put("x", client.getStructure().inner_size_x());
        response.put("y", client.getStructure().inner_size_y());
        response.put("z", client.getStructure().inner_size_z());
    }
}
