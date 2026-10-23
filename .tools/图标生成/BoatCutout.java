import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * One-off: the boat mark ships with a near-white canvas; the voyage page wants
 * the boat alone, so the paper fades out through a soft threshold — unlike
 * DeerCutout's hard cut, the band between the two thresholds keeps partial
 * alpha, which is what keeps the line art's anti-aliased edges smooth over the
 * river art. The output is also trimmed to the mark's own bounding box plus a
 * small margin, so the sprite carries no square of empty pixels with it.
 */
public class BoatCutout {
    private static final int OPAQUE_BELOW = 205;
    private static final int GONE_ABOVE = 225;
    private static final int MARGIN = 8;

    public static void main(String[] args) throws Exception {
        var src = ImageIO.read(new File(args[0]));
        int w = src.getWidth(), h = src.getHeight();
        var cut = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int lum = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                int alpha;
                if (lum <= OPAQUE_BELOW) alpha = 255;
                else if (lum >= GONE_ABOVE) alpha = 0;
                else alpha = 255 * (GONE_ABOVE - lum) / (GONE_ABOVE - OPAQUE_BELOW);
                cut.setRGB(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
                if (alpha > 24) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        int x0 = Math.max(0, minX - MARGIN);
        int y0 = Math.max(0, minY - MARGIN);
        int x1 = Math.min(w - 1, maxX + MARGIN);
        int y1 = Math.min(h - 1, maxY + MARGIN);
        var out = cut.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);

        ImageIO.write(out, "png", new File(args[1]));
        System.out.println("written " + args[1] + " (" + out.getWidth() + "x" + out.getHeight() + ")");
    }
}
