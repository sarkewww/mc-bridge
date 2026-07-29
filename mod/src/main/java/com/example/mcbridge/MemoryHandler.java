package com.example.mcbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MemoryHandler {

    public static String handleMemoryAdd(JsonObject json) throws Exception {
        if (!json.has("content")) throw new Exception("Missing 'content' parameter");
        String content = json.get("content").getAsString();
        String category = json.has("category") ? json.get("category").getAsString() : "note";
        int importance = json.has("importance") ? json.get("importance").getAsInt() : 3;
        String tags = json.has("tags") ? json.get("tags").getAsString() : "";
        return MemoryService.addMemory(content, category, importance, tags);
    }

    public static String handleMemoryRecall(JsonObject json) {
        String query = json.has("query") ? json.get("query").getAsString() : "";
        String category = json.has("category") ? json.get("category").getAsString() : "";
        int limit = json.has("limit") ? json.get("limit").getAsInt() : 20;
        return MemoryService.recallMemories(query.isEmpty() ? null : query,
                category.isEmpty() ? null : category, limit);
    }

    public static String handleMemoryList(JsonObject json) {
        String category = json.has("category") ? json.get("category").getAsString() : "";
        int limit = json.has("limit") ? json.get("limit").getAsInt() : 50;
        return MemoryService.listMemories(category.isEmpty() ? null : category, limit);
    }

    public static String handleMemoryDelete(JsonObject json) throws Exception {
        if (!json.has("id")) throw new Exception("Missing 'id' parameter");
        String id = json.get("id").getAsString();
        return MemoryService.deleteMemory(id);
    }

    public static String handleMemorySummary() {
        return MemoryService.summarize();
    }

    public static String handleMemoryNear(JsonObject json) throws Exception {
        if (!json.has("x") || !json.has("y") || !json.has("z"))
            throw new Exception("Missing coordinates (x, y, z)");
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        double radius = json.has("radius") ? json.get("radius").getAsDouble() : 32;
        int limit = json.has("limit") ? json.get("limit").getAsInt() : 20;
        return MemoryService.findNear(x, y, z, radius, limit);
    }

    // --- memory_export ---
    public static String handleMemoryExport(JsonObject json) throws Exception {
        String format = json.has("format") ? json.get("format").getAsString() : "json";

        var memories = MemoryService.getAllMemories();
        if (memories == null || memories.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("count", 0);
            return empty.toString();
        }

        if (format.equals("csv")) {
            StringBuilder csv = new StringBuilder("id,category,importance,content,tags,created\n");
            for (var mem : memories) {
                csv.append(mem.id).append(",");
                csv.append(mem.category).append(",");
                csv.append(mem.importance).append(",");
                csv.append("\"").append(mem.content.replace("\"", "\"\"")).append("\",");
                csv.append(mem.tags != null && !mem.tags.isEmpty() ? "\"" + mem.tags + "\"" : "");
                csv.append(",").append(mem.time).append("\n");
            }
            JsonObject result = new JsonObject();
            result.addProperty("count", memories.size());
            result.addProperty("csv", csv.toString());
            return result.toString();
        }

        JsonArray arr = new JsonArray();
        for (var mem : memories) {
            JsonObject m = new JsonObject();
            m.addProperty("id", mem.id);
            m.addProperty("category", mem.category);
            m.addProperty("importance", mem.importance);
            m.addProperty("content", mem.content);
            if (mem.tags != null && !mem.tags.isEmpty()) {
                JsonArray tags = new JsonArray();
                for (String t : mem.tags.split(",")) tags.add(t.trim());
                m.add("tags", tags);
            }
            m.addProperty("created", mem.time);
            m.addProperty("dimension", mem.dimension);
            m.addProperty("x", mem.x);
            m.addProperty("y", mem.y);
            m.addProperty("z", mem.z);
            arr.add(m);
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", memories.size());
        result.add("memories", arr);
        return result.toString();
    }
}
