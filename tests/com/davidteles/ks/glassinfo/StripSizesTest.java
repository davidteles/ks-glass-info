package com.davidteles.ks.glassinfo;

/** Run with java -ea: the Columns layout weather card fits after rotating to portrait. */
public final class StripSizesTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        // "15°C" at 54 sp is about 100 dp wide.
        float temp = 100f, pad = 22f;

        // Landscape (wide column, capped at 360 dp): unchanged — 64 dp icon, 54 sp text.
        float[] wide = StripSizes.weather(360f, pad, temp);
        check(wide[0] == 64f && wide[1] == 16f && wide[2] == 54f, "landscape keeps the icon and 54 sp");

        // Portrait on the tablet (1080 px wide, column ~112 dp): before the fix, icon + 54 sp text
        // needed 22 + 64 + 16 + 100 + 22 = 224 dp, so the temperature ran off the screen.
        float colW = 112f;
        float[] narrow = StripSizes.weather(colW, pad, temp);
        float used = pad + narrow[0] + narrow[1] + temp * narrow[2] / 54f + pad;
        check(used <= colW + 0.01f, "portrait: icon + temperature fit inside the card (" + used + " > " + colW + ")");
        check(narrow[0] == 0f, "portrait drops the icon to make room");
        check(narrow[2] >= 30f, "portrait temperature stays readable (" + narrow[2] + " sp)");

        // A very long value never overflows (it shrinks to 12 sp, then the drawing ellipsizes).
        float[] tiny = StripSizes.weather(60f, pad, 400f);
        check(tiny[2] >= 12f, "never below 12 sp");
        System.out.println("StripSizesTest OK");
    }
}
