package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

@WebsocketAction(route = "get_sign_text", permission = "replcraft.api.get_sign_text", cost = FuelCost.Regular)
public class GetSignText implements WebsocketActionHandler {
    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        BlockState state = getBlock(client, request).getState();
        if (!(state instanceof Sign)) {
            throw new ApiError("invalid operation", "block is not a sign");
        }
        response.put("lines", ((Sign) state).getLines());
    }
}
