package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WynnMarketScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("WynnMarket");
    private static final int C_BG = 0xFF090D18;
    private static final int C_PANEL = 0xFF0A1020;
    private static final int C_LINE = 0xFF1C263D;
    private static final int C_TEXT = 0xFFE5ECFF;
    private static final int C_MUTED = 0xFF667196;
    private static final int C_ACCENT = 0xFF19D9A3;
    private static final int C_INGR = 0xFFFFD580;
    private static final int C_ROW_EVEN = 0xFF0E1728;
    private static final int C_ROW_ODD = 0xFF09121F;

    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 28;
    private static final int ROW_H = 24;
    private static final int CANDIDATE_ROW_H = 30;
    private static final int SCROLLBAR_W = 6;
    private static final int QUICK_PANEL_H = 54;
    private static final int CHIP_H = 16;
    private static final int CHIP_PAD_X = 6;
    private static final int CHIP_GAP = 6;
    private static final Pattern SCRAPE_AVG_30D = Pattern.compile("30d Avg\\s*\\R\\s*([0-9][0-9,]*)");
    private static final Pattern SCRAPE_LOWEST = Pattern.compile("Recent Lowest\\s*\\R\\s*([0-9][0-9,]*)");
    private static final Pattern SCRAPE_META = Pattern.compile("Recently Listed\\s*[\\s\\S]{0,120}?([0-9,]+\\s*items.?[0-9,]+\\s*listings)");
    private static final Pattern SCRAPE_VOLATILITY = Pattern.compile("Volatility\\s*\\R\\s*([0-9]+)");
    private static final Pattern SCRAPE_EVENT = Pattern.compile(
        "([0-9][0-9,]*)!\\[Image[^\\]]*:\\s*(E|EB|LE)\\]\\([^)]*\\)"
        + "|"
        + "([0-9][0-9,]*)\\s*([0-9]+[a-z]+)\\s+ago",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern AGE_TOKEN_PATTERN = Pattern.compile("(\\d+)([a-z]+)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RECENT = 5;

    private static class ListingEntry {
        final double pricePerUnitEm;
        final int quantity;
        final String timeAgo;

        ListingEntry(double pricePerUnitEm, int quantity, String timeAgo) {
            this.pricePerUnitEm = pricePerUnitEm;
            this.quantity = quantity;
            this.timeAgo = timeAgo;
        }
    }

    private static class SearchResolution {
        final String exactName;
        final long avg30d;
        final long lowest;
        final int listingCount;

        SearchResolution(String exactName, long avg30d, long lowest, int listingCount) {
            this.exactName = exactName;
            this.avg30d = avg30d;
            this.lowest = lowest;
            this.listingCount = listingCount;
        }
    }

    private static class SearchCandidate {
        final String name;
        final long avg30d;
        final long lowest;
        final int listingCount;

        SearchCandidate(String name, long avg30d, long lowest, int listingCount) {
            this.name = name;
            this.avg30d = avg30d;
            this.lowest = lowest;
            this.listingCount = listingCount;
        }
    }

    private static class QuickChip {
        final int x;
        final int y;
        final int w;
        final int h;
        final String value;

        QuickChip(int x, int y, int w, int h, String value) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.value = value;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private final Screen parent;
    private EditBox searchBox;

    private final List<ListingEntry> listings = new ArrayList<>();
    private final List<SearchCandidate> candidates = new ArrayList<>();
    private final List<String> recentSearches = new ArrayList<>();
    private final List<String> recentTopLoots = new ArrayList<>();
    private final List<QuickChip> recentSearchChips = new ArrayList<>();
    private final List<QuickChip> recentTopLootChips = new ArrayList<>();

    private String status = "Type an item name then press Enter";
    private boolean isSearching = false;
    private int scrollOffset = 0;
    private String searchedItemName = "";
    private long recentLowest = -1;
    private long avg30d = -1;
    private int volatility = -1;
    private String listingsMeta = "";

    private boolean isDraggingScrollbar = false;
    private int dragScrollY = 0;
    private int dragScrollOffset = 0;

    public WynnMarketScreen(Screen parent) {
        super(Component.literal("Wynnic Market"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int searchX = 14;
        int searchW = Math.max(180, this.width - searchX - 14);

        searchBox = new EditBox(this.font, searchX, HEADER_H + 8, searchW, 20, Component.literal("Search..."));
        searchBox.setMaxLength(80);
        searchBox.setFocused(true);
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);
        this.setFocused(searchBox);

        refreshRecentTopLoots();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, C_BG);

        gui.fill(0, 0, this.width, HEADER_H, C_PANEL);
        gui.fill(0, HEADER_H - 1, this.width, HEADER_H, C_LINE);

        gui.pose().pushMatrix();
        gui.pose().translate(14, 9);
        gui.pose().scale(1.3f, 1.3f);
        gui.drawString(this.font, "Wynnic Market", 0, 0, C_INGR, false);
        gui.pose().popMatrix();
        gui.drawString(this.font, "In-game item search", 14, 26, C_MUTED, false);

        String hint = isSearching ? "Searching..." : "Press Enter to search";
        int hintColor = isSearching ? C_ACCENT : C_MUTED;
        gui.drawString(this.font, hint, this.width - this.font.width(hint) - 14, 22, hintColor, false);

        super.render(gui, mouseX, mouseY, partialTicks);

        int contentTop = getContentTop();
        int contentBottom = this.height - FOOTER_H;

        renderQuickAccessPanel(gui, mouseX, mouseY, HEADER_H + 34);

        if (isSearching) {
            gui.drawCenteredString(this.font, "⌛ Searching...", this.width / 2, contentTop + 24, C_ACCENT);
        } else if (!candidates.isEmpty()) {
            renderCandidatesPanel(gui, mouseX, mouseY, contentTop, contentBottom);
        } else if (listings.isEmpty()) {
            if (!searchedItemName.isEmpty()) {
                renderSummaryPanel(gui, contentTop, true);
                gui.drawCenteredString(this.font, status, this.width / 2, contentTop + 78, C_MUTED);
            } else {
                gui.drawCenteredString(this.font, status, this.width / 2, contentTop + 24, C_MUTED);
            }
        } else {
            renderListingsPanel(gui, mouseX, mouseY, contentTop, contentBottom);
        }

        gui.fill(0, contentBottom, this.width, this.height, C_PANEL);
        gui.fill(0, contentBottom, this.width, contentBottom + 1, C_LINE);
        gui.drawString(this.font, "ESC: Back | Scroll: Navigate", 10, contentBottom + 8, C_MUTED, false);

        if (!candidates.isEmpty()) {
            String cnt = candidates.size() + (candidates.size() > 1 ? " matches" : " match");
            gui.drawString(this.font, cnt, this.width - this.font.width(cnt) - 10, contentBottom + 8, C_ACCENT, false);
        } else if (!listings.isEmpty()) {
            String cnt = "Top " + listings.size() + " latest listings";
            gui.drawString(this.font, cnt, this.width - this.font.width(cnt) - 10, contentBottom + 8, C_ACCENT, false);
        }
    }

    private void renderListingsPanel(GuiGraphics gui, int mouseX, int mouseY, int contentTop, int contentBottom) {
        renderSummaryPanel(gui, contentTop, false);

        int headY = contentTop + 46;
        gui.fill(0, headY, this.width, headY + 20, 0xFF101D30);
        gui.drawString(this.font, "Price", 10, headY + 5, C_ACCENT, false);
        gui.drawString(this.font, "Qty", this.width / 2 - 18, headY + 5, C_ACCENT, false);
        gui.drawString(this.font, "Time", this.width - 74, headY + 5, C_ACCENT, false);
        gui.fill(0, headY + 19, this.width, headY + 20, C_LINE);

        int listTop = headY + 20;
        int y = listTop - scrollOffset;
        for (int i = 0; i < listings.size(); i++) {
            ListingEntry r = listings.get(i);
            int ry = y + i * ROW_H;
            if (ry + ROW_H < listTop || ry > contentBottom) continue;

            int rowBg = (i % 2 == 0) ? C_ROW_EVEN : C_ROW_ODD;
            gui.fill(0, ry, this.width, ry + ROW_H - 1, rowBg);
            gui.fill(0, ry + ROW_H - 1, this.width, ry + ROW_H, C_LINE);
            gui.fill(0, ry, 3, ry + ROW_H - 1, C_INGR);

            gui.drawString(this.font, formatPrice(Math.round(r.pricePerUnitEm)), 10, ry + 7, C_INGR, false);
            gui.drawString(this.font, formatInt(r.quantity), this.width / 2 - 18, ry + 7, C_TEXT, false);
            gui.drawString(this.font, r.timeAgo, this.width - 74, ry + 7, C_MUTED, false);
        }

        if (listings.size() * ROW_H - scrollOffset > (contentBottom - listTop)) {
            gui.fill(0, contentBottom - 16, this.width, contentBottom,
                0x00000000 | (C_BG & 0x00FFFFFF) | 0xAA000000);
        }

        renderScrollbar(gui, mouseX, mouseY, listTop, contentBottom, listings.size() * ROW_H);
    }

    private void renderCandidatesPanel(GuiGraphics gui, int mouseX, int mouseY, int contentTop, int contentBottom) {
        gui.fill(0, contentTop, this.width, contentTop + 34, 0xFF101A2A);
        gui.drawString(this.font, "Multiple items found. Click one:", 10, contentTop + 6, C_TEXT, false);
        gui.drawString(this.font, "Sorted alphabetically", this.width - 130, contentTop + 6, C_MUTED, false);

        int listTop = contentTop + 34;
        int y = listTop - scrollOffset;
        for (int i = 0; i < candidates.size(); i++) {
            SearchCandidate candidate = candidates.get(i);
            int ry = y + i * CANDIDATE_ROW_H;
            if (ry + CANDIDATE_ROW_H < listTop || ry > contentBottom) continue;

            boolean hovered = mouseX >= 0 && mouseX <= this.width && mouseY >= ry && mouseY <= ry + CANDIDATE_ROW_H;
            int rowBg = hovered ? 0xFF163052 : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            gui.fill(0, ry, this.width, ry + CANDIDATE_ROW_H - 1, rowBg);
            gui.fill(0, ry + CANDIDATE_ROW_H - 1, this.width, ry + CANDIDATE_ROW_H, C_LINE);
            gui.fill(0, ry, 4, ry + CANDIDATE_ROW_H - 1, C_ACCENT);

            gui.pose().pushMatrix();
            gui.pose().translate(12, ry + 8);
            gui.pose().scale(1.15f, 1.15f);
            gui.drawString(this.font, candidate.name, 0, 0, C_TEXT, false);
            gui.pose().popMatrix();

            String meta = buildCandidateMeta(candidate);
            gui.drawString(this.font, meta, this.width - this.font.width(meta) - 10, ry + 10, C_MUTED, false);
        }

        renderScrollbar(gui, mouseX, mouseY, listTop, contentBottom, candidates.size() * CANDIDATE_ROW_H);
    }

    private int getContentTop() {
        return HEADER_H + 34 + QUICK_PANEL_H;
    }

    private void renderQuickAccessPanel(GuiGraphics gui, int mouseX, int mouseY, int panelTop) {
        int panelBottom = panelTop + QUICK_PANEL_H;
        gui.fill(0, panelTop, this.width, panelBottom, 0xFF0F192A);
        gui.fill(0, panelBottom - 1, this.width, panelBottom, C_LINE);

        recentSearchChips.clear();
        recentTopLootChips.clear();

        gui.drawString(this.font, "Recent searches:", 10, panelTop + 6, C_MUTED, false);
        drawQuickChips(gui, recentSearches, recentSearchChips, 118, panelTop + 4, mouseX, mouseY, C_ACCENT);

        gui.drawString(this.font, "Recent top loot:", 10, panelTop + 28, C_MUTED, false);
        drawQuickChips(gui, recentTopLoots, recentTopLootChips, 118, panelTop + 26, mouseX, mouseY, C_INGR);
    }

    private void drawQuickChips(
        GuiGraphics gui,
        List<String> values,
        List<QuickChip> chipsOut,
        int startX,
        int y,
        int mouseX,
        int mouseY,
        int accentColor
    ) {
        int x = startX;
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            String value = raw.trim();
            int w = this.font.width(value) + CHIP_PAD_X * 2;
            if (x + w > this.width - 10) break;

            QuickChip chip = new QuickChip(x, y, w, CHIP_H, value);
            chipsOut.add(chip);

            boolean hovered = chip.contains(mouseX, mouseY);
            int bg = hovered ? 0x66304E7A : 0x3320304A;
            gui.fill(chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, bg);
            gui.fill(chip.x, chip.y + chip.h - 1, chip.x + chip.w, chip.y + chip.h, 0x553A4D6D);
            gui.drawString(this.font, value, chip.x + CHIP_PAD_X, chip.y + 4, hovered ? accentColor : C_TEXT, false);

            x += w + CHIP_GAP;
        }
    }



    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            && searchBox != null && searchBox.isFocused()) {
            triggerSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (super.mouseClicked(event, consumed)) return true;
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) return false;

        if (tryQuickChipClick(mx, my)) {
            return true;
        }

        int contentTop = getContentTop();
        int contentBottom = this.height - FOOTER_H;

        if (!candidates.isEmpty()) {
            int listTop = contentTop + 34;
            int clickedIndex = (int) ((my + scrollOffset - listTop) / CANDIDATE_ROW_H);
            if (clickedIndex >= 0 && clickedIndex < candidates.size() && my >= listTop && my <= contentBottom) {
                SearchCandidate chosen = candidates.get(clickedIndex);
                startSearchForResolvedItem(new SearchResolution(
                    chosen.name,
                    chosen.avg30d,
                    chosen.lowest,
                    chosen.listingCount
                ));
                return true;
            }
            return tryStartScrollbarDrag(mx, my, listTop, contentBottom, candidates.size() * CANDIDATE_ROW_H);
        }

        if (!listings.isEmpty()) {
            int listTop = contentTop + 46 + 20;
            return tryStartScrollbarDrag(mx, my, listTop, contentBottom, listings.size() * ROW_H);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (isDraggingScrollbar && event.button() == 0) {
            int contentTop = getContentTop();
            int contentBottom = this.height - FOOTER_H;
            int listTop = !candidates.isEmpty() ? (contentTop + 34) : (contentTop + 46 + 20);
            int totalH = !candidates.isEmpty()
                ? candidates.size() * CANDIDATE_ROW_H
                : listings.size() * ROW_H;
            updateScrollFromDrag((int) event.y(), listTop, contentBottom, totalH);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (isDraggingScrollbar && event.button() == 0) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!candidates.isEmpty()) {
            int listTop = getContentTop() + 34;
            int listHeight = this.height - FOOTER_H - listTop;
            int maxScroll = Math.max(0, candidates.size() * CANDIDATE_ROW_H - listHeight);
            scrollOffset = (int) Math.max(0, Math.min(scrollOffset - sy * 14, maxScroll));
            return true;
        }
        if (!listings.isEmpty()) {
            int listTop = getContentTop() + 46 + 20;
            int listHeight = this.height - FOOTER_H - listTop;
            int maxScroll = Math.max(0, listings.size() * ROW_H - listHeight);
            scrollOffset = (int) Math.max(0, Math.min(scrollOffset - sy * 14, maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private boolean tryQuickChipClick(double mx, double my) {
        for (QuickChip chip : recentSearchChips) {
            if (chip.contains(mx, my)) {
                if (searchBox != null) {
                    searchBox.setValue(chip.value);
                    searchBox.setCursorPosition(chip.value.length());
                    searchBox.setFocused(true);
                    this.setFocused(searchBox);
                }
                triggerSearch();
                return true;
            }
        }

        for (QuickChip chip : recentTopLootChips) {
            if (chip.contains(mx, my)) {
                if (searchBox != null) {
                    searchBox.setValue(chip.value);
                    searchBox.setCursorPosition(chip.value.length());
                    searchBox.setFocused(true);
                    this.setFocused(searchBox);
                }
                triggerSearch();
                return true;
            }
        }
        return false;
    }

    private void addRecentSearch(String raw) {
        String value = normalizeSearchQuery(raw);
        if (value.isEmpty()) return;
        recentSearches.removeIf(s -> s != null && s.equalsIgnoreCase(value));
        recentSearches.add(0, value);
        while (recentSearches.size() > MAX_RECENT) {
            recentSearches.remove(recentSearches.size() - 1);
        }
    }

    private void refreshRecentTopLoots() {
        recentTopLoots.clear();
        recentTopLoots.addAll(ConfigManager.loadRecentTopLootItems(MAX_RECENT));
    }

    private void triggerSearch() {
        String query = normalizeSearchQuery(searchBox.getValue());
        if (query.isEmpty() || isSearching) return;

        addRecentSearch(query);

        isSearching = true;
        listings.clear();
        candidates.clear();
        scrollOffset = 0;
        searchedItemName = query;
        recentLowest = -1;
        avg30d = -1;
        volatility = -1;
        listingsMeta = "";

        new Thread(() -> {
            try {
                List<SearchResolution> matches = resolveSearchMatches(query);
                if (matches.isEmpty()) {
                    status = "Item not found on WynnMarket";
                    isSearching = false;
                    return;
                }

                if (matches.size() > 1) {
                    candidates.clear();
                    for (SearchResolution match : matches) {
                        candidates.add(new SearchCandidate(match.exactName, match.avg30d, match.lowest, match.listingCount));
                    }
                    candidates.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
                    status = "Multiple matches found";
                    isSearching = false;
                    return;
                }

                startSearchForResolvedItem(matches.get(0));
            } catch (Exception e) {
                LOGGER.error("[WynnMarket] Search error", e);
                status = "Error: " + e.getMessage();
                listings.clear();
                candidates.clear();
                isSearching = false;
            }
        }, "wynnmarket-search").start();
    }

    private void startSearchForResolvedItem(SearchResolution resolution) {
        if (resolution == null || resolution.exactName == null || resolution.exactName.isEmpty()) {
            status = "Item not found on WynnMarket";
            isSearching = false;
            return;
        }

        addRecentSearch(resolution.exactName);

        isSearching = true;
        listings.clear();
        candidates.clear();
        scrollOffset = 0;

        new Thread(() -> {
            try {
                searchedItemName = resolution.exactName;
                avg30d = resolution.avg30d > 0 ? resolution.avg30d : -1;
                recentLowest = resolution.lowest > 0 ? resolution.lowest : -1;
                listingsMeta = resolution.listingCount >= 0
                    ? resolution.listingCount + (resolution.listingCount > 1 ? " listings" : " listing")
                    : "";

                String encoded = URLEncoder.encode(resolution.exactName, StandardCharsets.UTF_8.toString()).replace("+", "%20");
                String url = "https://r.jina.ai/http://wynnmarket.com/items/" + encoded;
                String page = fetchText(url, 15000);

                Matcher avgMatcher = SCRAPE_AVG_30D.matcher(page);
                if (avgMatcher.find()) avg30d = parseIntSafe(avgMatcher.group(1));

                Matcher lowMatcher = SCRAPE_LOWEST.matcher(page);
                if (lowMatcher.find()) recentLowest = parseIntSafe(lowMatcher.group(1));

                Matcher metaMatcher = SCRAPE_META.matcher(page);
                if (metaMatcher.find()) listingsMeta = metaMatcher.group(1).replace('·', ' ').replace("  ", " ").trim();

                Matcher volMatcher = SCRAPE_VOLATILITY.matcher(page);
                if (volMatcher.find()) volatility = parseIntSafe(volMatcher.group(1));

                int start = page.indexOf("Recently Listed");
                int end = page.indexOf("Data provided by", Math.max(0, start));
                String section = (start >= 0)
                    ? page.substring(start, end > start ? end : page.length())
                    : page;

                Matcher events = SCRAPE_EVENT.matcher(section);
                double pendingPrice = 0;
                while (events.find()) {
                    if (events.group(1) != null && events.group(2) != null) {
                        int amount = parseIntSafe(events.group(1));
                        String cur = events.group(2).toUpperCase(Locale.ROOT);
                        if ("LE".equals(cur)) pendingPrice += amount * 4096.0;
                        else if ("EB".equals(cur)) pendingPrice += amount * 64.0;
                        else pendingPrice += amount;
                        continue;
                    }

                    if (events.group(3) != null && events.group(4) != null && pendingPrice > 0) {
                        int qty = parseIntSafe(events.group(3));
                        String timeAgo = events.group(4) + " ago";
                        listings.add(new ListingEntry(pendingPrice, qty, timeAgo));
                        pendingPrice = 0;
                        if (listings.size() >= 100) break;
                    }
                }

                if (!listings.isEmpty()) {
                    List<ListingEntry> considered = new ArrayList<>();
                    for (ListingEntry entry : listings) {
                        double d = ageInDays(entry.timeAgo);
                        if (d >= 0 && d <= 7.0) considered.add(entry);
                    }
                    if (considered.isEmpty()) {
                        for (ListingEntry entry : listings) {
                            double d = ageInDays(entry.timeAgo);
                            if (d >= 0 && d <= 30.0) considered.add(entry);
                        }
                    }
                    if (considered.isEmpty()) considered = listings;

                    double minPrice = Double.MAX_VALUE;
                    for (ListingEntry entry : considered) {
                        if (entry.pricePerUnitEm > 0 && entry.pricePerUnitEm < minPrice) minPrice = entry.pricePerUnitEm;
                    }
                    if (minPrice != Double.MAX_VALUE) {
                        recentLowest = Math.round(minPrice);

                        double cap = minPrice * 3.0;
                        double sumPrice = 0;
                        int count = 0;
                        for (ListingEntry entry : considered) {
                            if (entry.pricePerUnitEm > 0 && entry.pricePerUnitEm <= cap) {
                                sumPrice += entry.pricePerUnitEm;
                                count++;
                            }
                        }
                        avg30d = count > 0 ? Math.round(sumPrice / count) : recentLowest;
                    }
                }

                status = listings.isEmpty()
                    ? "No recent listings for «" + resolution.exactName + "»"
                    : "";
            } catch (Exception e) {
                LOGGER.error("[WynnMarket] Search resolved error", e);
                status = "Error: " + e.getMessage();
                listings.clear();
            }
            isSearching = false;
        }, "wynnmarket-search-resolved").start();
    }

    private List<SearchResolution> resolveSearchMatches(String query) throws Exception {
        String cleanedQuery = normalizeSearchQuery(query);
        if (cleanedQuery.isEmpty()) return List.of();

        JsonArray items = fetchSearchItems(cleanedQuery);
        if (items == null || items.isEmpty()) return List.of();

        String q = normalizeForComparison(cleanedQuery);
        Map<String, SearchResolution> byName = new LinkedHashMap<>();
        boolean hasPrefixOrExact = false;

        for (JsonElement el : items) {
            if (!el.isJsonObject()) continue;
            SearchResolution res = toSearchResolution(el.getAsJsonObject());
            if (res == null || res.exactName == null || res.exactName.isEmpty()) continue;

            String normalizedName = normalizeForComparison(res.exactName);
            if (!normalizedName.isEmpty() && (normalizedName.equals(q) || normalizedName.startsWith(q))) {
                hasPrefixOrExact = true;
            }
            byName.putIfAbsent(normalizedName, res);
        }

        List<SearchResolution> out = new ArrayList<>(byName.values());
        if (out.isEmpty()) return List.of();

        List<SearchResolution> filtered = new ArrayList<>();
        for (SearchResolution res : out) {
            String n = normalizeForComparison(res.exactName);
            if (hasPrefixOrExact) {
                if (n.equals(q) || n.startsWith(q)) filtered.add(res);
            } else {
                if (matchScore(q, n) > 0) filtered.add(res);
            }
        }
        if (filtered.isEmpty()) filtered = out;

        filtered.sort(Comparator.comparing(r -> r.exactName.toLowerCase(Locale.ROOT)));
        return filtered;
    }

    private SearchResolution toSearchResolution(JsonObject obj) {
        if (obj == null || !obj.has("name") || obj.get("name").isJsonNull()) return null;

        String exactName = obj.get("name").getAsString();
        if (exactName == null || exactName.isEmpty()) return null;

        long avg = obj.has("avg_30d") && !obj.get("avg_30d").isJsonNull()
            ? Math.round(obj.get("avg_30d").getAsDouble())
            : -1;
        long lowest = -1;
        if (obj.has("listingSummary") && obj.get("listingSummary").isJsonObject()) {
            JsonObject listingSummary = obj.getAsJsonObject("listingSummary");
            if (listingSummary.has("min") && !listingSummary.get("min").isJsonNull()) {
                lowest = Math.round(listingSummary.get("min").getAsDouble());
            }
        }
        int listingCount = obj.has("listing_count") && !obj.get("listing_count").isJsonNull()
            ? obj.get("listing_count").getAsInt()
            : -1;

        return new SearchResolution(exactName, avg, lowest, listingCount);
    }

    private JsonArray fetchSearchItems(String cleanedQuery) throws Exception {
        String[] attempts = {
            cleanedQuery,
            normalizeForComparison(cleanedQuery),
            cleanedQuery.replace('-', ' ').replace('_', ' ').trim()
        };

        for (String attempt : attempts) {
            if (attempt == null || attempt.isBlank()) continue;

            String encoded = URLEncoder.encode(attempt, StandardCharsets.UTF_8.toString());
            String body = fetchText("https://wynnmarket.com/api/items/page?search=" + encoded, 8000);
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) continue;

            JsonObject payload = root.getAsJsonObject();
            if (!payload.has("items") || !payload.get("items").isJsonArray()) continue;

            JsonArray items = payload.getAsJsonArray("items");
            if (!items.isEmpty()) return items;
        }

        return null;
    }

    private int matchScore(String query, String candidate) {
        if (candidate.equals(query)) return 1000;
        if (candidate.startsWith(query)) return 700 - Math.abs(candidate.length() - query.length());
        if (candidate.contains(query)) return 500 - Math.abs(candidate.length() - query.length());

        int overlap = 0;
        String[] queryTokens = query.split(" ");
        for (String token : queryTokens) {
            if (!token.isEmpty() && candidate.contains(token)) overlap++;
        }
        return overlap * 100 - Math.abs(candidate.length() - query.length());
    }

    private String buildCandidateMeta(SearchCandidate candidate) {
        String low = candidate.lowest >= 0 ? formatPrice(candidate.lowest) : "-";
        String avg = candidate.avg30d >= 0 ? formatPrice(candidate.avg30d) : "-";
        String cnt = candidate.listingCount >= 0
            ? candidate.listingCount + (candidate.listingCount > 1 ? " listings" : " listing")
            : "-";
        return "Low " + low + " | Avg " + avg + " | " + cnt;
    }

    private void renderSummaryPanel(GuiGraphics gui, int contentTop, boolean noListings) {
        gui.fill(0, contentTop, this.width, contentTop + 46, 0xFF101A2A);
        gui.drawString(this.font, "Item: " + searchedItemName, 10, contentTop + 4, C_TEXT, false);
        gui.drawString(this.font, "Lowest: " + formatPrice(recentLowest), 10, contentTop + 16, C_INGR, false);
        gui.drawString(this.font, "Average: " + formatPrice(avg30d), this.width / 2 - 40, contentTop + 16, C_ACCENT, false);

        if (volatility >= 0) {
            int volColor;
            String volLabel;
            if (volatility <= 20) {
                volColor = 0xFF19D9A3;
                volLabel = "Stable";
            } else if (volatility <= 50) {
                volColor = 0xFFFFD580;
                volLabel = "Moderate";
            } else if (volatility <= 75) {
                volColor = 0xFFFF9040;
                volLabel = "Volatile";
            } else {
                volColor = 0xFFFF4444;
                volLabel = "Very Volatile";
            }
            String volText = "Volatility: " + volatility + "/100 (" + volLabel + ")";
            gui.drawString(this.font, volText, 10, contentTop + 28, volColor, false);
        }

        String meta = listingsMeta;
        if (meta.isEmpty() && noListings) meta = "0 listing";
        gui.drawString(this.font, meta, this.width - this.font.width(meta) - 10, contentTop + 28, C_MUTED, false);
    }

    private boolean tryStartScrollbarDrag(double mx, double my, int listTop, int contentBottom, int totalH) {
        int visibleH = contentBottom - listTop;
        if (totalH <= visibleH) return false;

        int trackX = this.width - SCROLLBAR_W - 2;
        if (mx < trackX || mx > trackX + SCROLLBAR_W || my < listTop || my > contentBottom) {
            return false;
        }

        isDraggingScrollbar = true;
        dragScrollY = (int) my;
        dragScrollOffset = scrollOffset;
        return true;
    }

    private void updateScrollFromDrag(int mouseY, int listTop, int contentBottom, int totalH) {
        int visibleH = contentBottom - listTop;
        int trackH = contentBottom - listTop;
        int thumbH = Math.max(20, (int) ((long) visibleH * trackH / totalH));
        int maxScroll = Math.max(0, totalH - visibleH);
        int scrollRange = trackH - thumbH;
        int delta = mouseY - dragScrollY;
        scrollOffset = scrollRange > 0
            ? (int) Math.max(0, Math.min(dragScrollOffset + (long) delta * maxScroll / scrollRange, maxScroll))
            : 0;
    }

    private void renderScrollbar(GuiGraphics gui, int mouseX, int mouseY, int listTop, int contentBottom, int totalH) {
        int visibleH = contentBottom - listTop;
        if (totalH <= visibleH) return;

        int trackX = this.width - SCROLLBAR_W - 2;
        int trackH = contentBottom - listTop;
        gui.fill(trackX, listTop, trackX + SCROLLBAR_W, contentBottom, 0x33FFFFFF);

        int thumbH = Math.max(20, (int) ((long) visibleH * trackH / totalH));
        int maxScroll = totalH - visibleH;
        int thumbY = listTop + (maxScroll > 0 ? (int) ((long) scrollOffset * (trackH - thumbH) / maxScroll) : 0);

        boolean hoverThumb = mouseX >= trackX && mouseX <= trackX + SCROLLBAR_W
            && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int thumbColor = isDraggingScrollbar || hoverThumb ? 0xBBFFFFFF : 0x77FFFFFF;
        gui.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, thumbColor);
    }

    private String fetchText(String url, int readTimeoutMs) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "WynnicSessions-Mod/1.0");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(readTimeoutMs);

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new RuntimeException("HTTP " + code);
        }

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String normalizeSearchQuery(String query) {
        if (query == null) return "";

        String cleaned = Normalizer.normalize(query, Normalizer.Form.NFKC);
        cleaned = cleaned.replaceAll("§[0-9a-fk-or]", "");
        cleaned = cleaned.replaceAll("\\[[^\\]]*\\]", "");
        cleaned = cleaned.replaceAll("\\([^\\)]*\\)", " ");
        cleaned = cleaned.replaceAll("[✫★✦✧✪☆✩✯⭐🌟💫*]", "");
        cleaned = cleaned.replaceAll("^x\\d+\\s+", "");
        cleaned = cleaned.replaceAll("^\\+\\d+\\s+", "");
        cleaned = cleaned.replaceAll("[^\\p{L}\\p{N}' -]", " ");
        return cleaned.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeForComparison(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    private static double ageInDays(String timeAgo) {
        Matcher m = AGE_TOKEN_PATTERN.matcher(timeAgo == null ? "" : timeAgo);
        if (!m.find()) return -1;
        int val = parseIntSafe(m.group(1));
        if (val < 0) return -1;
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        if (unit.startsWith("m") && !unit.startsWith("mo")) return val / 1440.0;
        if (unit.startsWith("h")) return val / 24.0;
        if (unit.startsWith("d")) return val;
        if (unit.startsWith("w")) return val * 7.0;
        if (unit.startsWith("mo")) return val * 30.0;
        if (unit.startsWith("y")) return val * 365.0;
        return -1;
    }

    private static String formatInt(int n) {
        if (n < 0) return "-";
        return String.format("%,d", n);
    }

    private static String formatPrice(long em) {
        if (em < 0) return "-";
        return MobKillerCalculatorClient.formatWynnCurrencyCompact(em);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
