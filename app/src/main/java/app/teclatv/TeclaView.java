package app.teclatv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;

import java.util.List;

/** Teclado dibujado a mano en un solo View (rápido, sin jerarquía de vistas). Navegación por D-pad. */
public final class TeclaView extends View {
    static final int PAGE_ABC = 0, PAGE_SYM = 1, PAGE_ACC = 2;
    static final int MODE_NORMAL = 0, MODE_EMAIL = 1, MODE_URI = 2;
    static final int SHIFT_OFF = 0, SHIFT_ONCE = 1, SHIFT_LOCK = 2;

    private static final int C_BG = 0xFF0C0F1D, C_KEY = 0xFF1F2540, C_SPECIAL = 0xFF2D3558,
            C_SUG = 0xFF161B30, C_TEXT = 0xFFE8EAF6, C_ACCENT = 0xFF22D3EE, C_ON_ACCENT = 0xFF03141A,
            C_AMBER = 0xFFFBBF24, C_DIM = 0xFF7C84A8;

    private final float u;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();

    private Key[][] rows = new Key[0][];
    private final Key[] sugKeys = {new Key("", Key.SUG, 1), new Key("", Key.SUG, 1), new Key("", Key.SUG, 1)};
    private int page = PAGE_ABC, mode = MODE_NORMAL, shift = SHIFT_OFF;
    private String enterLabel = null; // null = icono de retorno
    private int selRow = 2, selCol = 4;
    private float desiredCx = -1;
    private float keyH, gap, barH, padX, padY, panelW;
    private int lastW = -1;

    public TeclaView(Context c) {
        super(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        u = Math.min(dm.widthPixels, dm.heightPixels) / 1080f;
        keyH = 64 * u; gap = 7 * u; barH = 56 * u; padX = 14 * u; padY = 12 * u;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3.2f * u);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setFocusable(false);
        buildRows();
    }

    // ---------- estado ----------
    void setState(int page, int mode, int shift, String enterLabel) {
        boolean rebuild = page != this.page || mode != this.mode || !eq(enterLabel, this.enterLabel);
        this.page = page; this.mode = mode; this.shift = shift; this.enterLabel = enterLabel;
        if (rebuild) buildRows();
        invalidate();
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    int page() { return page; }

    void setSuggestions(List<String> s) {
        boolean ch = false;
        for (int i = 0; i < 3; i++) {
            String w = i < s.size() ? s.get(i) : "";
            if (!w.equals(sugKeys[i].label)) ch = true;
            sugKeys[i] = new Key(w, Key.SUG, 1);
        }
        if (ch) {
            layoutRects();
            if (selRow == 0 && sugKeys[selCol].label.isEmpty()) { selRow = 2; selCol = Math.min(selCol, rows[2].length - 1); }
            invalidate();
        }
    }

    private void buildRows() {
        String[][] def;
        String last = mode == MODE_EMAIL ? "@" : mode == MODE_URI ? "/" : ",";
        String bottom = mode == MODE_URI || mode == MODE_EMAIL
                ? "{sym}:1.4 {acc}:1.2 {space}:2.4 " + last + ":1 .:1 {.com}:1.5 {enter}:1.5"
                : "{sym}:1.5 {acc}:1.5 {space}:3.5 ,:1 .:1 {enter}:1.5";
        String abcBottom = "{abc}:1.5 {acc}:1.5 {space}:3.5 ,:1 .:1 {enter}:1.5";
        String abcBottom2 = "{abc}:1.5 {sym}:1.5 {space}:3.5 ,:1 .:1 {enter}:1.5";
        switch (page) {
            case PAGE_SYM:
                def = new String[][]{
                        sp("1 2 3 4 5 6 7 8 9 0"),
                        sp("@ # $ % & * - + = /"),
                        sp("( ) [ ] \" ' : ; ! ?"),
                        sp("_ ~ ^ < > { } | \\ {del}:1.5"),
                        sp(abcBottom)};
                break;
            case PAGE_ACC:
                def = new String[][]{
                        sp("1 2 3 4 5 6 7 8 9 0"),
                        sp("á é í ó ú ü ñ ç ¿ ¡"),
                        sp("à è ì ò ù â ê î ô û"),
                        sp("ä ë ï ö € £ ¥ ° · {del}:1.5"),
                        sp(abcBottom2)};
                break;
            default:
                def = new String[][]{
                        sp("1 2 3 4 5 6 7 8 9 0"),
                        sp("q w e r t y u i o p"),
                        sp("a s d f g h j k l ñ"),
                        sp("{shift}:1.5 z x c v b n m {del}:1.5"),
                        sp(bottom)};
        }
        rows = new Key[def.length + 1][];
        rows[0] = sugKeys;
        for (int i = 0; i < def.length; i++) {
            Key[] r = new Key[def[i].length];
            for (int j = 0; j < r.length; j++) r[j] = parse(def[i][j]);
            rows[i + 1] = r;
        }
        selRow = Math.min(selRow, rows.length - 1);
        selCol = Math.min(selCol, rows[selRow].length - 1);
        layoutRects();
    }

    private static String[] sp(String s) { return s.split(" "); }

    private Key parse(String tok) {
        float w = 1f;
        int c = tok.lastIndexOf(':');
        if (c > 0 && tok.length() > 1 && tok.charAt(c - 1) != ':' ) {
            try { w = Float.parseFloat(tok.substring(c + 1)); tok = tok.substring(0, c); } catch (Exception ignored) { }
        }
        switch (tok) {
            case "{shift}": return new Key("", Key.SHIFT, w);
            case "{del}": return new Key("", Key.DEL, w);
            case "{space}": return new Key("", Key.SPACE, w);
            case "{enter}": return new Key("", Key.ENTER, w);
            case "{sym}": return new Key("123", Key.SYM, w);
            case "{acc}": return new Key("áü", Key.ACC, w);
            case "{abc}": return new Key("abc", Key.ABC, w);
            case "{.com}": return new Key(".com", Key.TEXT, w);
            default: return new Key(tok, Key.CHAR, w);
        }
    }

    String display(Key k) {
        if (k.type == Key.CHAR && shift != SHIFT_OFF && page != PAGE_SYM) return k.label.toUpperCase();
        return k.label;
    }

    Key selectedKey() { return rows[selRow][selCol]; }

    // ---------- layout ----------
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (padY * 2 + barH + gap + 5 * keyH + 4 * gap);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        lastW = w;
        layoutRects();
    }

