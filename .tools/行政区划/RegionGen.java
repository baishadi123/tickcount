import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the offline place-name table for the footprint editor from the
 * kakuilan/china_area_mysql dump (中国民政行政区划，含每级中心经纬度).
 *
 * Usage: java RegionGen.java <cnarea_2023.sql> <output.md>
 *
 * Only level-3 rows (区县) are kept — the visit's own name is typed by hand,
 * the row under it only has to say which city and district it sat in. Output,
 * one entry per line, `lat|lng|merger_name` where merger_name reads `杭州,西湖`:
 *
 *   30.27410|120.15510|杭州,西湖
 *
 * A leading `#` line documents the source; the parser skips it.
 */
public class RegionGen {

    private static final Pattern ROW = Pattern.compile(
        "VALUES \\((\\d+), (\\d+), \\d+, \\d+, (?:'[^']*'|\\d+), '[^']*', '((?:[^'\\\\]|\\\\.)*)', " +
            "'((?:[^'\\\\]|\\\\.)*)', '((?:[^'\\\\]|\\\\.)*)', '((?:[^'\\\\]|\\\\.)*)', " +
            "(-?[\\d.]+), (-?[\\d.]+)\\);"
    );

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: java RegionGen.java <cnarea_2023.sql> <output.md>");
            System.exit(2);
        }
        Path sql = Paths.get(args[0]);
        Path out = Paths.get(args[1]);

        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        lines.add("# 中国省市区划（来源 kakuilan/china_area_mysql，基于民政部公开区划；由 .tools/行政区划/RegionGen.java 生成）");
        int parsed = 0;
        int failed = 0;
        // The dump is GBK with a few bytes that are not even valid GBK: read it
        // byte-preserving (Latin-1) and re-decode each line as GBK, so a stray
        // byte costs one character, not the whole run.
        try (BufferedReader reader = Files.newBufferedReader(sql, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("INSERT INTO")) continue;
                line = new String(line.getBytes(StandardCharsets.ISO_8859_1), Charset.forName("GBK"));
                Matcher matcher = ROW.matcher(line);
                if (!matcher.find()) {
                    failed++;
                    continue;
                }
                int level = Integer.parseInt(matcher.group(2));
                if (level != 3) continue;
                double lng = Double.parseDouble(matcher.group(7));
                double lat = Double.parseDouble(matcher.group(8));
                String path = matcher.group(8) == null ? "" : matcher.group(8).replace("\\'", "'");
                path = matcher.group(5).replace("\\'", "'");
                // Mainland districts read 省,市,区 (直辖市 two parts); the same
                // level carries Taiwan's village rows four parts deep — deeper
                // than a visit's card ever needs, so they go.
                if (path.isBlank() || path.split(",").length > 3) continue;
                if (!Double.isFinite(lng) || !Double.isFinite(lat)) continue;
                if (!seen.add(path)) continue;
                lines.add(String.format(Locale.ROOT, "%.5f|%.5f|%s", lat, lng, path));
                parsed++;
            }
        }

        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.write(out, lines, StandardCharsets.UTF_8);
        System.out.println("districts: " + parsed + " (rows unparsed: " + failed + ")");
        System.out.println("written: " + out);
    }
}
