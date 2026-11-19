package com.davidteles.ks.glassinfo;

/** Pure sizing maths for the Columns layout (no Android classes, so it can be tested off-device). */
final class StripSizes {
    private StripSizes() {}

    /** Below this column width (dp) the weather icon is dropped, so the temperature has room. */
    static final float NARROW_COLUMN_DP = 220f;

    /**
     * Weather card sizes for a side column, all in dp/sp.
     *
     * @param colW       column width (dp)
     * @param pad        inner padding (dp)
     * @param tempAt54   width of the temperature text at 54 sp (dp)
     * @return {iconSize, iconGap, temperatureSp}: wide columns keep the 64 dp icon and 54 sp text;
     *         narrow ones (portrait) drop the icon and shrink the text until it fits the card.
     */
    static float[] weather(float colW, float pad, float tempAt54) {
        boolean narrow = colW < NARROW_COLUMN_DP;
        float icon = narrow ? 0f : 64f, gap = narrow ? 0f : 16f;
        float avail = Math.max(1f, colW - pad * 2 - icon - gap);
        float size = tempAt54 > avail ? Math.max(12f, 54f * avail / tempAt54) : 54f;
        return new float[] {icon, gap, size};
    }
}
