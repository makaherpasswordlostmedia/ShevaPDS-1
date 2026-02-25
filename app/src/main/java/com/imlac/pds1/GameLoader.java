package com.imlac.pds1;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Manages custom user-written games (assembly programs).
 * Stored in SharedPreferences as JSON.
 *
 * Each game: { "name": "...", "source": "...", "desc": "..." }
 */
public class GameLoader {

    private static final String PREFS    = "imlac_games";
    private static final String KEY_LIST = "games_json";

    public static class Game {
        public String name;
        public String source;
        public String desc;
        public long   timestamp;

        public Game(String name, String source, String desc) {
            this.name = name;
            this.source = source;
            this.desc = desc;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final SharedPreferences prefs;
    private final List<Game> games = new ArrayList<>();

    public GameLoader(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    // ── Persistence ──────────────────────────────────────────
    private void load() {
        games.clear();
        String json = prefs.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Game g = new Game(
                    o.optString("name", "unnamed"),
                    o.optString("source", ""),
                    o.optString("desc", "")
                );
                g.timestamp = o.optLong("ts", 0);
                games.add(g);
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Game g : games) {
                JSONObject o = new JSONObject();
                o.put("name",   g.name);
                o.put("source", g.source);
                o.put("desc",   g.desc);
                o.put("ts",     g.timestamp);
                arr.put(o);
            }
            prefs.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ── CRUD ─────────────────────────────────────────────────
    public List<Game> getGames() { return Collections.unmodifiableList(games); }

    public void saveGame(String name, String source, String desc) {
        for (Game g : games) {
            if (g.name.equals(name)) {
                g.source = source;
                g.desc = desc;
                g.timestamp = System.currentTimeMillis();
                save();
                return;
            }
        }
        games.add(new Game(name, source, desc));
        save();
    }

    public void deleteGame(String name) {
        games.removeIf(g -> g.name.equals(name));
        save();
    }

    public Game findGame(String name) {
        for (Game g : games) if (g.name.equals(name)) return g;
        return null;
    }

    // ── Built-in example programs ─────────────────────────────
    public static final String EXAMPLE_COUNTER =
        "; Binary counter 0-15 via TTY\n" +
        "        ORG     0050\n" +
        "START:  CLA\n" +
        "        DAC     COUNT\n" +
        "LOOP:   LDA     COUNT\n" +
        "        IOT     0104    ; TTY output\n" +
        "        ISP     COUNT\n" +
        "        LDA     COUNT\n" +
        "        AND     MASK\n" +
        "        SKZ\n" +
        "        JMP     LOOP\n" +
        "        HLT\n" +
        "COUNT:  DATA    0\n" +
        "MASK:   DATA    0017\n";

    public static final String EXAMPLE_DRAW_BOX =
        "; Draw a box on the vector display\n" +
        "        .DP\n" +
        "        ORG     0100\n" +
        "        DLXA    0200    ; X = 200\n" +
        "        DLYA    0200    ; Y = 200\n" +
        "        DSVH    0x0640  ; right\n" +
        "        DSVH    0x0006  ; up\n" +
        "        DSVH    0x0840  ; left\n" +
        "        DSVH    0x0806  ; down\n" +
        "        DHLT\n";

    public static final String EXAMPLE_SINE_WAVE =
        "; Sine wave via display list\n" +
        "; (Uses pre-computed table trick)\n" +
        "        ORG     0050\n" +
        "        .DP\n" +
        "        ORG     0100\n" +
        "DSTART: DLXA    0100\n" +
        "        DLYA    0400\n" +
        "        DJMS    SINE\n" +
        "        DHLT\n" +
        "SINE:   DSVH    0x0102\n" +
        "        DSVH    0x0103\n" +
        "        DSVH    0x0103\n" +
        "        DSVH    0x0102\n" +
        "        DSVH    0x0101\n" +
        "        DSVH    0x8102\n" +
        "        DSVH    0x8103\n" +
        "        DSVH    0x8103\n" +
        "        DSVH    0x8102\n" +
        "        DRJM\n";
}
