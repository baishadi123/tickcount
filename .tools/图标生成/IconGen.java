import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the TickCount launcher foreground out of a finished artwork
 * (icon1.png): a calendar picture on ivory paper, delivered as a square with
 * a black surround.
 *
 * The two details that matter are carried over from the text version: the art
 * is sized to fit the 66dp safe circle inside the 108dp adaptive-icon canvas,
 * which is what keeps it inside every mask shape, including a full circle;
 * and the paper colour is sampled and printed so it can go into colors.xml as
 * the icon's background — the paper's own vignette stays within a few RGB
 * steps of it, so the seam between layer and background is invisible.
 */
public class IconGen {

    private static final String SOURCE = "icon1.png";         // the artwork to adopt
    private static final int SIZE = 432;                      // 108dp at xxxhdpi
    private static final int SAFE_DIAGONAL = 66 * SIZE / 108; // every mask keeps this circle
    private static final int ART_INK = 40;                    // colour distance that counts as art
    private static final int EDGE_PAD = 100;                  // stays clear of the artwork's rounded rim

    /** The sampled paper colour, used by the preview's backdrop. */
    private static int background;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File outFile = new File(args[0]);
        outFile.getParentFile().mkdirs();
        File previewFile = args.length > 1 ? new File(args[1]) : null;

        BufferedImage icon = renderIcon();
        ImageIO.write(icon, "png", outFile);
        System.out.println("wrote " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");

        if (previewFile != null) {
            if (previewFile.getParentFile() != null) previewFile.getParentFile().mkdirs();
            ImageIO.write(renderPreview(icon), "png", previewFile);
            System.out.println("wrote " + previewFile.getAbsolutePath());
        }
    }

    private static BufferedImage renderIcon() throws Exception {
        BufferedImage art = ImageIO.read(new File(SOURCE));
        BufferedImage src = new BufferedImage(art.getWidth(), art.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D prep = src.createGraphics();
        prep.drawImage(art, 0, 0, null);
        prep.dispose();
        System.out.println("size=" + src.getWidth() + "x" + src.getHeight());

        Rectangle paper = paperRect(src);
        System.out.println("paper=" + paper.width + "x" + paper.height
                + " at (" + paper.x + "," + paper.y + ")");
        BufferedImage sheet = src.getSubimage(paper.x, paper.y, paper.width, paper.height);

        // The backdrop the layer sits on is sampled at the four edge midpoints
        // of the paper window, and averaged: those are the pixels the seam has
        // to match, and a single point picks up too much of the vignette.
        int w = sheet.getWidth(), h = sheet.getHeight();
        int r = 0, gch = 0, b = 0;
        int[][] probes = {{w / 2, 4}, {w / 2, h - 5}, {4, h / 2}, {w - 5, h / 2}};
        for (int[] p : probes) {
            int rgb = sheet.getRGB(p[0], p[1]);
            r += (rgb >> 16) & 0xFF;
            gch += (rgb >> 8) & 0xFF;
            b += rgb & 0xFF;
        }
        background = (r / 4 << 16) | (gch / 4 << 8) | b / 4;
        System.out.println("background=#" + String.format("%06X", background));

        Rectangle box = inkBounds(sheet, background);
        double diagonal = Math.hypot(box.width, box.height);
        double scale = Math.min(1.0, (double) SAFE_DIAGONAL / diagonal);
        System.out.println("art=" + box.width + "x" + box.height + " at (" + box.x + "," + box.y + ")");
        System.out.println("diagonal=" + Math.round(diagonal) + "px scale=" + String.format("%.4f", scale));

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        applyQuality(g);

        // Centring uses the art's own box, not the picture frame, so the
        // calendar sits on the canvas centre the way the label used to.
        double x = SIZE / 2.0 - (box.x + box.width / 2.0) * scale;
        double y = SIZE / 2.0 - (box.y + box.height / 2.0) * scale;
        g.drawImage(sheet,
                (int) Math.round(x), (int) Math.round(y),
                (int) Math.round(x + sheet.getWidth() * scale),
                (int) Math.round(y + sheet.getHeight() * scale),
                0, 0, sheet.getWidth(), sheet.getHeight(), null);
        g.dispose();
        return image;
    }

    /** The artwork's paper: where the two middle lines leave the black surround, padded. */
    private static Rectangle paperRect(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int left = 0, right = w - 1, top = 0, bottom = h - 1;
        while (left < w && !light(src.getRGB(left, h / 2))) left++;
        while (right >= 0 && !light(src.getRGB(right, h / 2))) right--;
        while (top < h && !light(src.getRGB(w / 2, top))) top++;
        while (bottom >= 0 && !light(src.getRGB(w / 2, bottom))) bottom--;
        if (right <= left || bottom <= top) throw new IllegalStateException("paper not found");
        return new Rectangle(left + EDGE_PAD, top + EDGE_PAD,
                right - left - 2 * EDGE_PAD, bottom - top - 2 * EDGE_PAD);
    }

    private static boolean light(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) > 600;
    }

    /** The calendar's bounding box: every pixel well off the paper colour. */
    private static Rectangle inkBounds(BufferedImage sheet, int bg) {
        int minX = sheet.getWidth(), minY = sheet.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                if (distance(sheet.getRGB(x, y) & 0xFFFFFF, bg) <= ART_INK) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) throw new IllegalStateException("no art found");
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static int distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.round(Math.sqrt((double) dr * dr + (double) dg * dg + (double) db * db));
    }

    private static void applyQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    /**
     * Builds a contact sheet showing the icon under the two mask shapes Android
     * actually uses, plus an unmasked copy, at a realistic launcher size.
     */
    private static BufferedImage renderPreview(BufferedImage icon) {
        int cell = 200;
        int label = 26;
        int gap = 24;
        int width = gap + 3 * (cell + gap);
        int height = gap + cell + label + gap;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        applyQuality(g);
        g.setColor(new Color(0xECECEC));
        g.fillRect(0, 0, width, height);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

        String[] captions = {"squircle mask", "circle mask", "unmasked"};
        for (int i = 0; i < 3; i++) {
            int x = gap + i * (cell + gap);
            int y = gap;

            BufferedImage masked = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_RGB);
            Graphics2D mg = masked.createGraphics();
            applyQuality(mg);
            mg.setColor(new Color(background));
            mg.fillRect(0, 0, cell, cell);

            Shape clip = switch (i) {
                case 0 -> new RoundRectangle2D.Float(0, 0, cell, cell, cell * 0.42f, cell * 0.42f);
                case 1 -> new Ellipse2D.Float(0, 0, cell, cell);
                default -> new Rectangle2D.Float(0, 0, cell, cell);
            };
            mg.setClip(clip);
            mg.drawImage(icon, 0, 0, cell, cell, null);
            mg.setClip(null);
            mg.setColor(new Color(0x33000000, true));
            mg.setStroke(new BasicStroke(1f));
            mg.draw(clip);
            mg.dispose();

            g.drawImage(masked, x, y, null);
            g.setColor(new Color(0x333333));
            g.drawString(captions[i], x, y + cell + 18);
        }
        g.dispose();
        return out;
    }
}
