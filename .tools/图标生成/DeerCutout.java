import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * One-off: the deer mark ships with a near-white canvas; the launch page wants
 * the deer alone, so every pixel past the threshold goes transparent. The edge
 * pixels stay — they blend toward the paper anyway, which is near-white itself.
 */
public class DeerCutout {
    public static void main(String[] args) throws Exception {
        var src = ImageIO.read(new File(args[0]));
        int w = src.getWidth(), h = src.getHeight();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (r >= 246 && g >= 246 && b >= 244) {
                    out.setRGB(x, y, 0);
                } else {
                    out.setRGB(x, y, rgb | 0xFF000000);
                }
            }
        }
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println("written " + args[1]);
    }
}
