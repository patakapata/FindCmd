package me.patakapata.findcmd.client;

public class Color {
    private float red;
    private float green;
    private float blue;
    private float alpha;
    private int packed;

    /**
     * @param color 0xAARRGGBB
     */
    public static Color of(int color) {
        return new Color(color);
    }

    public static Color of(int alpha, int red, int green, int blue) {
        return new Color(alpha / 255F, red / 255F, green / 255F, blue / 255F);
    }

    public static Color of(float alpha, float red, float green, float blue) {
        return new Color(alpha, red, green, blue);
    }

    public static int pack(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static int pack(float alpha, float red, float green, float blue) {
        return pack((int) alpha * 255, (int) red * 255, (int) blue * 255, (int) green * 255);
    }

    public static float[] unpack(int color) {
        return new float[]{
                (color >> 24 & 0xFF) / 255F,
                (color >> 16 & 0xFF) / 255F,
                (color >> 8 & 0xFF) / 255F,
                (color & 0xFF) / 255F
        };
    }

    public Color(int color) {
        set(color);
    }

    public Color(int alpha, int red, int green, int blue) {
        set(alpha, red, green, blue);
    }

    public Color(float alpha, float red, float green, float blue) {
        set(alpha, red, green, blue);
    }

    private void updatePackedColor() {
        this.packed = pack(getIntAlpha(), getIntRed(), getIntGreen(), getIntBlue());
    }

    public int getPacked() {
        return packed;
    }

    public float getRed() {
        return red;
    }

    public int getIntRed() {
        return (int) (red * 255);
    }

    public float getGreen() {
        return green;
    }

    public int getIntGreen() {
        return (int) (green * 255);
    }

    public float getBlue() {
        return blue;
    }

    public int getIntBlue() {
        return (int) (blue * 255);
    }

    public float getAlpha() {
        return alpha;
    }

    public int getIntAlpha() {
        return (int) (alpha * 255);
    }

    public void setRed(float red) {
        this.red = red;
        updatePackedColor();
    }

    public void setGreen(float green) {
        this.green = green;
        updatePackedColor();
    }

    public void setBlue(float blue) {
        this.blue = blue;
        updatePackedColor();
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        updatePackedColor();
    }

    public void set(int color) {
        float[] unpacked = unpack(color);
        alpha = unpacked[0];
        red = unpacked[1];
        green = unpacked[2];
        blue = unpacked[3];
        this.packed = color;
    }

    public void set(int alpha, int red, int green, int blue) {
        this.alpha = alpha / 255F;
        this.red = red / 255F;
        this.green = green / 255F;
        this.blue = blue / 255F;
        updatePackedColor();
    }

    public void set(float alpha, float red, float green, float blue) {
        this.alpha = alpha;
        this.red = red;
        this.green = green;
        this.blue = blue;
        updatePackedColor();
    }
}
