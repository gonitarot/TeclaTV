package app.teclatv;

import android.graphics.RectF;

final class Key {
    static final int CHAR = 0, SHIFT = 1, DEL = 2, SPACE = 3, ENTER = 4, SYM = 5, ACC = 6, ABC = 7, TEXT = 8, SUG = 9;
    final String label;
    final int type;
    final float w;
    final RectF r = new RectF();

    Key(String label, int type, float w) {
        this.label = label;
        this.type = type;
        this.w = w;
    }
}
