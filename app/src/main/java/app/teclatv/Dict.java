package app.teclatv;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Diccionario embebido (ES+EN) + palabras aprendidas del usuario. */
final class Dict {
    private final ArrayList<String> words = new ArrayList<>();
    private final ArrayList<String> norm = new ArrayList<>();
    private final HashMap<String, Integer> learned = new HashMap<>();
    private final SharedPreferences prefs;
    private boolean dirty;

    Dict(Context c) {
        prefs = c.getSharedPreferences("learned", Context.MODE_PRIVATE);
        for (String f : new String[]{"es.txt", "en.txt"}) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open(f), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    for (String w : line.trim().split("\\s+")) {
                        if (w.length() > 0) { words.add(w); norm.add(strip(w)); }
                    }
                }
            } catch (Exception ignored) { }
        }
        String s = prefs.getString("data", "");
        for (String ln : s.split("\n")) {
            int t = ln.lastIndexOf('\t');
            if (t > 0) {
                try { learned.put(ln.substring(0, t), Integer.parseInt(ln.substring(t + 1))); } catch (Exception ignored) { }
            }
        }
    }

    static String strip(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    void learn(String w) {
        if (w.length() < 2 || w.length() > 24) return;
        String k = w.toLowerCase();
        Integer n = learned.get(k);
        learned.put(k, n == null ? 1 : Math.min(n + 1, 999));
        dirty = true;
    }

    void save() {
        if (!dirty) return;
        dirty = false;
        if (learned.size() > 3000) {
            ArrayList<Map.Entry<String, Integer>> l = new ArrayList<>(learned.entrySet());
            l.sort((a, b) -> b.getValue() - a.getValue());
            HashMap<String, Integer> keep = new HashMap<>();
            for (int i = 0; i < 2000; i++) keep.put(l.get(i).getKey(), l.get(i).getValue());
            learned.clear();
            learned.putAll(keep);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : learned.entrySet())
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        prefs.edit().putString("data", sb.toString()).apply();
    }

    /** Hasta 3 sugerencias (minúsculas) para el prefijo dado. */
    List<String> suggest(String prefix) {
        ArrayList<String> out = new ArrayList<>(3);
        if (prefix.isEmpty()) return out;
        String p = strip(prefix);
        double[] bs = new double[3];
        String[] bw = new String[3];
        HashMap<String, Boolean> seen = new HashMap<>();
        for (Map.Entry<String, Integer> e : learned.entrySet()) {
            String w = e.getKey();
            if (strip(w).startsWith(p)) consider(bs, bw, w, 1000 + e.getValue() * 10, seen);
        }
        int n = words.size();
        for (int i = 0; i < n; i++) {
            if (norm.get(i).startsWith(p)) consider(bs, bw, words.get(i), 500.0 * (n - i) / n, seen);
        }
        for (int i = 0; i < 3; i++) if (bw[i] != null) out.add(bw[i]);
        if (out.size() == 1 && out.get(0).equalsIgnoreCase(prefix)) out.clear();
        return out;
    }

    private static void consider(double[] bs, String[] bw, String w, double score, HashMap<String, Boolean> seen) {
        if (seen.containsKey(w)) return;
        for (int i = 0; i < 3; i++) {
            if (bw[i] == null || score > bs[i]) {
                for (int j = 2; j > i; j--) { bs[j] = bs[j - 1]; bw[j] = bw[j - 1]; }
                bs[i] = score; bw[i] = w; seen.put(w, true);
                return;
            }
        }
    }
}
