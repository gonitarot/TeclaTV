package app.teclatv;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.HashSet;
import java.util.List;

public class TeclaService extends InputMethodService {
    private TeclaView view;
    private Dict dict;
    private SharedPreferences prefs;
    private AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashSet<Integer> consumed = new HashSet<>();

    private int page = TeclaView.PAGE_ABC, mode = TeclaView.MODE_NORMAL, shift = TeclaView.SHIFT_OFF;
    private boolean password, noSuggest, multiline;
    private int imeAction = EditorInfo.IME_ACTION_NONE;
    private String actionLabel = null;
    private EditorInfo info;
    private final Runnable refresh = this::refreshSuggestions;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        dict = new Dict(this);
    }

    @Override
    public View onCreateInputView() {
        view = new TeclaView(this);
        return view;
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    @Override
    public void onStartInputView(EditorInfo ei, boolean restarting) {
        super.onStartInputView(ei, restarting);
        info = ei;
        int cls = ei.inputType & InputType.TYPE_MASK_CLASS;
        int var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        password = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        noSuggest = password || (ei.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || var == InputType.TYPE_TEXT_VARIATION_URI;
        multiline = (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        mode = TeclaView.MODE_NORMAL;
        page = TeclaView.PAGE_ABC;
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) {
            page = TeclaView.PAGE_SYM;
        } else if (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
            mode = TeclaView.MODE_EMAIL;
        } else if (var == InputType.TYPE_TEXT_VARIATION_URI) {
            mode = TeclaView.MODE_URI;
        }
        imeAction = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        actionLabel = null;
        if ((ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            if (ei.actionLabel != null) actionLabel = ei.actionLabel.toString();
            else switch (imeAction) {
                case EditorInfo.IME_ACTION_GO: actionLabel = "Ir"; break;
                case EditorInfo.IME_ACTION_SEARCH: actionLabel = "Buscar"; break;
                case EditorInfo.IME_ACTION_SEND: actionLabel = "Enviar"; break;
                case EditorInfo.IME_ACTION_NEXT: actionLabel = "Sig."; break;
                case EditorInfo.IME_ACTION_DONE: actionLabel = "Listo"; break;
                default: break;
            }
        }
        shift = TeclaView.SHIFT_OFF;
        autoCap();
        view.setState(page, mode, shift, actionLabel);
        refreshSuggestions();
    }

    @Override
    public void onFinishInput() {
        dict.save();
        super.onFinishInput();
    }

    @Override
    public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce);
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 30);
    }

    // ---------- teclas físicas / control remoto ----------
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (isInputViewShown() && view != null && handle(keyCode, ev)) {
            consumed.add(keyCode);
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev) {
        if (consumed.remove(keyCode)) return true;
        return super.onKeyUp(keyCode, ev);
    }

    private boolean handle(int keyCode, KeyEvent ev) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                return view.navigate(keyCode);
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_BUTTON_A: {
                press(view.selectedKey(), ev.getRepeatCount());
                return true;
            }
            case KeyEvent.KEYCODE_PROG_RED:
                press(new Key("", Key.DEL, 1), ev.getRepeatCount()); return true;
            case KeyEvent.KEYCODE_PROG_GREEN:
                if (ev.getRepeatCount() == 0) press(new Key("", Key.SPACE, 1), 0);
                return true;
            case KeyEvent.KEYCODE_PROG_YELLOW:
                if (ev.getRepeatCount() == 0) press(new Key("", Key.SHIFT, 1), 0);
                return true;
            case KeyEvent.KEYCODE_PROG_BLUE:
                if (ev.getRepeatCount() == 0) {
                    page = page == TeclaView.PAGE_ABC ? TeclaView.PAGE_SYM : TeclaView.PAGE_ABC;
                    view.setState(page, mode, shift, actionLabel);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                cursor(KeyEvent.KEYCODE_DPAD_LEFT); return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                cursor(KeyEvent.KEYCODE_DPAD_RIGHT); return true;
            default:
                return false;
        }
    }

    private void cursor(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    // ---------- acciones ----------
    private void click() {
        if (prefs.getBoolean("sound", true)) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f);
    }

    private void press(Key k, int repeat) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (k.type == Key.DEL) {
            click();
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null && sel.length() > 0) ic.commitText("", 1);
            else if (repeat > 6) deleteWord(ic);
            else ic.deleteSurroundingText(1, 0);
            return;
        }
        if (repeat > 0) {
            // mantener pulsado: una letra pasa a su variante acentuada (una sola vez)
            if (repeat == 1 && k.type == Key.CHAR) accentLast(ic, k.label);
            return;
        }
        click();
        switch (k.type) {
            case Key.CHAR: {
                String s = (shift != TeclaView.SHIFT_OFF && page != TeclaView.PAGE_SYM) ? k.label.toUpperCase() : k.label;
                boolean punct = !Character.isLetterOrDigit(s.charAt(0)) && page != TeclaView.PAGE_ACC;
                if (punct) learnLast(ic);
                ic.commitText(s, 1);
                if (shift == TeclaView.SHIFT_ONCE) shift = TeclaView.SHIFT_OFF;
                if (punct) autoCap();
                break;
            }
            case Key.TEXT:
                ic.commitText(k.label, 1);
                break;
            case Key.SPACE: {
                CharSequence b = ic.getTextBeforeCursor(2, 0);
                if (!noSuggest && b != null && b.length() == 2 && b.charAt(1) == ' ' && Character.isLetterOrDigit(b.charAt(0))) {
                    ic.deleteSurroundingText(1, 0);
                    ic.commitText(". ", 1);
                } else {
                    learnLast(ic);
                    ic.commitText(" ", 1);
                }
                if (shift == TeclaView.SHIFT_ONCE) shift = TeclaView.SHIFT_OFF;
                autoCap();
                break;
            }
            case Key.ENTER:
                learnLast(ic);
                doEnter(ic);
                autoCap();
                break;
            case Key.SHIFT:
                shift = shift == TeclaView.SHIFT_OFF ? TeclaView.SHIFT_ONCE
                        : shift == TeclaView.SHIFT_ONCE ? TeclaView.SHIFT_LOCK : TeclaView.SHIFT_OFF;
                break;
            case Key.SYM: page = TeclaView.PAGE_SYM; break;
            case Key.ACC: page = TeclaView.PAGE_ACC; break;
            case Key.ABC: page = TeclaView.PAGE_ABC; break;
            case Key.SUG:
                if (!k.label.isEmpty()) applySuggestion(ic, k.label);
                break;
            default: break;
        }
        view.setState(page, mode, shift, actionLabel);
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 10);
    }

    private void doEnter(InputConnection ic) {
        if (info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(imeAction);
        } else if (multiline || info == null || (info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        } else {
            ic.performEditorAction(EditorInfo.IME_ACTION_DONE);
        }
    }

    private void deleteWord(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(40, 0);
        if (b == null || b.length() == 0) return;
        int i = b.length();
        while (i > 0 && Character.isWhitespace(b.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(b.charAt(i - 1))) i--;
        ic.deleteSurroundingText(Math.max(1, b.length() - i), 0);
    }

    private void accentLast(InputConnection ic, String base) {
        String lower = base.toLowerCase(), v;
        switch (lower) {
            case "a": v = "á"; break; case "e": v = "é"; break; case "i": v = "í"; break;
            case "o": v = "ó"; break; case "u": v = "ú"; break; case "n": v = "ñ"; break;
            case "c": v = "ç"; break; default: return;
        }
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        if (b == null || b.length() != 1) return;
        boolean up = Character.isUpperCase(b.charAt(0));
        ic.deleteSurroundingText(1, 0);
        ic.commitText(up ? v.toUpperCase() : v, 1);
    }

    private String currentWord(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(32, 0);
        if (b == null) return "";
        int i = b.length();
        while (i > 0 && Character.isLetter(b.charAt(i - 1))) i--;
        return b.subSequence(i, b.length()).toString();
    }

    private void learnLast(InputConnection ic) {
        if (password) return;
        String w = currentWord(ic);
        if (w.length() >= 2) dict.learn(w);
    }

    private void applySuggestion(InputConnection ic, String word) {
        String cur = currentWord(ic);
        String out = word;
        if (!cur.isEmpty()) {
            if (cur.length() > 1 && cur.equals(cur.toUpperCase())) out = word.toUpperCase();
            else if (Character.isUpperCase(cur.charAt(0))) out = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        ic.beginBatchEdit();
        if (!cur.isEmpty()) ic.deleteSurroundingText(cur.length(), 0);
        ic.commitText(out + " ", 1);
        ic.endBatchEdit();
        dict.learn(word);
        if (shift == TeclaView.SHIFT_ONCE) shift = TeclaView.SHIFT_OFF;
    }

    private void autoCap() {
        if (shift == TeclaView.SHIFT_LOCK || info == null || !prefs.getBoolean("autocap", true)) return;
        if (page != TeclaView.PAGE_ABC || password) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int caps = ic.getCursorCapsMode(info.inputType);
        shift = caps != 0 ? TeclaView.SHIFT_ONCE : TeclaView.SHIFT_OFF;
    }

    private void refreshSuggestions() {
        if (view == null) return;
        List<String> s = java.util.Collections.emptyList();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && !noSuggest && prefs.getBoolean("suggest", true)) {
            String cur = currentWord(ic);
            s = dict.suggest(cur);
        }
        view.setSuggestions(s);
    }
}
