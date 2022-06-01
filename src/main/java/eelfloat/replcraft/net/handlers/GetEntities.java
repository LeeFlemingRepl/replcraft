package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.json.JSONArray;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetEntities implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_entities";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_entities";
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
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        Block zero = ctx.client.getStructure().getBlock(0, 0, 0);
        Block max = ctx.client.getStructure().getBlock(
                ctx.client.getStructure().inner_size_x()-1,
                ctx.client.getStructure().inner_size_y()-1,
                ctx.client.getStructure().inner_size_z()-1
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
            if (entity instanceof Player) {
                entity_json.put("player_uuid", entity.getUniqueId());
            }
            entity_json.put("x", entity.getLocation().getX() - ctx.client.getStructure().inner_min_x());
            entity_json.put("y", entity.getLocation().getY() - ctx.client.getStructure().inner_min_y());
            entity_json.put("z", entity.getLocation().getZ() - ctx.client.getStructure().inner_min_z());
            entities.put(entity_json);
        }
        ctx.response.put("entities", entities);
        return null;
    }
}
