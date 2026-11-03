package nl.jays.boodschappenbenelux;

/** Visual tokens from the approved theme 3 and theme 4 reference images. */
final class AppPalette {
    static final AppPalette LIGHT = new AppPalette(
        0xFFFBF9F3, 0xFFFFFDF8, 0xFF1E3023, 0xFF59634F,
        0xFF3E6B3F, 0xFFE6EEDC, 0xFFD6DEC9, 0xFF3E6B3F, 0xFF8FAF8A);
    static final AppPalette DARK = new AppPalette(
        0xFF0F1420, 0xFF1B2333, 0xFFF3F4F6, 0xFFD9C9B2,
        0xFF91D6B1, 0xFF253D38, 0xFF354354, 0xFF3E7C6A, 0xFF3E7C6A);

    final int canvas, card, ink, muted, accent, soft, border, button, track;
    private AppPalette(int canvas, int card, int ink, int muted, int accent,
                       int soft, int border, int button, int track) {
        this.canvas=canvas; this.card=card; this.ink=ink; this.muted=muted;
        this.accent=accent; this.soft=soft; this.border=border;
        this.button=button; this.track=track;
    }
    static AppPalette forDarkMode(boolean dark) { return dark ? DARK : LIGHT; }
}
