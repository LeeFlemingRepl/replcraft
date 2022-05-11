package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.json.JSONArray;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

@WebsocketAction(route = "get_entities", permission = "replcraft.api.get_entities", cost = FuelCost.Expensive)
public class GetEntities implements WebsocketActionHandler {
    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        Block zero = client.getStructure().getBlock(0, 0, 0);
        Block max = client.getStructure().getBlock(
                client.getStructure().inner_size_x()-1,
                client.getStructure().inner_size_y()-1,
                client.getStructure().inner_size_z()-1
        );

        JSONArray entities = new JSONArray();
        for (Entity entity: zero.getWorld().getNearbyEntities(BoundingBox.of(zero, max))) {
            JSONObject entity_json = new JSONObject();
            entity_json.put("type", entity.getType());
            entity_json.put("name", entity.getName());
            if (entity instanceof LivingEntity) {
                LivingEntity live = (LivingEntity) entity;
                entity_json.put("health", live.getHealth());
                entity_json.put("max_health", live.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
            entity_json.put("x", entity.getLocation().getX() - client.getStructure().inner_min_x());
            entity_json.put("y", entity.getLocation().getY() - client.getStructure().inner_min_y());
            entity_json.put("z", entity.getLocation().getZ() - client.getStructure().inner_min_z());
            entities.put(entity_json);
        }
        response.put("entities", entities);
    }
}