    private void layoutRects() {
        if (lastW <= 0) return;
        panelW = Math.min(lastW, 1560 * u);
        float x0 = (lastW - panelW) / 2f + padX, avail = panelW - 2 * padX;
        float y = padY;
        for (int r = 0; r < rows.length; r++) {
            Key[] row = rows[r];
            float sum = 0;
            for (Key k : row) sum += k.w;
            float unit = (avail - gap * (row.length - 1)) / sum;
            float h = r == 0 ? barH : keyH, x = x0;
            for (Key k : row) {
                float w = unit * k.w;
                k.r.set(x, y, x + w, y + h);
                x += w + gap;
            }
            y += h + gap;
        }
    }

    // ---------- navegación ----------
    boolean navigate(int keyCode) {
        Key cur = rows[selRow][selCol];
        if (desiredCx < 0) desiredCx = cur.r.centerX();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int d = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                Key[] row = rows[selRow];
                int c = selCol;
                for (int i = 0; i < row.length; i++) {
                    c = (c + d + row.length) % row.length;
                    if (selRow != 0 || !row[c].label.isEmpty()) break;
                }
                selCol = c;
                desiredCx = row[c].r.centerX();
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: {
                int d = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                int r = selRow;
                for (int i = 0; i < rows.length; i++) {
                    r = (r + d + rows.length) % rows.length;
                    if (r != 0 || hasSuggestions()) break;
                }
                if (r == 0 && d == 1) r = 1; // seguridad
                selRow = r;
                selCol = closest(rows[r]);
                break;
            }
            default: return false;
        }
        invalidate();
        return true;
    }

    private boolean hasSuggestions() {
        for (Key k : sugKeys) if (!k.label.isEmpty()) return true;
        return false;
    }

    private int closest(Key[] row) {
        int best = 0; float bd = Float.MAX_VALUE;
        for (int i = 0; i < row.length; i++) {
            if (row[0].type == Key.SUG && row[i].label.isEmpty()) continue;
            float d = Math.abs(row[i].r.centerX() - desiredCx);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** Selecciona la primera tecla de un tipo (para atajos). */
    void flash() { invalidate(); }

    // ---------- dibujo ----------
    @Override
    protected void onDraw(Canvas cv) {
        cv.drawColor(C_BG);
        float rad = 11 * u;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                Key k = rows[r][c];
                if (r == 0 && k.label.isEmpty()) continue;
                boolean sel = r == selRow && c == selCol;
                int bg = r == 0 ? C_SUG
                        : (k.type == Key.CHAR || k.type == Key.SPACE) ? C_KEY : C_SPECIAL;
                if (sel) {
                    tmp.set(k.r); tmp.inset(-3 * u, -3 * u);
                    fill.setColor(C_ACCENT);
                    cv.drawRoundRect(tmp, rad, rad, fill);
                } else {
                    fill.setColor(bg);
                    cv.drawRoundRect(k.r, rad, rad, fill);
                }
                int fg = sel ? C_ON_ACCENT : C_TEXT;
                drawKey(cv, k, fg, sel);
            }
        }
    }

    private void drawKey(Canvas cv, Key k, int fg, boolean sel) {
        float cx = k.r.centerX(), cy = k.r.centerY();
        stroke.setColor(fg);
        fill.setColor(fg);
        switch (k.type) {
            case Key.SHIFT: {
                int col = shift == SHIFT_LOCK ? (sel ? fg : C_AMBER) : fg;
                stroke.setColor(col); fill.setColor(col);
                float s = keyH * 0.2f;
                path.reset();
                path.moveTo(cx, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx + s * 0.45f, cy);
                path.lineTo(cx + s * 0.45f, cy + s); path.lineTo(cx - s * 0.45f, cy + s);
                path.lineTo(cx - s * 0.45f, cy); path.lineTo(cx - s, cy); path.close();
                if (shift != SHIFT_OFF) { cv.drawPath(path, fill); }
                else cv.drawPath(path, stroke);
                if (shift == SHIFT_LOCK) cv.drawLine(cx - s * 0.45f, cy + s * 1.35f, cx + s * 0.45f, cy + s * 1.35f, stroke);
                break;
            }
            case Key.DEL: {
                float s = keyH * 0.22f;
                path.reset();
                path.moveTo(cx - s * 1.3f, cy); path.lineTo(cx - s * 0.5f, cy - s);
                path.lineTo(cx + s * 1.3f, cy - s); path.lineTo(cx + s * 1.3f, cy + s);
                path.lineTo(cx - s * 0.5f, cy + s); path.close();
                cv.drawPath(path, stroke);
                float q = s * 0.45f, mx = cx + s * 0.4f;
                cv.drawLine(mx - q, cy - q, mx + q, cy + q, stroke);
                cv.drawLine(mx - q, cy + q, mx + q, cy - q, stroke);
                break;
            }
            case Key.ENTER: {
                if (enterLabel != null) { label(cv, enterLabel, cx, cy, 24 * u, fg); break; }
                float s = keyH * 0.2f;
                path.reset();
                path.moveTo(cx + s * 1.2f, cy - s); path.lineTo(cx + s * 1.2f, cy + s * 0.2f);
                path.lineTo(cx - s * 1.1f, cy + s * 0.2f);
                cv.drawPath(path, stroke);
                path.reset();
                path.moveTo(cx - s * 0.3f, cy - s * 0.6f); path.lineTo(cx - s * 1.1f, cy + s * 0.2f);
                path.lineTo(cx - s * 0.3f, cy + s * 1.0f);
                cv.drawPath(path, stroke);
                break;
            }
            case Key.SPACE: {
                float w = Math.min(k.r.width() * 0.25f, 90 * u);
                cv.drawLine(cx - w, cy, cx + w, cy, stroke);
                break;
            }
            case Key.SYM: case Key.ACC: case Key.ABC: case Key.TEXT:
                label(cv, k.label, cx, cy, 24 * u, fg);
                break;
            case Key.SUG:
                label(cv, k.label, cx, cy, 28 * u, fg);
                break;
            default:
                label(cv, display(k), cx, cy, 32 * u, fg);
        }
    }

    private void label(Canvas cv, String s, float cx, float cy, float size, int color) {
        text.setTextSize(size);
        text.setColor(color);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }
}
