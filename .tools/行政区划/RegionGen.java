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
 * Run it with `-Dfile.encoding=UTF-8` (JDK 18+ may omit it): on a GBK
 * Windows, javac decodes this UTF-8 source as GBK, and the Chinese in the
 * header line compiles into the class as mojibake — the first table's
 * header was garbled exactly so, while its data stayed clean.
 *
 * Only level-3 rows (区县) are kept — the visit's own name is typed by hand,
 * the row under it only has to say which city and district it sat in. Output,
 * one entry per line, `lat|lng|merger_name` where merger_name reads
 * `浙江,杭州,西湖` (省,市,区; 直辖市 two parts):
 *
 *   30.27410|120.15510|浙江,杭州,西湖
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
        // The 2023 dump is UTF-8. Read it strictly, so a damaged byte aborts
        // the run instead of quietly eating a character (the first table was
        // built believing the dump GBK, and 742 district names lost a char
        // to a wrong transcode). A genuinely GBK dump still works: it fails
        // the strict read and falls through to the byte-preserving path.
        List<String> sqlLines;
        try (BufferedReader strict = Files.newBufferedReader(sql, StandardCharsets.UTF_8)) {
            sqlLines = strict.lines().toList();
        } catch (java.nio.charset.MalformedInputException e) {
            System.err.println("dump is not UTF-8; reading as GBK");
            sqlLines = new ArrayList<>();
            try (BufferedReader gbk = Files.newBufferedReader(sql, Charset.forName("GBK"))) {
                sqlLines = gbk.lines().toList();
            }
        }
        for (String line : sqlLines) {
            if (!line.startsWith("INSERT INTO")) continue;
            Matcher matcher = ROW.matcher(line);
            if (!matcher.find()) {
                failed++;
                continue;
            }
            int level = Integer.parseInt(matcher.group(2));
            if (level != 3) continue;
            double lng = Double.parseDouble(matcher.group(7));
            double lat = Double.parseDouble(matcher.group(8));
            // merger_name reads 省,市,区 — Mainland districts at most three
            // parts; Taiwan's rows go four deep, deeper than a visit's card
            // ever needs, so they go.
            String path = matcher.group(5).replace("\\'", "'");
            if (path.isBlank() || path.split(",").length > 3) continue;
            if (!Double.isFinite(lng) || !Double.isFinite(lat)) continue;
            if (!seen.add(path)) continue;
            lines.add(String.format(Locale.ROOT, "%.5f|%.5f|%s", lat, lng, path));
            parsed++;
        }

        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.write(out, lines, StandardCharsets.UTF_8);
        System.out.println("districts: " + parsed + " (rows unparsed: " + failed + ")");
        System.out.println("written: " + out);
    }
}
