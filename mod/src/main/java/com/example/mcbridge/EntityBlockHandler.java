package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.block.BlockState;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.fluid.FluidState;
import net.minecraft.state.property.Properties;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class EntityBlockHandler {

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String errorJson(String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        return err.toString();
    }

    public static String handleEntities(JsonObject json, MinecraftClient client) throws Exception {
        ClientPlayerEntity player = client.player;
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        double radius = json.has("r") ? json.get("r").getAsDouble() : 16;
        double radiusSq = radius * radius;
        Vec3d playerPos = player.getPos();

        JsonArray list = new JsonArray();
        for (var entity : world.getEntities()) {
            if (entity == player) continue;
            double distSq = entity.getPos().squaredDistanceTo(playerPos);
            if (distSq > radiusSq) continue;

            JsonObject obj = new JsonObject();
            obj.addProperty("id", entity.getId());
            obj.addProperty("name", entity.getName().getString());
            obj.addProperty("type", entity.getType().getName().getString());
            Vec3d ep = entity.getPos();
            obj.addProperty("x", round(ep.x, 1));
            obj.addProperty("y", round(ep.y, 1));
            obj.addProperty("z", round(ep.z, 1));
            obj.addProperty("distance", round(Math.sqrt(distSq), 1));

            if (entity instanceof PlayerEntity pe) {
                obj.addProperty("health", round(pe.getHealth(), 1));
                obj.addProperty("gameMode", pe.isSpectator() ? "spectator" : pe.isCreative() ? "creative" : "unknown");
                JsonObject pePos = new JsonObject();
                pePos.addProperty("x", round(ep.x, 1));
                pePos.addProperty("y", round(ep.y, 1));
                pePos.addProperty("z", round(ep.z, 1));
                obj.add("position", pePos);
            }
            list.add(obj);
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", list.size());
        result.add("entities", list);
        return result.toString();
    }

    public static String handleEntity(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        int id;
        if (json.has("id")) {
            id = json.get("id").getAsInt();
        } else if (json.has("look")) {
            ClientPlayerEntity player = client.player;
            double reach = player.isCreative() ? 5.0 : 4.5;
            Entity hit = client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY
                    ? ((EntityHitResult) client.crosshairTarget).getEntity()
                    : null;
            if (hit == null) throw new Exception("Not looking at any entity");
            id = hit.getId();
        } else {
            throw new Exception("Missing 'id' parameter (entity ID) or 'look' flag");
        }

        Entity entity = world.getEntityById(id);
        if (entity == null) throw new Exception("Entity #" + id + " not found");

        JsonObject result = new JsonObject();
        result.addProperty("id", entity.getId());
        result.addProperty("name", entity.getName().getString());
        result.addProperty("type", entity.getType().getName().getString());
        result.addProperty("uuid", entity.getUuid().toString());
        result.addProperty("x", round(entity.getPos().x, 2));
        result.addProperty("y", round(entity.getPos().y, 2));
        result.addProperty("z", round(entity.getPos().z, 2));
        result.addProperty("yaw", round(entity.getYaw(), 1));
        result.addProperty("pitch", round(entity.getPitch(), 1));
        result.addProperty("onGround", entity.isOnGround());
        result.addProperty("dimension", entity.getWorld().getRegistryKey().getValue().toString());
        result.addProperty("distance", round(entity.getPos().distanceTo(client.player.getPos()), 1));

        if (entity instanceof LivingEntity living) {
            result.addProperty("health", round(living.getHealth(), 1));
            result.addProperty("maxHealth", round(living.getMaxHealth(), 1));
            result.addProperty("armor", living.getArmor());
            result.addProperty("age", entity.age);

            JsonArray equipment = new JsonArray();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = living.getEquippedStack(slot);
                if (!stack.isEmpty()) {
                    JsonObject eq = new JsonObject();
                    eq.addProperty("slot", slot.getName());
                    eq.addProperty("item", stack.getItem().toString());
                    eq.addProperty("name", stack.getName().getString());
                    equipment.add(eq);
                }
            }
            if (equipment.size() > 0) result.add("equipment", equipment);

            JsonArray effects = new JsonArray();
            for (StatusEffectInstance effect : living.getStatusEffects()) {
                JsonObject ef = new JsonObject();
                ef.addProperty("id", effect.getEffectType().getIdAsString());
                ef.addProperty("amplifier", effect.getAmplifier());
                ef.addProperty("duration", effect.getDuration());
                ef.addProperty("ambient", effect.isAmbient());
                ef.addProperty("showParticles", effect.shouldShowParticles());
                effects.add(ef);
            }
            if (effects.size() > 0) result.add("statusEffects", effects);
        }

        if (entity instanceof PlayerEntity pe) {
            result.addProperty("gameMode", pe.isSpectator() ? "spectator" : pe.isCreative() ? "creative" : "survival");
        }

        try {
            NbtCompound nbt = new NbtCompound();
            entity.writeNbt(nbt);
            String nbtStr = nbt.toString();
            if (nbtStr.length() > 5000) nbtStr = nbtStr.substring(0, 5000) + "... (truncated)";
            result.addProperty("nbt", nbtStr);
        } catch (Exception e) {
            result.addProperty("nbtError", e.getMessage());
        }

        return result.toString();
    }

    public static String handleEntityDetail(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        int id;
        if (json.has("id")) {
            id = json.get("id").getAsInt();
        } else if (json.has("look")) {
            ClientPlayerEntity player = client.player;
            if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                id = ((EntityHitResult) client.crosshairTarget).getEntity().getId();
            } else {
                throw new Exception("Not looking at any entity");
            }
        } else {
            throw new Exception("Missing 'id' or 'look'");
        }

        Entity entity = world.getEntityById(id);
        if (entity == null) throw new Exception("Entity #" + id + " not found");

        JsonObject result = new JsonObject();
        result.addProperty("id", entity.getId());
        result.addProperty("name", entity.getName().getString());
        result.addProperty("type", entity.getType().getName().getString());
        result.addProperty("uuid", entity.getUuid().toString());
        result.addProperty("x", round(entity.getPos().x, 2));
        result.addProperty("y", round(entity.getPos().y, 2));
        result.addProperty("z", round(entity.getPos().z, 2));
        result.addProperty("yaw", round(entity.getYaw(), 1));
        result.addProperty("pitch", round(entity.getPitch(), 1));
        result.addProperty("onGround", entity.isOnGround());
        result.addProperty("dimension", entity.getWorld().getRegistryKey().getValue().toString());
        result.addProperty("distance", round(entity.getPos().distanceTo(client.player.getPos()), 1));
        result.addProperty("age", entity.age);
        result.addProperty("alive", entity.isAlive());
        result.addProperty("sneaking", entity.isSneaking());
        result.addProperty("sprinting", entity.isSprinting());
        result.addProperty("glowing", entity.isGlowing());
        result.addProperty("invisible", entity.isInvisible());
        result.addProperty("touchingWater", entity.isTouchingWater());
        result.addProperty("fallDistance", round(entity.fallDistance, 2));
        result.addProperty("fireTicks", entity.getFireTicks());
        result.addProperty("air", entity.getAir());
        result.addProperty("maxAir", entity.getMaxAir());
        result.addProperty("width", entity.getWidth());
        result.addProperty("height", entity.getHeight());
        result.addProperty("eyeHeight", entity.getEyeHeight(entity.getPose()));

        if (entity.hasVehicle()) {
            Entity v = entity.getVehicle();
            JsonObject vObj = new JsonObject();
            vObj.addProperty("id", v.getId());
            vObj.addProperty("name", v.getName().getString());
            vObj.addProperty("type", v.getType().getName().getString());
            result.add("vehicle", vObj);
        }

        if (entity.hasPassengers()) {
            JsonArray pass = new JsonArray();
            for (Entity p : entity.getPassengersDeep()) {
                JsonObject pObj = new JsonObject();
                pObj.addProperty("id", p.getId());
                pObj.addProperty("name", p.getName().getString());
                pObj.addProperty("type", p.getType().getName().getString());
                pass.add(pObj);
            }
            result.add("passengers", pass);
        }

        JsonArray items = new JsonArray();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity instanceof LivingEntity lv ? lv.getEquippedStack(slot) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                items.add(itemStackToJson(stack, slot));
            }
        }
        if (items.size() > 0) result.add("items", items);

        if (entity instanceof LivingEntity living) {
            result.addProperty("health", round(living.getHealth(), 1));
            result.addProperty("maxHealth", round(living.getMaxHealth(), 1));
            result.addProperty("absorption", round(living.getAbsorptionAmount(), 1));
            result.addProperty("armor", living.getArmor());
            result.addProperty("armorToughness", living.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
            result.addProperty("scale", living.getScale());
            result.addProperty("stepHeight", round(living.getStepHeight(), 2));
            result.addProperty("deathTime", living.deathTime);
            result.addProperty("hurtTime", living.hurtTime);
            result.addProperty("maxHurtTime", living.maxHurtTime);
            result.addProperty("stuckArrowCount", living.getStuckArrowCount());
            result.addProperty("freezeTicks", living.getFrozenTicks());
            result.addProperty("isBaby", living.isBaby());

            var attrMap = living.getAttributes();
            if (attrMap != null) {
                double atkDmg = attrMap.getValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                double atkSpeed = attrMap.getValue(EntityAttributes.GENERIC_ATTACK_SPEED);
                double moveSpeed = attrMap.getValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                double knockbackResist = attrMap.getValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                double luck = attrMap.getValue(EntityAttributes.GENERIC_LUCK);
                result.addProperty("attackDamage", round(atkDmg, 2));
                result.addProperty("attackSpeed", round(atkSpeed, 2));
                result.addProperty("movementSpeed", round(moveSpeed, 3));
                result.addProperty("knockbackResistance", round(knockbackResist, 2));
                result.addProperty("luck", round(luck, 1));
            }

            if (living instanceof MobEntity mob) {
                result.addProperty("canPickUpLoot", mob.canPickUpLoot());
                result.addProperty("aiDisabled", mob.isAiDisabled());
            }

            JsonArray effects = new JsonArray();
            for (StatusEffectInstance effect : living.getStatusEffects()) {
                JsonObject ef = new JsonObject();
                ef.addProperty("id", effect.getEffectType().getIdAsString());
                ef.addProperty("translationKey", effect.getTranslationKey());
                ef.addProperty("amplifier", effect.getAmplifier());
                ef.addProperty("amplifierDisplay", effect.getAmplifier() + 1);
                ef.addProperty("duration", effect.getDuration());
                ef.addProperty("durationSeconds", effect.getDuration() / 20);
                ef.addProperty("ambient", effect.isAmbient());
                ef.addProperty("showParticles", effect.shouldShowParticles());
                ef.addProperty("showIcon", effect.shouldShowIcon());
                effects.add(ef);
            }
            if (effects.size() > 0) result.add("statusEffects", effects);

            JsonArray attributes = new JsonArray();
            for (EntityAttributeInstance inst : living.getAttributes().getTracked()) {
                JsonObject aObj = new JsonObject();
                RegistryEntry<EntityAttribute> attr = inst.getAttribute();
                aObj.addProperty("id", attr.getIdAsString());
                aObj.addProperty("base", inst.getBaseValue());
                aObj.addProperty("current", inst.getValue());
                if (inst.getModifiers().size() > 0) {
                    JsonArray mods = new JsonArray();
                    for (EntityAttributeModifier mod : inst.getModifiers()) {
                        JsonObject mObj = new JsonObject();
                        mObj.addProperty("id", mod.id().toString());
                        mObj.addProperty("val", mod.value());
                        mObj.addProperty("op", mod.operation().toString());
                        mods.add(mObj);
                    }
                    aObj.add("modifiers", mods);
                }
                attributes.add(aObj);
            }
            if (attributes.size() > 0) result.add("attributes", attributes);

            if (living.getActiveItem() != null && !living.getActiveItem().isEmpty()) {
                JsonObject hand = new JsonObject();
                hand.addProperty("item", living.getActiveItem().getItem().toString());
                hand.addProperty("displayName", living.getActiveItem().getName().getString());
                hand.addProperty("activeTicks", living.getItemUseTime());
                result.add("activeItem", hand);
            }
        }

        if (entity instanceof PlayerEntity pe) {
            result.addProperty("gameMode", pe.isSpectator() ? "spectator" : pe.isCreative() ? "creative" : "survival");
            result.addProperty("score", pe.getScore());
            result.addProperty("experienceLevel", pe.experienceLevel);
            result.addProperty("totalExperience", pe.totalExperience);
            result.addProperty("experienceProgress", round(pe.experienceProgress, 3));
            result.addProperty("foodLevel", pe.getHungerManager().getFoodLevel());
            result.addProperty("saturation", round(pe.getHungerManager().getSaturationLevel(), 1));
            result.addProperty("exhaustion", round(pe.getHungerManager().getExhaustion(), 2));
            result.addProperty("mainInventorySize", pe.getInventory().main.size());
            result.addProperty("armorInventorySize", pe.getInventory().armor.size());
            result.addProperty("offHandSlot", !pe.getOffHandStack().isEmpty() ? pe.getOffHandStack().getName().getString() : "empty");
            result.addProperty("selectedSlot", pe.getInventory().selectedSlot);
            result.addProperty("sleepTimer", pe.getSleepTimer());
            result.addProperty("swinging", pe.handSwinging);
            result.addProperty("swingProgress", round(pe.handSwingProgress, 2));
        }

        try {
            NbtCompound nbt = new NbtCompound();
            entity.writeNbt(nbt);
            String nbtStr = nbt.toString();
            if (nbtStr.length() > 10000) nbtStr = nbtStr.substring(0, 10000) + "... (truncated)";
            result.addProperty("nbt", nbtStr);
        } catch (Exception e) {
            result.addProperty("nbtError", e.getMessage());
        }

        return result.toString();
    }

    private static JsonObject itemStackToJson(ItemStack stack, EquipmentSlot slot) {
        JsonObject obj = new JsonObject();
        obj.addProperty("slot", slot != null ? slot.getName() : "inventory");
        obj.addProperty("item", stack.getItem().toString());
        obj.addProperty("count", stack.getCount());
        obj.addProperty("maxCount", stack.getMaxCount());
        obj.addProperty("displayName", stack.getName().getString());
        obj.addProperty("durability", stack.getMaxDamage() - stack.getDamage());
        obj.addProperty("maxDurability", stack.getMaxDamage());
        obj.addProperty("damage", stack.getDamage());
        obj.addProperty("hasCustomName", stack.get(DataComponentTypes.CUSTOM_NAME) != null);

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

        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            JsonArray loreArr = new JsonArray();
            for (var line : lore.lines()) {
                loreArr.add(line.getString());
            }
            obj.add("lore", loreArr);
        }

        return obj;
    }

    public static String handleBlock(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        BlockPos pos;
        if (json.has("x") && json.has("y") && json.has("z")) {
            pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        } else if (json.has("look")) {
            if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
                throw new Exception("Not looking at a block");
            }
            BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
            pos = hit.getBlockPos();
        } else {
            throw new Exception("Missing coordinates (x,y,z) or 'look' flag");
        }

        JsonObject result = new JsonObject();

        BlockState state = world.getBlockState(pos);
        result.addProperty("name", state.getBlock().getName().getString());
        result.addProperty("id", state.getBlock().toString());
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        result.addProperty("hardness", state.getHardness(world, pos));
        result.addProperty("blastResistance", state.getBlock().getBlastResistance());
        result.addProperty("lightEmission", state.getLuminance());
        result.addProperty("opaque", state.isOpaque());
        result.addProperty("air", state.isAir());
        result.addProperty("liquid", !state.getFluidState().isEmpty());
        result.addProperty("replaceable", state.isReplaceable());

        JsonObject props = new JsonObject();
        for (var prop : state.getProperties()) {
            props.addProperty(prop.getName(), state.get(prop).toString());
        }
        result.add("properties", props);

        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            result.addProperty("fluid_level", fluid.getLevel());
            result.addProperty("fluid_falling", fluid.isStill() ? false : true);
        }

        result.addProperty("redstone_power", world.getReceivedRedstonePower(pos));

        BlockEntity be = world.getBlockEntity(pos);
        if (be != null) {
            result.addProperty("blockEntityType", be.getClass().getSimpleName());
            try {
                NbtCompound beNbt = be.createNbt(world.getRegistryManager());
                String nbtStr = beNbt.toString();
                if (nbtStr.length() > 5000) nbtStr = nbtStr.substring(0, 5000) + "... (truncated)";
                result.addProperty("nbt", nbtStr);
            } catch (Exception e) {
                result.addProperty("nbtError", e.getMessage());
            }
        }

        return result.toString();
    }

    public static String handleFindBlocks(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        List<String> queries = new ArrayList<>();
        if (json.has("block")) {
            queries.add(json.get("block").getAsString().toLowerCase());
        }
        if (json.has("blocks")) {
            JsonArray arr = json.getAsJsonArray("blocks");
            for (int i = 0; i < arr.size(); i++) {
                queries.add(arr.get(i).getAsString().toLowerCase());
            }
        }
        if (queries.isEmpty()) throw new Exception("Missing 'block' or 'blocks' parameter");

        int x1, y1, z1, x2, y2, z2;
        if (json.has("radius")) {
            int r = json.get("radius").getAsInt();
            BlockPos p = client.player.getBlockPos();
            x1 = p.getX() - r; x2 = p.getX() + r;
            y1 = Math.max(world.getBottomY(), p.getY() - r);
            y2 = Math.min(world.getTopY(), p.getY() + r);
            z1 = p.getZ() - r; z2 = p.getZ() + r;
        } else {
            x1 = json.get("x1").getAsInt(); x2 = json.get("x2").getAsInt();
            y1 = json.get("y1").getAsInt(); y2 = json.get("y2").getAsInt();
            z1 = json.get("z1").getAsInt(); z2 = json.get("z2").getAsInt();
        }

        JsonArray blocks = new JsonArray();
        int checked = 0;

        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    checked++;
                    if (state.isAir()) continue;

                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    String name = state.getBlock().getName().getString();

                    for (String q : queries) {
                        if (id.contains(q) || name.toLowerCase().contains(q)) {
                            JsonObject p = new JsonObject();
                            p.addProperty("x", x);
                            p.addProperty("y", y);
                            p.addProperty("z", z);
                            p.addProperty("id", id);
                            p.addProperty("name", name);
                            blocks.add(p);
                            break;
                        }
                    }
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", blocks.size());
        result.addProperty("checked", checked);
        result.add("blocks", blocks);
        return result.toString();
    }

    public static String handleBreakBlock(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z")) {
            throw new Exception("Missing x, y, or z parameter");
        }
        BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        var interactionManager = client.interactionManager;
        if (interactionManager == null) throw new Exception("Interaction manager not available");

        boolean started = interactionManager.attackBlock(pos, Direction.UP);

        JsonObject result = new JsonObject();
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        result.addProperty("success", started);
        return result.toString();
    }

    public static String handlePlaceBlock(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z") || !json.has("block")) {
            throw new Exception("Missing x, y, z, or block parameter");
        }
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        String block = json.get("block").getAsString();
        if (block.startsWith("minecraft:")) block = block.substring(10);

        player.networkHandler.sendChatCommand("setblock " + x + " " + y + " " + z + " " + block);

        JsonObject result = new JsonObject();
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        result.addProperty("block", block);
        result.addProperty("success", true);
        return result.toString();
    }

    public static String handleHighlight(JsonObject json, MinecraftClient client) throws Exception {
        String action = json.has("action") ? json.get("action").getAsString() : "add";
        JsonObject result = new JsonObject();

        switch (action) {
            case "add" -> {
                if (!json.has("x") || !json.has("y") || !json.has("z")) {
                    if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
                        BlockPos pos = hit.getBlockPos();
                        float r = json.has("r") ? json.get("r").getAsFloat() : 1f;
                        float g = json.has("g") ? json.get("g").getAsFloat() : 0f;
                        float b = json.has("b") ? json.get("b").getAsFloat() : 0f;
                        float a = json.has("a") ? json.get("a").getAsFloat() : 0.8f;
                        long dur = json.has("duration") ? json.get("duration").getAsLong() : 60000;
                        HighlightManager.addBlock(pos, r, g, b, a, dur);
                        result.addProperty("pos", pos.toShortString());
                        result.addProperty("count", HighlightManager.count());
                    } else {
                        result.addProperty("error", "Not looking at a block, provide x,y,z");
                    }
                } else {
                    BlockPos pos = new BlockPos(
                            json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
                    float r = json.has("r") ? json.get("r").getAsFloat() : 1f;
                    float g = json.has("g") ? json.get("g").getAsFloat() : 0f;
                    float b = json.has("b") ? json.get("b").getAsFloat() : 0f;
                    float a = json.has("a") ? json.get("a").getAsFloat() : 0.8f;
                    long dur = json.has("duration") ? json.get("duration").getAsLong() : 60000;
                    HighlightManager.addBlock(pos, r, g, b, a, dur);
                    result.addProperty("pos", pos.toShortString());
                    result.addProperty("count", HighlightManager.count());
                }
            }
            case "clear" -> {
                HighlightManager.clear();
                result.addProperty("cleared", true);
            }
            case "list" -> {
                var active = HighlightManager.getActive();
                JsonArray arr = new JsonArray();
                for (var h : active) {
                    JsonObject o = new JsonObject();
                    o.addProperty("x", h.pos().getX());
                    o.addProperty("y", h.pos().getY());
                    o.addProperty("z", h.pos().getZ());
                    arr.add(o);
                }
                result.add("highlights", arr);
                result.addProperty("count", active.size());
            }
            default -> result.addProperty("error", "Unknown action: " + action + " (add/clear/list)");
        }
        return result.toString();
    }

    public static String handleFindItem(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("name")) throw new Exception("Missing 'name' parameter");
        String query = json.get("name").getAsString().toLowerCase(Locale.ROOT);
        boolean searchContainer = json.has("container") && json.get("container").getAsBoolean();
        JsonArray results = new JsonArray();
        var inv = player.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            var stack = inv.main.get(i);
            if (!stack.isEmpty() && matchesItem(stack, query)) {
                var obj = itemStackToShortJson(stack);
                obj.addProperty("slot", i);
                obj.addProperty("location", "main");
                results.add(obj);
            }
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            var stack = inv.armor.get(i);
            if (!stack.isEmpty() && matchesItem(stack, query)) {
                var obj = itemStackToShortJson(stack);
                obj.addProperty("slot", 100 + i);
                obj.addProperty("location", "armor");
                results.add(obj);
            }
        }
        var offhand = inv.offHand.get(0);
        if (!offhand.isEmpty() && matchesItem(offhand, query)) {
            var obj = itemStackToShortJson(offhand);
            obj.addProperty("slot", 40);
            obj.addProperty("location", "offhand");
            results.add(obj);
        }
        if (searchContainer && player.currentScreenHandler != null && player.currentScreenHandler instanceof net.minecraft.screen.ScreenHandler) {
            var handler = player.currentScreenHandler;
            for (int i = 0; i < handler.slots.size(); i++) {
                var stack = handler.getSlot(i).getStack();
                if (!stack.isEmpty() && matchesItem(stack, query)) {
                    var obj = itemStackToShortJson(stack);
                    obj.addProperty("slot", i);
                    obj.addProperty("location", "container");
                    results.add(obj);
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("found", results.size());
        result.add("items", results);
        return result.toString();
    }

    private static boolean matchesItem(ItemStack stack, String query) {
        String name = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String display = stack.getName().getString().toLowerCase(Locale.ROOT);
        return name.contains(query) || display.contains(query);
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

    public static String handleBlockCounter(JsonObject json, MinecraftClient client) {
        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        int cx, cz;
        if (json.has("cx") && json.has("cz")) {
            cx = json.get("cx").getAsInt();
            cz = json.get("cz").getAsInt();
        } else {
            var player = client.player;
            if (player == null) return errorJson("Not in game");
            cx = player.getBlockPos().getX() >> 4;
            cz = player.getBlockPos().getZ() >> 4;
        }

        result.addProperty("chunkX", cx);
        result.addProperty("chunkZ", cz);

        var chunk = world.getChunk(cx, cz);
        if (chunk == null) return errorJson("Chunk not loaded");

        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        BlockPos.Mutable bp = new BlockPos.Mutable();

        int minY = world.getBottomY();
        int maxY = world.getTopY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    bp.set((cx << 4) + x, y, (cz << 4) + z);
                    BlockState state = world.getBlockState(bp);
                    String blockId = state.getBlock().toString();
                    counts.merge(blockId, 1, Integer::sum);
                    total++;
                }
            }
        }

        int finalTotal = total;
        JsonArray blocks = new JsonArray();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(50)
                .forEach(e -> {
                    JsonObject b = new JsonObject();
                    b.addProperty("block", e.getKey());
                    b.addProperty("count", e.getValue());
                    b.addProperty("percent", round(e.getValue() * 100.0 / finalTotal, 2));
                    blocks.add(b);
                });

        result.addProperty("totalBlocks", finalTotal);
        result.addProperty("uniqueBlocks", counts.size());
        result.add("blockCounts", blocks);
        return result.toString();
    }

    public static String handleScanCrops(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        int radius = json.has("radius") ? json.get("radius").getAsInt() : 16;
        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        BlockPos center = player.getBlockPos();
        JsonArray crops = new JsonArray();
        int totalScanned = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -4; y <= 4; y++) {
                    BlockPos bp = center.add(x, y, z);
                    BlockState state = world.getBlockState(bp);
                    var block = state.getBlock();

                    if (block instanceof CropBlock crop) {
                        int age = crop.getAge(state);
                        int maxAge = crop.getMaxAge();
                        if (age >= maxAge) {
                            JsonObject c = new JsonObject();
                            c.addProperty("block", block.toString());
                            c.addProperty("x", bp.getX());
                            c.addProperty("y", bp.getY());
                            c.addProperty("z", bp.getZ());
                            c.addProperty("age", age);
                            c.addProperty("maxAge", maxAge);
                            c.addProperty("distance", round(Math.sqrt(bp.getSquaredDistance(center)), 1));
                            crops.add(c);
                        }
                        totalScanned++;
                    }

                    if (block.toString().contains("nether_wart")) {
                        int age = state.get(net.minecraft.block.NetherWartBlock.AGE);
                        if (age >= 3) {
                            JsonObject c = new JsonObject();
                            c.addProperty("block", block.toString());
                            c.addProperty("x", bp.getX());
                            c.addProperty("y", bp.getY());
                            c.addProperty("z", bp.getZ());
                            c.addProperty("age", age);
                            c.addProperty("maxAge", 3);
                            c.addProperty("distance", round(Math.sqrt(bp.getSquaredDistance(center)), 1));
                            crops.add(c);
                        }
                        totalScanned++;
                    }
                }
            }
        }

        result.addProperty("count", crops.size());
        result.addProperty("totalScanned", totalScanned);
        result.add("matureCrops", crops);
        return result.toString();
    }

    public static String handleScanTerrain(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        int radius = json.has("radius") ? json.get("radius").getAsInt() : 16;
        int yRange = json.has("yRange") ? json.get("yRange").getAsInt() : 8;

        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        Vec3d pos = player.getPos();
        int cx = (int) Math.floor(pos.x);
        int cy = (int) Math.floor(pos.y);
        int cz = (int) Math.floor(pos.z);

        result.addProperty("centerX", cx);
        result.addProperty("centerY", cy);
        result.addProperty("centerZ", cz);
        result.addProperty("radius", radius);

        Map<String, Integer> blockCounts = new HashMap<>();
        int totalScanned = 0;
        BlockPos.Mutable bp = new BlockPos.Mutable();

        int yMin = Math.max(world.getBottomY(), cy - yRange / 2);
        int yMax = Math.min(world.getTopY(), cy + yRange / 2);

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    bp.set(x, y, z);
                    var blockState = world.getBlockState(bp);
                    String blockId = blockState.getBlock().toString();
                    blockCounts.merge(blockId, 1, Integer::sum);
                    totalScanned++;
                }
            }
        }

        JsonArray topBlocks = new JsonArray();
        blockCounts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .forEach(entry -> {
                    JsonObject b = new JsonObject();
                    b.addProperty("block", entry.getKey());
                    b.addProperty("count", entry.getValue());
                    topBlocks.add(b);
                });

        result.addProperty("totalScanned", totalScanned);
        result.addProperty("uniqueBlocks", blockCounts.size());
        result.add("topBlocks", topBlocks);

        return result.toString();
    }

    public static String handleScanContainers(JsonObject json, MinecraftClient client) {
        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        int radius = json.has("radius") ? json.get("radius").getAsInt() : 32;
        var player = client.player;
        if (player == null) return errorJson("Not in game");
        BlockPos center = player.getBlockPos();

        JsonArray containers = new JsonArray();

        for (int cx = -radius / 16 - 1; cx <= radius / 16 + 1; cx++) {
            for (int cz = -radius / 16 - 1; cz <= radius / 16 + 1; cz++) {
                var chunk = world.getChunk(center.getX() / 16 + cx, center.getZ() / 16 + cz);
                if (chunk == null) continue;

                var blockEntities = chunk.getBlockEntities();
                if (blockEntities == null) continue;

                for (var entry : blockEntities.entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity be = entry.getValue();

                    double dist = pos.getSquaredDistance(center);
                    if (dist > radius * radius) continue;

                    String type = null;
                    if (be instanceof ChestBlockEntity) type = "chest";
                    else if (be instanceof BarrelBlockEntity) type = "barrel";
                    else if (be instanceof HopperBlockEntity) type = "hopper";
                    else if (be instanceof ShulkerBoxBlockEntity) type = "shulker_box";

                    if (type != null) {
                        JsonObject c = new JsonObject();
                        c.addProperty("type", type);
                        c.addProperty("x", pos.getX());
                        c.addProperty("y", pos.getY());
                        c.addProperty("z", pos.getZ());
                        c.addProperty("distance", round(Math.sqrt(dist), 1));
                        containers.add(c);
                    }
                }
            }
        }

        result.addProperty("count", containers.size());
        result.add("containers", containers);
        return result.toString();
    }

    public static String handleShulkerPeek(JsonObject json, ClientPlayerEntity player) {
        JsonObject result = new JsonObject();
        var handler = player.currentScreenHandler;
        if (handler == null) return errorJson("No container open");

        int slotIdx = json.has("slot") ? json.get("slot").getAsInt() : -1;
        int shulkerCount = 0;

        for (int i = 0; i < handler.slots.size(); i++) {
            if (slotIdx >= 0 && i != slotIdx) continue;

            var stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            if (!stack.getItem().toString().toLowerCase(Locale.ROOT).contains("shulker")) continue;

            var containerComp = stack.get(DataComponentTypes.CONTAINER);
            if (containerComp == null) continue;

            JsonObject shulker = new JsonObject();
            shulker.addProperty("slot", i);
            shulker.addProperty("name", stack.getName().getString());
            shulker.addProperty("itemCount", stack.getCount());

            var itemStacks = containerComp.iterateNonEmpty();
            JsonArray items = new JsonArray();
            for (ItemStack inner : itemStacks) {
                JsonObject item = new JsonObject();
                item.addProperty("name", inner.getItem().toString());
                item.addProperty("count", inner.getCount());
                item.addProperty("displayName", inner.getName().getString());
                items.add(item);
            }
            shulker.add("contents", items);
            shulker.addProperty("totalItems", items.size());

            result.add("shulker_" + i, shulker);
            shulkerCount++;
        }

        result.addProperty("count", shulkerCount);
        return result.toString();
    }

    public static String handleBiome(JsonObject json, MinecraftClient client) throws Exception {
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        BlockPos pos;
        if (json.has("x") && json.has("y") && json.has("z")) {
            pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        } else {
            ClientPlayerEntity player = client.player;
            if (player == null) throw new Exception("Player not available");
            pos = player.getBlockPos();
        }

        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        Biome biome = biomeEntry.value();

        JsonObject result = new JsonObject();
        result.addProperty("id", biomeEntry.getIdAsString());
        result.addProperty("temperature", biome.getTemperature());
        result.addProperty("precipitation", biome.getPrecipitation(pos).toString());
        result.addProperty("hasPrecipitation", biome.hasPrecipitation());
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        return result.toString();
    }

    public static String handleChunk(JsonObject json, MinecraftClient client) throws Exception {
        if (client.world == null) throw new Exception("No world loaded");
        if (!json.has("x") || !json.has("z")) throw new Exception("Missing chunk x, z");
        int cx = json.get("x").getAsInt();
        int cz = json.get("z").getAsInt();
        var chunk = client.world.getChunk(cx, cz);
        if (chunk == null) throw new Exception("Chunk not loaded at " + cx + ", " + cz);
        JsonObject r = new JsonObject();
        r.addProperty("x", cx);
        r.addProperty("z", cz);
        r.addProperty("loaded", true);
        r.addProperty("section_count", chunk.getSectionArray().length);
        JsonArray heightSamples = new JsonArray();
        for (int lx = 0; lx < 16; lx += 4) {
            for (int lz = 0; lz < 16; lz += 4) {
                JsonObject pt = new JsonObject();
                int wx = (cx << 4) + lx;
                int wz = (cz << 4) + lz;
                int topY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
                pt.addProperty("x", wx);
                pt.addProperty("z", wz);
                pt.addProperty("top_y", topY);
                var biome = client.world.getBiome(new BlockPos(wx, topY, wz));
                pt.addProperty("biome", biome.getIdAsString());
                heightSamples.add(pt);
            }
        }
        r.add("samples", heightSamples);
        return r.toString();
    }

    public static String handleLightLevel(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        var world = client.world;
        if (world == null) return "{}";
        int x, y, z;
        if (json.has("x") && json.has("y") && json.has("z")) {
            x = json.get("x").getAsInt(); y = json.get("y").getAsInt(); z = json.get("z").getAsInt();
        } else {
            x = (int) player.getX(); y = (int) player.getY(); z = (int) player.getZ();
        }
        BlockPos pos = new BlockPos(x, y, z);
        JsonObject r = new JsonObject();
        r.addProperty("x", x); r.addProperty("y", y); r.addProperty("z", z);
        r.addProperty("light_level", world.getLightLevel(pos));
        r.addProperty("ambient_darkness", world.getAmbientDarkness());
        BlockState state = world.getBlockState(pos);
        r.addProperty("block_luminance", state.getLuminance());
        r.addProperty("block_id", Registries.BLOCK.getId(state.getBlock()).toString());
        return r.toString();
    }

    // --- mc_entity_highlight ---
    public static String handleEntityHighlight(JsonObject json, MinecraftClient client) {
        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        String type = json.has("type") ? json.get("type").getAsString().toLowerCase(Locale.ROOT) : "";
        int radius = json.has("radius") ? json.get("radius").getAsInt() : 32;
        int maxResults = json.has("max") ? json.get("max").getAsInt() : 20;

        var player = client.player;
        Vec3d playerPos = player != null ? player.getPos() : Vec3d.ZERO;

        JsonArray entities = new JsonArray();
        for (var entity : world.getEntities()) {
            if (entities.size() >= maxResults) break;
            if (!type.isEmpty()) {
                String name = entity.getName().getString().toLowerCase(Locale.ROOT);
                String entityType = entity.getType().toString().toLowerCase(Locale.ROOT);
                if (!name.contains(type) && !entityType.contains(type)) continue;
            }
            Vec3d pos = entity.getPos();
            double dist = player != null ? pos.distanceTo(playerPos) : 0;
            if (dist > radius) continue;

            JsonObject e = new JsonObject();
            e.addProperty("id", entity.getId());
            e.addProperty("name", entity.getName().getString());
            e.addProperty("type", entity.getType().toString());
            e.addProperty("x", round(pos.x, 1));
            e.addProperty("y", round(pos.y, 1));
            e.addProperty("z", round(pos.z, 1));
            e.addProperty("distance", round(dist, 1));
            entities.add(e);
        }

        result.addProperty("count", entities.size());
        result.add("entities", entities);
        result.addProperty("highlightInfo", "Use mc_highlight_block for each entity position to mark in-game");
        return result.toString();
    }

    // --- mc_damage_display ---
    public static String handleDamageDisplay(JsonObject json, MinecraftClient client) {
        JsonObject result = new JsonObject();
        var world = client.world;
        if (world == null) return errorJson("World not loaded");

        int entityId = json.has("id") ? json.get("id").getAsInt() : -1;
        if (entityId < 0) return errorJson("Missing 'id' parameter");

        var entity = world.getEntityById(entityId);
        if (entity == null) return errorJson("Entity not found");

        result.addProperty("id", entity.getId());
        result.addProperty("name", entity.getName().getString());
        result.addProperty("type", entity.getType().toString());

        if (entity instanceof LivingEntity le) {
            result.addProperty("health", round(le.getHealth(), 1));
            result.addProperty("maxHealth", round(le.getMaxHealth(), 1));
            double maxHp = le.getMaxHealth();
            if (maxHp > 0) {
                result.addProperty("healthPercent", round(le.getHealth() / maxHp * 100, 1));
            } else {
                result.addProperty("healthPercent", 0);
            }
            result.addProperty("armor", le.getArmor());
            result.addProperty("armorToughness", le.getArmorVisibility());

            var attributes = le.getAttributes();
            if (attributes != null) {
                var atkDamage = attributes.getValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                var atkSpeed = attributes.getValue(EntityAttributes.GENERIC_ATTACK_SPEED);
                var movementSpeed = attributes.getValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                result.addProperty("attackDamage", round(atkDamage, 2));
                result.addProperty("attackSpeed", round(atkSpeed, 2));
                result.addProperty("movementSpeed", round(movementSpeed, 3));
            }

            JsonArray effects = new JsonArray();
            for (var effect : le.getStatusEffects()) {
                JsonObject eff = new JsonObject();
                if (effect.getEffectType() != null) {
                    eff.addProperty("effect", effect.getEffectType().getIdAsString());
                    eff.addProperty("amplifier", effect.getAmplifier());
                    eff.addProperty("duration", effect.getDuration());
                    effects.add(eff);
                }
            }
            if (effects.size() > 0) result.add("effects", effects);
        }

        return result.toString();
    }

    public static String handleSummon(JsonObject json, ClientPlayerEntity player) throws Exception {
        if (!json.has("entity") || !json.has("x") || !json.has("y") || !json.has("z")) {
            throw new Exception("Missing entity, x, y, or z parameter");
        }
        String entity = json.get("entity").getAsString();
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        if (entity.startsWith("minecraft:")) entity = entity.substring(10);

        String nbtArg = json.has("nbt") ? " " + json.get("nbt").getAsString() : "";
        String cmd = "summon " + entity + " " + x + " " + y + " " + z + nbtArg;
        player.networkHandler.sendChatCommand(cmd);

        JsonObject result = new JsonObject();
        result.addProperty("entity", entity);
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        result.addProperty("success", true);
        return result.toString();
    }

    public static String handleAttackEntity(JsonObject json, MinecraftClient client) throws Exception {
        if (!json.has("id")) throw new Exception("Missing 'id' parameter");
        int id = json.get("id").getAsInt();
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        var player = client.player;
        if (player == null) throw new Exception("No player");

        var entity = world.getEntityById(id);
        if (entity == null) throw new Exception("Entity not found: " + id);

        var interactionManager = client.interactionManager;
        if (interactionManager == null) throw new Exception("Interaction manager not available");

        interactionManager.attackEntity(player, entity);

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("entity_id", id);
        result.addProperty("entity", entity.getType().getName().getString());
        return result.toString();
    }

    public static String handleInteractEntity(JsonObject json, MinecraftClient client) throws Exception {
        if (!json.has("id")) throw new Exception("Missing 'id' parameter");
        int id = json.get("id").getAsInt();
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        var player = client.player;
        if (player == null) throw new Exception("No player");

        var entity = world.getEntityById(id);
        if (entity == null) throw new Exception("Entity not found: " + id);

        var interactionManager = client.interactionManager;
        if (interactionManager == null) throw new Exception("Interaction manager not available");

        var hand = json.has("hand") && json.get("hand").getAsString().equals("offhand") ? Hand.OFF_HAND : Hand.MAIN_HAND;

        var resultType = interactionManager.interactEntity(player, entity, hand);

        JsonObject result = new JsonObject();
        result.addProperty("success", resultType.isAccepted());
        result.addProperty("entity_id", id);
        result.addProperty("entity", entity.getClass().getSimpleName());
        return result.toString();
    }

    public static String handleRideEntity(JsonObject json, MinecraftClient client) throws Exception {
        if (!json.has("id")) throw new Exception("Missing 'id' parameter");
        int id = json.get("id").getAsInt();
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        var player = client.player;
        if (player == null) throw new Exception("No player");

        var entity = world.getEntityById(id);
        if (entity == null) throw new Exception("Entity not found: " + id);

        // Dismount if already riding
        if (player.hasVehicle()) {
            player.stopRiding();
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("action", "dismount");
            return result.toString();
        }

        // Try to start riding - use interact (right-click) on the entity
        var interactionManager = client.interactionManager;
        if (interactionManager != null) {
            interactionManager.interactEntity(player, entity, Hand.MAIN_HAND);
        }

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("action", "mount");
        result.addProperty("entity_id", id);
        result.addProperty("entity", entity.getClass().getSimpleName());
        return result.toString();
    }

    // --- read_comparator ---
    public static String handleReadComparator(JsonObject json, MinecraftClient client) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z")) {
            throw new Exception("Missing x, y, or z");
        }
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");

        BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        BlockState state = world.getBlockState(pos);

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        JsonObject result = new JsonObject();
        result.addProperty("block", blockId);
        result.addProperty("has_comparator", blockId.contains("comparator"));

        if (state.contains(Properties.POWERED)) {
            result.addProperty("powered", state.get(Properties.POWERED));
        }

        var sm = state.getBlock().getStateManager();
        var outputProp = sm.getProperty("output_signal");
        if (outputProp != null && state.contains(outputProp)) {
            int signal = (Integer) state.get(outputProp);
            result.addProperty("signal_strength", signal);
        }

        return result.toString();
    }

    // --- toggle_block ---
    public static String handleToggleBlock(JsonObject json, MinecraftClient client, ClientPlayerEntity player) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z")) {
            throw new Exception("Missing x, y, or z");
        }
        var world = client.world;
        if (world == null) throw new Exception("No world loaded");
        var interactionManager = client.interactionManager;
        if (interactionManager == null) throw new Exception("Interaction manager not available");

        BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        BlockState state = world.getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        var hitResult = new BlockHitResult(
            new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
            Direction.UP, pos, false
        );

        boolean interacted = interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult).isAccepted();

        JsonObject result = new JsonObject();
        result.addProperty("success", interacted);
        result.addProperty("block", blockId);
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        return result.toString();
    }

    // --- auto_tnt ---
    public static String handleAutoTnt(JsonObject json, MinecraftClient client, ClientPlayerEntity player) {
        if (!json.has("x") || !json.has("y") || !json.has("z")) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Missing x, y, or z");
            return err.toString();
        }
        var world = client.world;
        if (world == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "No world loaded");
            return err.toString();
        }

        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        BlockPos pos = new BlockPos(x, y, z);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                CompletableFuture<?> cf1 = new CompletableFuture<>();
                client.execute(() -> {
                    try { player.networkHandler.sendChatCommand("setblock " + x + " " + y + " " + z + " tnt"); }
                    catch (Exception ignored) {}
                    cf1.complete(null);
                });
                cf1.get();
                Thread.sleep(100);

                CompletableFuture<?> cf2 = new CompletableFuture<>();
                client.execute(() -> {
                    try { player.networkHandler.sendChatCommand("give @s minecraft:flint_and_steel"); }
                    catch (Exception ignored) {}
                    cf2.complete(null);
                });
                cf2.get();
                Thread.sleep(50);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try { future.get(5000, TimeUnit.MILLISECONDS); } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "TNT placement failed: " + e.getMessage());
            return err.toString();
        }

        var interactionManager = client.interactionManager;
        if (interactionManager == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Interaction manager not available");
            return err.toString();
        }

        var hitResult = new BlockHitResult(
            new Vec3d(x + 0.5, y + 0.5, z + 0.5),
            Direction.UP, pos, false
        );

        boolean ignited = interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult).shouldSwingHand();

        JsonObject result = new JsonObject();
        result.addProperty("success", ignited);
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        result.addProperty("primed", ignited);
        return result.toString();
    }
}
