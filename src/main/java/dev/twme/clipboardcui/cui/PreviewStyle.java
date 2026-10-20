package dev.twme.clipboardcui.cui;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Colours and grid spacing sent with each preview.
 *
 * <p>Colours use the CUI {@code #RRGGBBAA} format. Index meaning follows WorldEditCUI and
 * WorldEditDisplay: for cuboids {@code edge, grid, point1, point2}; WorldEditDisplay also uses
 * the grid colour (at 25% alpha) for fill faces. A fully transparent colour hides that part.</p>
 *
 * @param gridSpacing grid spacing in blocks; values below 1 hide the grid
 */
public record PreviewStyle(String edge, String grid, String point1, String point2, double gridSpacing) {

    public static final String TRANSPARENT = "#00000000";
    private static final Pattern COLOUR = Pattern.compile("#[0-9A-F]{8}");

    public static final PreviewStyle DEFAULT =
            new PreviewStyle("#55FFFFFF", "#FFFFFF80", TRANSPARENT, TRANSPARENT, 0);

    public PreviewStyle {
        edge = normalise(edge);
        grid = normalise(grid);
        point1 = normalise(point1);
        point2 = normalise(point2);
        if (!Double.isFinite(gridSpacing)) gridSpacing = 0;
    }

    /**
     * Accepts {@code RRGGBB}, {@code RRGGBBAA}, with or without {@code #}.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static String normalise(String colour) {
        if (colour == null) throw new IllegalArgumentException("Colour is missing");
        String c = colour.trim().toUpperCase(Locale.ROOT);
        if (!c.startsWith("#")) c = "#" + c;
        if (c.length() == 7) c = c + "FF";
        if (!COLOUR.matcher(c).matches()) {
            throw new IllegalArgumentException("Invalid colour '" + colour + "', expected #RRGGBB or #RRGGBBAA");
        }
        return c;
    }
}
