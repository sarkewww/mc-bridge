package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

public class GUIHandler {

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }

    public static String handleScreen(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        ScreenHandler handler = player.currentScreenHandler;

        if (client.currentScreen == null) {
            result.addProperty("open", false);
            result.addProperty("error", "No screen open");
            return result.toString();
        }

        result.addProperty("open", true);
        result.addProperty("screenType", client.currentScreen.getClass().getSimpleName());
        result.addProperty("screenTitle", client.currentScreen.getTitle().getString());
        result.addProperty("handlerType", handler.getClass().getSimpleName());

        if (handler instanceof GenericContainerScreenHandler gch) {
            result.addProperty("containerType", "generic");
            result.addProperty("rows", gch.getRows());
        }

        if (handler instanceof MerchantScreenHandler msh) {
            result.addProperty("containerType", "merchant");
            result.addProperty("canRestock", true);
            JsonArray trades = new JsonArray();
            for (int i = 0; i < msh.getRecipes().size(); i++) {
                var recipe = msh.getRecipes().get(i);
                JsonObject t = new JsonObject();
                t.addProperty("index", i);
                t.addProperty("uses", recipe.getUses());
                t.addProperty("maxUses", recipe.getMaxUses());
                t.addProperty("xp", recipe.getMerchantExperience());
                t.addProperty("disabled", recipe.isDisabled());
                t.addProperty("priceMultiplier", recipe.getPriceMultiplier());
                t.addProperty("demandBonus", recipe.getDemandBonus());
                t.addProperty("specialPrice", recipe.getSpecialPrice());
                ItemStack buyA = recipe.getDisplayedFirstBuyItem();
                if (!buyA.isEmpty()) {
                    t.addProperty("buyA", buyA.getItem().toString());
                    t.addProperty("buyACount", buyA.getCount());
                }
                recipe.getSecondBuyItem().ifPresent(buyB -> {
                    t.addProperty("buyB", buyB.item().toString());
                    t.addProperty("buyBCount", buyB.count());
                });
                ItemStack sell = recipe.getSellItem();
                if (!sell.isEmpty()) {
                    t.addProperty("sell", sell.getItem().toString());
                    t.addProperty("sellCount", sell.getCount());
                }
                trades.add(t);
            }
            result.add("trades", trades);
        }

        if (handler instanceof AnvilScreenHandler ash) {
            result.addProperty("containerType", "anvil");
            result.addProperty("levelCost", ash.getLevelCost());
            if (!ash.getSlot(0).getStack().isEmpty()) {
                result.add("leftInput", itemStackToShortJson(ash.getSlot(0).getStack()));
            }
            if (!ash.getSlot(1).getStack().isEmpty()) {
                result.add("rightInput", itemStackToShortJson(ash.getSlot(1).getStack()));
            }
            if (!ash.getSlot(2).getStack().isEmpty()) {
                result.add("output", itemStackToShortJson(ash.getSlot(2).getStack()));
            }
        }

        if (handler instanceof BeaconScreenHandler bsh) {
            result.addProperty("containerType", "beacon");
            result.addProperty("levels", bsh.getProperties());
            result.addProperty("primaryEffect", bsh.getPrimaryEffect() != null ? bsh.getPrimaryEffect().getIdAsString() : "none");
            result.addProperty("secondaryEffect", bsh.getSecondaryEffect() != null ? bsh.getSecondaryEffect().getIdAsString() : "none");
            if (!bsh.getSlot(0).getStack().isEmpty()) {
                result.add("paymentItem", itemStackToShortJson(bsh.getSlot(0).getStack()));
            }
        }

        if (handler instanceof CraftingScreenHandler csh) {
            result.addProperty("containerType", "crafting");
            result.addProperty("craftingSlotCount", 9);
            JsonArray grid = new JsonArray();
            for (int i = 1; i <= 9; i++) {
                ItemStack s = csh.getSlot(i).getStack();
                if (!s.isEmpty()) {
                    JsonObject cell = itemStackToShortJson(s);
                    cell.addProperty("gridSlot", i - 1);
                    grid.add(cell);
                }
            }
            if (grid.size() > 0) result.add("craftingGrid", grid);
            if (!csh.getSlot(0).getStack().isEmpty()) {
                result.add("craftingOutput", itemStackToShortJson(csh.getSlot(0).getStack()));
            }
        }

        if (handler instanceof EnchantmentScreenHandler esh) {
            result.addProperty("containerType", "enchanting");
            result.addProperty("enchantmentSeed", esh.getSeed());
            JsonArray enchOptions = new JsonArray();
            if (esh.enchantmentLevel != null) {
                for (int i = 0; i < esh.enchantmentLevel.length; i++) {
                    JsonObject opt = new JsonObject();
                    opt.addProperty("slot", i);
                    opt.addProperty("level", esh.enchantmentLevel[i]);
                    opt.addProperty("enchantmentId", i < esh.enchantmentId.length ? String.valueOf(esh.enchantmentId[i]) : null);
                    enchOptions.add(opt);
                }
            }
            if (enchOptions.size() > 0) result.add("enchantmentOptions", enchOptions);
            if (!esh.getSlot(0).getStack().isEmpty()) result.add("item", itemStackToShortJson(esh.getSlot(0).getStack()));
            if (!esh.getSlot(1).getStack().isEmpty()) result.add("lapis", itemStackToShortJson(esh.getSlot(1).getStack()));
        }

        if (handler instanceof StonecutterScreenHandler scsh) {
            result.addProperty("containerType", "stonecutter");
            if (!scsh.getSlot(0).getStack().isEmpty()) {
                result.add("input", itemStackToShortJson(scsh.getSlot(0).getStack()));
            }
            int recipeCount = scsh.getAvailableRecipeCount();
            result.addProperty("recipeCount", recipeCount);
        }

        if (handler instanceof SmithingScreenHandler ssh) {
            result.addProperty("containerType", "smithing");
            if (!ssh.getSlot(0).getStack().isEmpty()) result.add("template", itemStackToShortJson(ssh.getSlot(0).getStack()));
            if (!ssh.getSlot(1).getStack().isEmpty()) result.add("base", itemStackToShortJson(ssh.getSlot(1).getStack()));
            if (!ssh.getSlot(2).getStack().isEmpty()) result.add("addition", itemStackToShortJson(ssh.getSlot(2).getStack()));
            if (!ssh.getSlot(3).getStack().isEmpty()) result.add("output", itemStackToShortJson(ssh.getSlot(3).getStack()));
        }

        if (handler instanceof LoomScreenHandler lsh) {
            result.addProperty("containerType", "loom");
            if (!lsh.getSlot(0).getStack().isEmpty()) result.add("banner", itemStackToShortJson(lsh.getSlot(0).getStack()));
            if (!lsh.getSlot(1).getStack().isEmpty()) result.add("dye", itemStackToShortJson(lsh.getSlot(1).getStack()));
            if (!lsh.getSlot(2).getStack().isEmpty()) result.add("pattern", itemStackToShortJson(lsh.getSlot(2).getStack()));
            if (!lsh.getSlot(3).getStack().isEmpty()) result.add("output", itemStackToShortJson(lsh.getSlot(3).getStack()));
        }

        if (handler instanceof CartographyTableScreenHandler ctsh) {
            result.addProperty("containerType", "cartography");
            if (!ctsh.getSlot(0).getStack().isEmpty()) result.add("input", itemStackToShortJson(ctsh.getSlot(0).getStack()));
            if (!ctsh.getSlot(1).getStack().isEmpty()) result.add("additional", itemStackToShortJson(ctsh.getSlot(1).getStack()));
            if (!ctsh.getSlot(2).getStack().isEmpty()) result.add("output", itemStackToShortJson(ctsh.getSlot(2).getStack()));
        }

        if (handler instanceof GrindstoneScreenHandler gsh) {
            result.addProperty("containerType", "grindstone");
            if (!gsh.getSlot(0).getStack().isEmpty()) result.add("inputA", itemStackToShortJson(gsh.getSlot(0).getStack()));
            if (!gsh.getSlot(1).getStack().isEmpty()) result.add("inputB", itemStackToShortJson(gsh.getSlot(1).getStack()));
            if (!gsh.getSlot(2).getStack().isEmpty()) result.add("output", itemStackToShortJson(gsh.getSlot(2).getStack()));
        }

        if (handler instanceof BrewingStandScreenHandler bssh) {
            result.addProperty("containerType", "brewing");
            result.addProperty("fuel", bssh.getFuel());
            if (!bssh.getSlot(0).getStack().isEmpty()) result.add("ingredient", itemStackToShortJson(bssh.getSlot(0).getStack()));
            JsonArray bottles = new JsonArray();
            for (int i = 1; i <= 3; i++) {
                if (!bssh.getSlot(i).getStack().isEmpty()) {
                    bottles.add(itemStackToShortJson(bssh.getSlot(i).getStack()));
                }
            }
            if (bottles.size() > 0) result.add("bottles", bottles);
        }

        if (handler instanceof AbstractFurnaceScreenHandler afsh) {
            result.addProperty("containerType", "furnace");
            result.addProperty("furnaceType", handler instanceof SmokerScreenHandler ? "smoker"
                    : handler instanceof BlastFurnaceScreenHandler ? "blast_furnace" : "furnace");
            result.addProperty("cookProgress", afsh.getCookProgress());
            result.addProperty("fuelProgress", afsh.getFuelProgress());
            result.addProperty("isBurning", afsh.isBurning());
            if (!afsh.getSlot(0).getStack().isEmpty()) result.add("input", itemStackToShortJson(afsh.getSlot(0).getStack()));
            if (!afsh.getSlot(1).getStack().isEmpty()) result.add("fuel", itemStackToShortJson(afsh.getSlot(1).getStack()));
            if (!afsh.getSlot(2).getStack().isEmpty()) result.add("output", itemStackToShortJson(afsh.getSlot(2).getStack()));
        }

        if (handler instanceof HopperScreenHandler hsh) {
            result.addProperty("containerType", "hopper");
        }

        if (handler instanceof Generic3x3ContainerScreenHandler) {
            result.addProperty("containerType", "generic_3x3");
        }

        if (handler instanceof HorseScreenHandler hs) {
            result.addProperty("containerType", "horse");
            result.addProperty("horseSlotCount", hs.slots.size() - 41);
        }

        if (handler instanceof PlayerScreenHandler psh) {
            result.addProperty("containerType", "player_inventory");
            if (!psh.getCraftingInput().getStack(0).isEmpty() || !psh.getCraftingInput().getStack(1).isEmpty() ||
                !psh.getCraftingInput().getStack(2).isEmpty() || !psh.getCraftingInput().getStack(3).isEmpty()) {
                JsonArray grid = new JsonArray();
                for (int i = 0; i < 4; i++) {
                    ItemStack s = psh.getCraftingInput().getStack(i);
                    if (!s.isEmpty()) {
                        JsonObject cell = itemStackToShortJson(s);
                        cell.addProperty("gridSlot", i);
                        grid.add(cell);
                    }
                }
                result.add("craftingGrid", grid);
            }
            int resultSlotIdx = psh.getCraftingResultSlotIndex();
            if (resultSlotIdx >= 0 && resultSlotIdx < psh.slots.size()) {
                ItemStack craftResult = psh.slots.get(resultSlotIdx).getStack();
                if (!craftResult.isEmpty()) {
                    result.add("craftingOutput", itemStackToShortJson(craftResult));
                }
            }
        }

        JsonArray allItems = new JsonArray();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty()) {
                JsonObject item = new JsonObject();
                item.addProperty("slot", i);
                item.addProperty("name", stack.getItem().toString());
                item.addProperty("count", stack.getCount());
                item.addProperty("displayName", stack.getName().getString());
                allItems.add(item);
            }
        }
        result.add("allItems", allItems);

        ItemStack cursor = handler.getCursorStack();
        if (!cursor.isEmpty()) {
            result.addProperty("cursorItem", cursor.getItem().toString());
            result.addProperty("cursorCount", cursor.getCount());
        }

        return result.toString();
    }

    public static String handleContainer(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        ScreenHandler handler = player.currentScreenHandler;

        if (handler == null) {
            result.addProperty("open", false);
            result.addProperty("error", "No container open");
            return result.toString();
        }

        if (client.currentScreen != null) {
            result.addProperty("screenType", client.currentScreen.getClass().getSimpleName());
            result.addProperty("screenTitle", client.currentScreen.getTitle().getString());
        }

        result.addProperty("open", true);
        result.addProperty("slots", handler.slots.size());

        List<Slot> allSlots = handler.slots;
        JsonArray items = new JsonArray();
        for (int i = 0; i < allSlots.size(); i++) {
            Slot slot = allSlots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                JsonObject item = new JsonObject();
                item.addProperty("slot", i);
                item.addProperty("name", stack.getItem().toString());
                item.addProperty("count", stack.getCount());
                item.addProperty("maxCount", stack.getMaxCount());
                item.addProperty("displayName", stack.getName().getString());
                if (stack.isDamageable()) {
                    item.addProperty("damage", stack.getDamage());
                    item.addProperty("maxDamage", stack.getMaxDamage());
                }

                ItemEnchantmentsComponent ench = stack.get(DataComponentTypes.ENCHANTMENTS);
                if (ench != null && !ench.isEmpty()) {
                    JsonArray enchList = new JsonArray();
                    for (var entry : ench.getEnchantments()) {
                        JsonObject e = new JsonObject();
                        e.addProperty("id", entry.getIdAsString());
                        e.addProperty("level", ench.getLevel(entry));
                        enchList.add(e);
                    }
                    item.add("enchantments", enchList);
                }

                items.add(item);
            }
        }
        result.add("items", items);

        ItemStack cursor = handler.getCursorStack();
        if (!cursor.isEmpty()) {
            result.addProperty("cursorItem", cursor.getItem().toString());
            result.addProperty("cursorCount", cursor.getCount());
        }

        return result.toString();
    }

    public static String handleGuiClick(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("slot")) throw new Exception("Missing 'slot' parameter");
        int slotIndex = json.get("slot").getAsInt();
        int button = json.has("button") ? json.get("button").getAsInt() : 0;
        String actionStr = json.has("action") ? json.get("action").getAsString() : "PICKUP";
        SlotActionType action;
        try {
            action = SlotActionType.valueOf(actionStr.toUpperCase());
        } catch (Exception e) {
            throw new Exception("Invalid action: " + actionStr + " (PICKUP/QUICK_MOVE/THROW/SWAP/CLONE/UNKNOWN)");
        }

        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null) throw new Exception("No screen open");

        client.interactionManager.clickSlot(handler.syncId, slotIndex, button, action, player);

        JsonObject result = new JsonObject();
        result.addProperty("clicked", true);
        result.addProperty("slot", slotIndex);
        result.addProperty("action", actionStr);
        return result.toString();
    }

    public static String handleTrade(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("index")) throw new Exception("Missing 'index' parameter (trade slot)");
        int index = json.get("index").getAsInt();

        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof MerchantScreenHandler msh)) {
            throw new Exception("Not a merchant/villager screen");
        }

        if (index < 0 || index >= msh.getRecipes().size()) {
            throw new Exception("Invalid trade index: " + index + " (0-" + (msh.getRecipes().size() - 1) + ")");
        }

        var recipe = msh.getRecipes().get(index);
        var buyA = recipe.getDisplayedFirstBuyItem();
        var sell = recipe.getSellItem();

        final int syncId = msh.syncId;
        final int finalIndex = index;
        final var finalRecipe = recipe;
        client.execute(() -> {
            try {
                ClientPlayerEntity p = client.player;
                if (p == null) return;
                var mgr = client.interactionManager;
                if (mgr == null) return;

                p.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(finalIndex));

                clearBuySlot(mgr, syncId, 0, p);
                clearBuySlot(mgr, syncId, 1, p);

                ItemStack reqA = finalRecipe.getDisplayedFirstBuyItem();
                if (!reqA.isEmpty()) {
                    fillBuySlotFromInventory(mgr, syncId, 0, reqA, p);
                }

                ItemStack reqB = finalRecipe.getDisplayedSecondBuyItem();
                if (!reqB.isEmpty()) {
                    fillBuySlotFromInventory(mgr, syncId, 1, reqB, p);
                }

                mgr.clickSlot(syncId, 2, 0, SlotActionType.QUICK_MOVE, p);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        JsonObject result = new JsonObject();
        result.addProperty("traded", true);
        result.addProperty("index", index);
        result.addProperty("buy", buyA.getItem().toString() + " x" + buyA.getCount());
        result.addProperty("sell", sell.getItem().toString() + " x" + sell.getCount());

        return result.toString();
    }

    private static void clearBuySlot(ClientPlayerInteractionManager mgr, int syncId, int slot, ClientPlayerEntity player) {
        if (mgr == null) return;
        mgr.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, player);
        mgr.clickSlot(syncId, 3, 0, SlotActionType.PICKUP, player);
    }

    private static void fillBuySlotFromInventory(ClientPlayerInteractionManager mgr, int syncId, int buySlot, ItemStack required, ClientPlayerEntity player) {
        int need = required.getCount();
        if (need <= 0) return;
        var inv = player.getInventory();

        for (int si = 0; si < inv.main.size(); si++) {
            ItemStack st = inv.main.get(si);
            if (st.isEmpty() || !st.isOf(required.getItem())) continue;
            int take = Math.min(need, st.getCount());
            if (take <= 0) continue;

            int screenSlot = toScreenSlot(si);

            mgr.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
            for (int c = 0; c < take; c++) {
                mgr.clickSlot(syncId, buySlot, 1, SlotActionType.PICKUP, player);
            }
            mgr.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);

            need -= take;
            if (need <= 0) return;
        }

        ItemStack off = inv.offHand.get(0);
        if (need > 0 && !off.isEmpty() && off.isOf(required.getItem())) {
            int take = Math.min(need, off.getCount());
            mgr.clickSlot(syncId, 40, 0, SlotActionType.PICKUP, player);
            for (int c = 0; c < take; c++) {
                mgr.clickSlot(syncId, buySlot, 1, SlotActionType.PICKUP, player);
            }
            mgr.clickSlot(syncId, 40, 0, SlotActionType.PICKUP, player);
        }
    }

    private static int toScreenSlot(int invIndex) {
        if (invIndex < 9) return 30 + invIndex;
        return invIndex - 9 + 3;
    }

    public static String handleMoveItem(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("from") || !json.has("to")) {
            throw new Exception("Missing 'from' or 'to' slot parameter");
        }
        int fromSlot = json.get("from").getAsInt();
        int toSlot = json.get("to").getAsInt();
        int count = json.has("count") ? json.get("count").getAsInt() : -1;

        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null) throw new Exception("No container open");

        client.interactionManager.clickSlot(handler.syncId, fromSlot, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, player);
        if (!handler.getCursorStack().isEmpty()) {
            client.interactionManager.clickSlot(handler.syncId, fromSlot, 0, SlotActionType.PICKUP, player);
        }

        JsonObject result = new JsonObject();
        result.addProperty("from", fromSlot);
        result.addProperty("to", toSlot);
        result.addProperty("moved", true);
        return result.toString();
    }

    public static String handleDropItem(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("slot")) throw new Exception("Missing 'slot' parameter");
        int slotIndex = json.get("slot").getAsInt();

        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null) throw new Exception("No container open");

        client.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.THROW, player);

        JsonObject result = new JsonObject();
        result.addProperty("slot", slotIndex);
        result.addProperty("dropped", true);
        return result.toString();
    }

    public static String handleEquipItem(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("slot")) throw new Exception("Missing 'slot' parameter");
        int slotIndex = json.get("slot").getAsInt();
        String equipSlot = json.has("equipment_slot") ? json.get("equipment_slot").getAsString() : "mainhand";

        int targetSlot;
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null) throw new Exception("No container open");

        targetSlot = switch (equipSlot.toLowerCase()) {
            case "head", "helmet" -> 5;
            case "chest", "chestplate" -> 6;
            case "legs", "leggings" -> 7;
            case "feet", "boots" -> 8;
            case "offhand" -> 45;
            default -> player.getInventory().selectedSlot + 36;
        };

        client.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, player);
        if (!handler.getCursorStack().isEmpty()) {
            client.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, player);
        }

        JsonObject result = new JsonObject();
        result.addProperty("slot", slotIndex);
        result.addProperty("equipmentSlot", equipSlot);
        result.addProperty("equipped", true);
        return result.toString();
    }

    public static String handleCraftItem(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("item")) throw new Exception("Missing 'item' parameter");

        String targetItem = json.get("item").getAsString().toLowerCase(Locale.ROOT);
        int craftCount = json.has("count") ? json.get("count").getAsInt() : 1;

        JsonObject result = new JsonObject();
        result.addProperty("item", targetItem);
        result.addProperty("count", craftCount);

        var handler = player.currentScreenHandler;
        if (handler instanceof CraftingScreenHandler csh) {
            result.addProperty("screenOpen", "crafting_table");
            result.addProperty("status", "ready");
            result.addProperty("info", "Open crafting table GUI then use mc_gui_click to place ingredients manually");

            JsonArray slots = new JsonArray();
            for (int i = 0; i < handler.slots.size(); i++) {
                ItemStack stack = handler.slots.get(i).getStack();
                if (!stack.isEmpty()) {
                    JsonObject slot = new JsonObject();
                    slot.addProperty("slot", i);
                    slot.addProperty("name", stack.getItem().toString());
                    slot.addProperty("count", stack.getCount());
                    slots.add(slot);
                }
            }
            result.add("slots", slots);

        } else if (handler instanceof PlayerScreenHandler psh) {
            result.addProperty("screenOpen", "player_inventory");
            result.addProperty("craftingSlots", "2x2");
            result.addProperty("info", "Use mc_gui_click to place items in crafting grid (slots 1-4) then collect from slot 0");
        } else {
            result.addProperty("screenOpen", false);
            result.addProperty("info", "Open a crafting table first, then use mc_craft_item again with the crafting table open");
        }

        return result.toString();
    }

    public static String handleRefill(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        var handler = player.currentScreenHandler;
        if (handler == null) return errorJson("No container open");

        int hotbarStart;
        int inventoryStart;
        if (handler instanceof PlayerScreenHandler) {
            hotbarStart = 36;
            inventoryStart = 9;
        } else {
            int total = handler.slots.size();
            hotbarStart = total - 9;
            inventoryStart = total - 36;
        }

        int refilled = 0;
        JsonArray actions = new JsonArray();

        int threshold = json.has("threshold") ? json.get("threshold").getAsInt() : 16;
        boolean checkContainer = json.has("container") && json.get("container").getAsBoolean();

        for (int h = 0; h < 9; h++) {
            int hotbarSlot = hotbarStart + h;
            ItemStack hotbarStack = handler.slots.get(hotbarSlot).getStack();
            if (hotbarStack.isEmpty()) continue;

            String itemName = hotbarStack.getItem().toString();
            int currentCount = hotbarStack.getCount();
            int maxCount = hotbarStack.getMaxCount();

            if (currentCount >= threshold || currentCount >= maxCount) continue;

            // Search main inventory first
            for (int s = inventoryStart; s < hotbarStart; s++) {
                if (s == hotbarSlot) continue;
                ItemStack stack = handler.slots.get(s).getStack();
                if (stack.isEmpty()) continue;
                if (stack.getItem().toString().equals(itemName)) {
                    int moveCount = Math.min(stack.getCount(), maxCount - currentCount);
                    JsonObject action = new JsonObject();
                    action.addProperty("fromSlot", s);
                    action.addProperty("toSlot", hotbarSlot);
                    action.addProperty("count", moveCount);
                    actions.add(action);
                    refilled++;
                    break;
                }
            }
            // If checkContainer, also search container slots
            if (checkContainer && refilled == 0 && !(handler instanceof PlayerScreenHandler)) {
                for (int s = 0; s < inventoryStart; s++) {
                    ItemStack stack = handler.slots.get(s).getStack();
                    if (stack.isEmpty()) continue;
                    if (stack.getItem().toString().equals(itemName)) {
                        int moveCount = Math.min(stack.getCount(), maxCount - currentCount);
                        JsonObject action = new JsonObject();
                        action.addProperty("fromSlot", s);
                        action.addProperty("toSlot", hotbarSlot);
                        action.addProperty("count", moveCount);
                        actions.add(action);
                        refilled++;
                        break;
                    }
                }
            }
        }

        result.addProperty("refilled", refilled);
        result.add("actions", actions);
        result.addProperty("info", refilled > 0
                ? "Use mc_move_item with each action to refill hotbar"
                : "All hotbar slots have sufficient items");

        return result.toString();
    }

    public static String handleSortInventory(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        var interactionManager = client.interactionManager;
        if (interactionManager == null) return errorJson("Not in game");

        var handler = player.currentScreenHandler;
        if (handler == null) return errorJson("No container open");

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            int slotIndex = i < 9 ? i + 36 : i;
            ItemStack stack = handler.slots.get(slotIndex).getStack();
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }

        items.sort((a, b) -> {
            int cmp = a.getItem().toString().compareTo(b.getItem().toString());
            if (cmp == 0) cmp = Integer.compare(b.getCount(), a.getCount());
            return cmp;
        });

        JsonArray sorted = new JsonArray();
        for (ItemStack stack : items) {
            JsonObject item = new JsonObject();
            item.addProperty("name", stack.getItem().toString());
            item.addProperty("count", stack.getCount());
            item.addProperty("displayName", stack.getName().getString());
            sorted.add(item);
        }
        result.add("sortedPreview", sorted);
        result.addProperty("note", "Use mc_move_item + mc_gui_click to physically sort. This is a preview.");

        return result.toString();
    }

    public static String handleExplainScreen(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        var currentScreen = client.currentScreen;
        if (currentScreen == null) {
            result.addProperty("open", false);
            result.addProperty("info", "No screen open");
            return result.toString();
        }

        result.addProperty("open", true);
        result.addProperty("screenType", currentScreen.getClass().getSimpleName());
        result.addProperty("title", currentScreen.getTitle().getString());

        var handler = player.currentScreenHandler;
        result.addProperty("handlerType", handler.getClass().getSimpleName());

        String explanation;
        if (handler instanceof MerchantScreenHandler) {
            explanation = "Villager trading screen. Select a trade from the list on the left, "
                    + "then click the 'Trade' button (or shift-click) to execute. "
                    + "Each trade shows buy items (left) and sell item (right). "
                    + "Use mc_trade(index) to execute a trade by index.";
        } else if (handler instanceof AnvilScreenHandler ash) {
            int cost = ash.getLevelCost();
            String left = handler.getSlot(0).getStack().isEmpty() ? "empty" : handler.getSlot(0).getStack().getName().getString();
            String right = handler.getSlot(1).getStack().isEmpty() ? "empty" : handler.getSlot(1).getStack().getName().getString();
            explanation = "Anvil screen. Left slot: " + left + ". Right slot: " + right
                    + ". Level cost: " + cost + ". "
                    + "Place an item in left slot, enchanted book/second item in right slot. "
                    + "Take result from output slot.";
        } else if (handler instanceof EnchantmentScreenHandler) {
            explanation = "Enchanting table. Place an item in the upper slot and lapis lazuli in the lower slot. "
                    + "Three enchantment options appear; higher levels require more bookshelves. "
                    + "Click an option to enchant.";
        } else if (handler instanceof BeaconScreenHandler) {
            explanation = "Beacon GUI. Place a consumable item (ingot/gem) in the payment slot. "
                    + "Select primary and secondary effects from the lists. "
                    + "Higher beacon tiers unlock more effects.";
        } else if (handler instanceof CraftingScreenHandler) {
            explanation = "Crafting table (3x3 grid). Place ingredients matching a recipe pattern. "
                    + "Result appears in the right slot. Shift-click or click to take it. "
                    + "Use mc_craft_item to get recipe guidance.";
        } else if (handler instanceof FurnaceScreenHandler || handler instanceof SmokerScreenHandler || handler instanceof BlastFurnaceScreenHandler) {
            explanation = "Furnace screen. Top slot: item to smelt/cook. Bottom slot: fuel. "
                    + "Right slot: output. Arrow shows progress.";
        } else if (handler instanceof BrewingStandScreenHandler) {
            explanation = "Brewing stand. Bottom slot: blaze powder fuel. Top slots: water bottles. "
                    + "Top row input: ingredient (nether wart, glowstone, etc.).";
        } else if (handler instanceof GenericContainerScreenHandler) {
            explanation = "Chest/container. Double-click items to collect them. "
                    + "Shift-click to move between container and inventory. "
                    + "Use mc_get_container to read all items.";
        } else if (handler instanceof StonecutterScreenHandler) {
            explanation = "Stonecutter. Place a stone-type block in the left slot. "
                    + "Click one of the recipes on the right to craft.";
        } else if (handler instanceof SmithingScreenHandler) {
            explanation = "Smithing table. From left to right: template, base item, addition material. "
                    + "Result appears in the rightmost slot.";
        } else if (handler instanceof LoomScreenHandler) {
            explanation = "Loom. Place a banner in the left slot, dye in the middle. "
                    + "Select a pattern from the list to apply.";
        } else if (handler instanceof CartographyTableScreenHandler) {
            explanation = "Cartography table. Place a map + paper (expand) or map + glass pane (lock). "
                    + "Add a compass to create a locator map.";
        } else if (handler instanceof GrindstoneScreenHandler) {
            explanation = "Grindstone. Place two items to disenchant. Removes non-curse enchantments "
                    + "and returns some experience. Also repairs items.";
        } else if (handler instanceof HorseScreenHandler) {
            explanation = "Horse inventory. Slot 0: saddle. Slot 1: horse armor. "
                    + "Remaining slots are the horse's inventory.";
        } else if (handler instanceof PlayerScreenHandler) {
            explanation = "Player inventory. 2x2 crafting grid (top-left), 4 armor slots (left), "
                    + "27 main inventory slots, 9 hotbar slots. Use mc_get_inventory to read.";
        } else {
            explanation = handler.getClass().getSimpleName() + " screen. Use mc_get_screen to read details, "
                    + "mc_gui_click(slot) to interact, or mc_get_container for items.";
        }

        result.addProperty("explanation", explanation);
        return result.toString();
    }

    public static String handleAnalyzeInventory(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();

        var handler = player.currentScreenHandler;
        if (handler == null) return errorJson("No container open");

        int totalSlots = handler.slots.size();
        int emptySlots = 0;
        int uniqueItems = 0;
        int totalItems = 0;
        Set<String> itemTypes = new HashSet<>();
        JsonArray valuableItems = new JsonArray();

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) {
                emptySlots++;
                continue;
            }
            String itemId = stack.getItem().toString();
            itemTypes.add(itemId);
            totalItems += stack.getCount();

            String name = stack.getItem().toString().toLowerCase(Locale.ROOT);
            boolean valuable = name.contains("diamond") || name.contains("netherite")
                    || name.contains("emerald") || name.contains("gold_ingot")
                    || name.contains("iron_ingot") || name.contains("enchanted")
                    || name.contains("trident") || name.contains("elytra")
                    || name.contains("beacon") || name.contains("totem")
                    || name.contains("shulker") || name.contains("spawner");
            if (valuable) {
                JsonObject v = new JsonObject();
                v.addProperty("slot", i);
                v.addProperty("name", stack.getItem().toString());
                v.addProperty("count", stack.getCount());
                v.addProperty("displayName", stack.getName().getString());
                valuableItems.add(v);
            }
        }

        uniqueItems = itemTypes.size();

        result.addProperty("totalSlots", totalSlots);
        result.addProperty("emptySlots", emptySlots);
        result.addProperty("usedSlots", totalSlots - emptySlots);
        result.addProperty("uniqueItemTypes", uniqueItems);
        result.addProperty("totalItems", totalItems);
        result.add("valuableItems", valuableItems);

        StringBuilder summary = new StringBuilder();
        summary.append("Inventory: ").append(totalSlots - emptySlots).append("/").append(totalSlots).append(" slots used, ")
                .append(totalItems).append(" total items, ")
                .append(uniqueItems).append(" unique types. ");
        if (emptySlots > totalSlots * 0.5) {
            summary.append("Plenty of free space.");
        } else if (emptySlots < 5) {
            summary.append("Inventory is nearly full.");
        } else {
            summary.append("Moderate free space.");
        }
        if (valuableItems.size() > 0) {
            summary.append(" ").append(valuableItems.size()).append(" valuable items detected.");
        }
        result.addProperty("summary", summary.toString());

        return result.toString();
    }

    private static JsonObject itemStackToShortJson(ItemStack stack) {
        JsonObject obj = new JsonObject();
        obj.addProperty("item", stack.getItem().toString());
        obj.addProperty("count", stack.getCount());
        obj.addProperty("displayName", stack.getName().getString());
        obj.addProperty("maxCount", stack.getMaxCount());
        obj.addProperty("hasCustomName", stack.get(DataComponentTypes.CUSTOM_NAME) != null);
        if (stack.isDamageable()) {
            obj.addProperty("damage", stack.getDamage());
            obj.addProperty("maxDamage", stack.getMaxDamage());
        }
        ItemEnchantmentsComponent ench = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            JsonArray enchList = new JsonArray();
            for (var entry : ench.getEnchantments()) {
                JsonObject e = new JsonObject();
                e.addProperty("id", entry.getIdAsString());
                e.addProperty("level", ench.getLevel(entry));
                enchList.add(e);
            }
            obj.add("enchantments", enchList);
        }
        return obj;
    }
}
