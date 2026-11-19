import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** 从用户本地 EPUB 提取 XHTML 文本，保留文件名用于定位。 */
class ExtractEpub {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("用法: java scripts/ExtractEpub.java 输入目录 输出目录");
        Path root = Path.of(args[0]), output = Path.of(args[1]);
        try (var files = Files.walk(root)) {
            for (Path epub : files.filter(p -> p.toString().toLowerCase().endsWith(".epub")).toList()) {
                String title = epub.getFileName().toString().replaceFirst("\\.epub$", "").replaceFirst(" \\(z-library.*", "");
                Path dest = output.resolve("epub-" + title).resolve("章节");
                Files.createDirectories(dest);
                int count = 0;
                try (ZipFile zip = new ZipFile(epub.toFile())) {
                    var entries = Collections.list(zip.entries());
                    for (var entry : entries) {
                        if (entry.isDirectory() || !entry.getName().matches("(?i).+\\.(xhtml|html|htm)$")) continue;
                        StringBuilder text = new StringBuilder();
                        try (Reader in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                            new ParserDelegator().parse(in, new HTMLEditorKit.ParserCallback() {
                                @Override public void handleText(char[] chars, int pos) { text.append(chars).append(' '); }
                            }, true);
                        }
                        if (text.length() < 60) continue;
                        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(entry.getName().getBytes(StandardCharsets.UTF_8));
                        Files.writeString(dest.resolve(encoded + ".txt"), text.toString());
                        count++;
                    }
                }
                System.out.println(title + "：" + count + " 个章节文件");
            }
        }
    }
}
