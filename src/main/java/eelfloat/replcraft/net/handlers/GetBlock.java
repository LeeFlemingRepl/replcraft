package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

@WebsocketAction(route = "get_block", permission = "replcraft.api.get_block", cost = FuelCost.Regular)
public class GetBlock implements WebsocketActionHandler {
    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        response.put("block", getBlock(client, request).getBlockData().getAsString());
    }
}
