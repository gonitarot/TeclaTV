package app.teclatv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private TextView status;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        float u = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) / 1080f;
        int p = (int) (40 * u);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p * 2, p, p * 2, p);
        root.setBackgroundColor(0xFF0C0F1D);

        TextView title = text("TeclaTV", 46 * u, 0xFF22D3EE);
        root.addView(title);
        root.addView(text("Teclado rápido para Android TV · control remoto (D-pad)", 24 * u, 0xFFB0B8D8));
        status = text("", 26 * u, 0xFFFBBF24);
        status.setPadding(0, p / 2, 0, p / 2);
        root.addView(status);

        root.addView(button("1 · Activar TeclaTV", u, v -> openImeSettings()));
        root.addView(button("2 · Elegir TeclaTV como teclado", u, v -> {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker();
        }));
        root.addView(check("Sonido al teclear", "sound", u));
        root.addView(check("Sugerencias de palabras", "suggest", u));
        root.addView(check("Mayúscula automática", "autocap", u));

        EditText test = new EditText(this);
        test.setHint("Prueba el teclado aquí…");
        test.setHintTextColor(0xFF7C84A8);
        test.setTextColor(0xFFE8EAF6);
        test.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28 * u);
        test.setInputType(InputType.TYPE_CLASS_TEXT);
        test.setSingleLine(true);
        root.addView(test);

        root.addView(text("Mando: OK escribe · mantener OK en a/e/i/o/u/n añade acento · mantener OK en ⌫ borra palabras · "
                + "Rojo = borrar · Verde = espacio · Amarillo = mayúsculas · Azul = 123 · Rebobinar/Avanzar = mover cursor · Atrás = cerrar", 20 * u, 0xFF7C84A8));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList()) if (i.getPackageName().equals(getPackageName())) enabled = true;
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = cur != null && cur.startsWith(getPackageName() + "/");
        status.setText(selected ? "✔ TeclaTV está activo y seleccionado"
                : enabled ? "● Activado — falta el paso 2 (elegirlo)" : "○ Falta el paso 1 (activarlo)");
        status.setTextColor(selected ? 0xFF34D399 : 0xFFFBBF24);
    }

    private void openImeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
        }
    }

    private TextView text(String s, float px, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private Button button(String s, float u, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28 * u);
        b.setTextColor(Color.WHITE);
        b.setOnClickListener(l);
        b.setFocusable(true);
        b.setBackground(focusBg(u));
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (84 * u));
        lp.topMargin = (int) (10 * u);
        b.setLayoutParams(lp);
        return b;
    }

    private CheckBox check(String s, String key, float u) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(Color.WHITE);
        c.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28 * u);
        c.setChecked(prefs.getBoolean(key, true));
        c.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
        c.setBackground(focusBg(u));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (76 * u));
        lp.topMargin = (int) (10 * u);
        c.setLayoutParams(lp);
        return c;
    }

    private android.graphics.drawable.StateListDrawable focusBg(float u) {
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        GradientDrawable f = new GradientDrawable();
        f.setColor(0xFF1F3A55); f.setCornerRadius(14 * u); f.setStroke((int) (4 * u), 0xFF22D3EE);
        GradientDrawable n = new GradientDrawable();
        n.setColor(0xFF1F2540); n.setCornerRadius(14 * u);
        sl.addState(new int[]{android.R.attr.state_focused}, f);
        sl.addState(new int[]{}, n);
        return sl;
    }
}
