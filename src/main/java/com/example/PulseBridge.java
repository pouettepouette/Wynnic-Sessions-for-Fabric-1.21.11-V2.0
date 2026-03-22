package com.example;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PulseBridge {
    private static final int CHUNK_LIMIT = 900;
    private static final boolean TRACE = false;
    private static final ExecutorService BUS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pulse-bridge-worker");
        t.setDaemon(true);
        return t;
    });

    private String route;

    public PulseBridge(String route) {
        this.route = route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public void publishLedger(String actor, String block) {
        if (route == null || route.trim().isEmpty()) {
            trace("route is empty, skipping ledger");
            return;
        }
        if (block == null || block.isEmpty()) {
            return;
        }

        BUS.execute(() -> {
            try {
                String text = block.replace("\r", "").trim();
                List<String> chunks = splitLedgerChunks(text);
                for (int index = 0; index < chunks.size(); index++) {
                    String title = chunks.size() > 1
                        ? "SPOTS REPORT " + (index + 1) + "/" + chunks.size()
                        : "SPOTS REPORT";
                    transmit("**" + title + "**\n" + toQuoteBlock(chunks.get(index)));
                    if (index < chunks.size() - 1) {
                        Thread.sleep(350L);
                    }
                }
            } catch (Exception error) {
                System.err.println("[MobKillerCalculator] Failed to publish ledger: " + error.getMessage());
                error.printStackTrace();
            }
        });
    }

    public void publishDigest(
        String actor,
        String timer,
        int count,
        int rareDrops,
        String leadName,
        int leadCount,
        double gross,
        double pace,
        int x,
        int y,
        int z,
        String block
    ) {
        if (route == null || route.trim().isEmpty()) {
            trace("route is empty, skipping digest");
            return;
        }

        BUS.execute(() -> {
            try {
                String session = composeSession(actor, timer, count, rareDrops, leadName, leadCount, gross, pace, x, y, z);
                String combined = fuseBlocks(block == null ? "" : block.replace("\r", "").trim(), session);
                if (combined.length() > 1800) {
                    combined = combined.substring(0, 1770) + "\n... (truncated)";
                }
                transmit(combined);
            } catch (Exception error) {
                System.err.println("[MobKillerCalculator] Failed to publish digest: " + error.getMessage());
                error.printStackTrace();
            }
        });
    }

    public void publishNote(String actor, String phrase) {
        if (route == null || route.trim().isEmpty()) {
            trace("route is empty, skipping note");
            return;
        }
        String safePhrase = phrase == null ? "" : phrase.trim();
        if (safePhrase.isEmpty()) {
            return;
        }

        BUS.execute(() -> {
            try {
                transmit(frame("NOTE", "Player: " + actor + "\nMessage: " + safePhrase));
            } catch (Exception error) {
                System.err.println("[MobKillerCalculator] Failed to publish note: " + error.getMessage());
                error.printStackTrace();
            }
        });
    }

    public void testConnection(java.util.function.Consumer<String> callback) {
        if (route == null || route.trim().isEmpty()) {
            callback.accept("No URL configured");
            return;
        }
        BUS.execute(() -> {
            try {
                transmit("WynnicSessions \u2014 webhook test \u2713");
                callback.accept(null);
            } catch (Exception e) {
                callback.accept(e.getMessage());
            }
        });
    }

    public static void closeBus() {
        if (!BUS.isShutdown()) {
            BUS.shutdown();
            try {
                if (!BUS.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    BUS.shutdownNow();
                }
            } catch (InterruptedException error) {
                BUS.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void trace(String message) {
        if (TRACE) {
            System.out.println(message);
        }
    }

    private String fuseBlocks(String left, String right) {
        String cleanLeft = sanitize(left);
        if (cleanLeft.isEmpty()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isEmpty()) {
            return frame("INVENTORY", cleanLeft);
        }
        return right + "\n\n" + frame("INVENTORY", cleanLeft);
    }

    private String composeSession(
        String actor,
        String timer,
        int count,
        int rareDrops,
        String leadName,
        int leadCount,
        double gross,
        double pace,
        int x,
        int y,
        int z
    ) {
        String safeActor = actor == null || actor.isBlank() ? "Unknown" : actor.trim();
        String safeLead = leadName == null || leadName.isBlank() ? "None" : leadName.trim();
        String grossText = gross >= 0 ? String.format("%.0f", gross) : "N/A";
        String paceText = pace >= 0 ? String.format("%.0f", pace) : "N/A";
        String mapLink = "https://map.wynncraft.com/#/" + x + "/" + y + "/" + z + "/-5/wynn-main/Wynncraft";

        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(safeActor)
            .append(" | Pos: ").append(x).append(' ').append(y).append(' ').append(z)
            .append(" | Map: ").append(mapLink);
        sb.append("\n\n");
        sb.append(frame("SESSION",
            "Timer: " + (timer == null ? "N/A" : timer)
            + "\nKills: " + count
            + "\nMythics: " + rareDrops
            + "\nTop loot: " + safeLead + " x" + Math.max(0, leadCount)
            + "\nMoney made: " + grossText
            + "\nEmeralds/hr: " + paceText
        ));
        return sb.toString();
    }

    private String sanitize(String block) {
        if (block == null || block.isEmpty()) {
            return "";
        }
        String[] lines = block.replace("\r", "").split("\n");
        StringBuilder out = new StringBuilder();
        boolean wrote = false;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String clean = line.trim();
            if (clean.isEmpty()) {
                continue;
            }
            if (clean.startsWith("**Player:**") || clean.startsWith("Generated:")) {
                continue;
            }
            String normalized = clean.replace("**", "");
            if (isHeading(normalized) && wrote) {
                out.append("\n");
            }
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append(normalized);
            wrote = true;
        }
        return out.toString();
    }

    private List<String> splitLedgerChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_LIMIT);
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end);
                if (newline > start + (CHUNK_LIMIT / 3)) {
                    end = newline;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            start = end;
            while (start < text.length() && text.charAt(start) == '\n') {
                start++;
            }
        }

        return chunks;
    }

    private String toQuoteBlock(String text) {
        if (text == null || text.isBlank()) {
            return ">>> (empty)";
        }
        return ">>> " + text.replace("\n", "\n>>> ");
    }

    private boolean isHeading(String line) {
        return line != null && line.startsWith("[") && line.endsWith("]") && line.length() > 2;
    }

    private String frame(String title, String content) {
        String safeTitle = title == null ? "INFO" : title.trim().toUpperCase();
        String safeContent = content == null ? "" : content.trim();
        return "```\n" + safeTitle + "\n" + safeContent + "\n```";
    }

    private void transmit(String content) throws Exception {
        String payload = "{\"content\":\"" + escape(content == null ? "" : content.trim()) + "\"}";
        for (int attempt = 0; attempt < 2; attempt++) {
            URL url = new URL(route);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                int code = connection.getResponseCode();
                if (code == 200 || code == 204) {
                    return;
                }
                if (code == 429 && attempt == 0) {
                    Thread.sleep(1500L);
                    continue;
                }
                throw new Exception("Unexpected response code: " + code + " body=" + readBody(connection));
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception("Failed to deliver payload after retry");
    }

    private String readBody(HttpURLConnection connection) {
        try (InputStream stream = connection.getErrorStream()) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
