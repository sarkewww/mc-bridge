package com.example.mcbridge;

import net.minecraft.text.Text;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ChatLog {
    private static final int MAX_MESSAGES = 500;
    private static final Deque<ChatEntry> entries = new ArrayDeque<>();
    private static final Set<String> messageHashes = new LinkedHashSet<>();
    private static final int MAX_HASHES = 2000;

    public static synchronized void add(Text text) {
        add(text.getString());
    }

    public static synchronized void add(String message) {
        ChatEntry entry = new ChatEntry();
        entry.time = Instant.now().toString();
        entry.content = message;
        entries.addLast(entry);
        if (entries.size() > MAX_MESSAGES) {
            entries.removeFirst();
        }
    }

    public static synchronized void addRaw(String message, String playerName, String direction) {
        String hash = playerName != null ? playerName + "|" + message : message;
        if (messageHashes.contains(hash)) return;
        messageHashes.add(hash);
        if (messageHashes.size() > MAX_HASHES) {
            var iter = messageHashes.iterator();
            for (int i = 0; i < 1000 && iter.hasNext(); i++) { iter.next(); iter.remove(); }
        }

        ChatEntry entry = new ChatEntry();
        entry.time = Instant.now().toString();
        entry.content = message;
        entry.playerName = playerName;
        entry.direction = direction;
        entries.addLast(entry);
        if (entries.size() > MAX_MESSAGES) {
            entries.removeFirst();
        }

        // Push to profile manager
        if (playerName != null && !playerName.isEmpty()) {
            String brand = ServerContext.getCurrentBrand();
            PlayerProfileManager.recordMessage(playerName, message, direction, null, brand);
        }
    }

    public static synchronized List<String> getRecent(int count) {
        List<ChatEntry> list = new ArrayList<>(entries);
        int start = Math.max(0, list.size() - count);
        return list.subList(start, list.size()).stream()
                .map(e -> e.content)
                .collect(Collectors.toList());
    }

    public static synchronized List<String> getPlayerHistory(String playerName, int limit) {
        return entries.stream()
                .filter(e -> playerName == null || playerName.isEmpty()
                        || (e.playerName != null && e.playerName.toLowerCase(Locale.ROOT).contains(playerName.toLowerCase())))
                .limit(limit)
                .map(e -> "[" + e.time.substring(11, 19) + "]" + (e.playerName != null ? "<" + e.playerName + "> " : "") + e.content)
                .collect(Collectors.toList());
    }

    public static synchronized List<String> getOwnMessages(int count) {
        return entries.stream()
                .filter(e -> e.direction != null && (e.direction.equals("from_me") || e.direction.equals("copy") || e.direction.equals("intercepted")))
                .limit(count)
                .map(e -> e.content)
                .collect(Collectors.toList());
    }

    public static synchronized int size() {
        return entries.size();
    }

    private static class ChatEntry {
        String time;
        String content;
        String playerName;
        String direction; // from_me, to_me, system
    }
}
