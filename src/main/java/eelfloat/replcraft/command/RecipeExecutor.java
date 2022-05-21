package eelfloat.replcraft.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;

public class RecipeExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String target = String.join(" ", args).trim();
        sender.sendMessage(String.format("Searching for recipes matching '%s'", target));
        if (target.length() == 0) return false;

        Iterator<Recipe> iter = Bukkit.recipeIterator();
        boolean found = false;
        while (iter.hasNext()) {
            Recipe next = iter.next();
            if (!target.equalsIgnoreCase(next.getResult().getType().toString()))
                continue;
            found = true;

            if (next instanceof ShapedRecipe) {
                sender.sendMessage("--- Recipe: shaped ---");
                sender.sendMessage("Map:");
                for (String s : ((ShapedRecipe) next).getShape())
                    sender.sendMessage("  " + s);

                sender.sendMessage("Keys:");
                for (Map.Entry<Character, ItemStack> entry : ((ShapedRecipe) next).getIngredientMap().entrySet())
                    sender.sendMessage(String.format("  %s: %s", entry.getKey(), entry.getValue().getType()));
            } else if (next instanceof ShapelessRecipe) {
                sender.sendMessage("--- Recipe: shapeless ---");
                for (ItemStack stack : ((ShapelessRecipe) next).getIngredientList())
                    sender.sendMessage(String.format("  " + stack.getType()));
            } else {
                sender.sendMessage("[Unimplemented recipe type: " + next.getClass().getName() + "]");
            }
            sender.sendMessage(String.format("Result: %s x%s", next.getResult().getType(), next.getResult().getAmount()));
        }

        if (!found) sender.sendMessage("No results found");
        return true;
    }
}
