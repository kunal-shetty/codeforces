import com.kun.cfdaily.data.Template;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs the template generator on saved Codeforces problem pages.
 *
 *   java Run <pagesDir> [problem]       score every page, print the code for one problem
 *   java Run <pagesDir> --dump <outDir> also write every generated template as .cpp
 */
public class Run {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: java Run <pagesDir> [problem | --dump <outDir>]");
            System.exit(2);
        }
        Path dir = Paths.get(args[0]);
        String show = args.length > 1 && !args[1].equals("--dump") ? args[1] : null;
        Path dump = args.length > 2 && args[1].equals("--dump") ? Paths.get(args[2]) : null;
        if (dump != null) Files.createDirectories(dump);

        List<Path> pages = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.html")) {
            for (Path p : ds) pages.add(p);
        }
        Collections.sort(pages);
        int ok = 0;
        for (Path p : pages) {
            String id = p.getFileName().toString().replace(".html", "");
            Template.Result r = Template.fromHtml(new String(Files.readAllBytes(p), StandardCharsets.UTF_8), new String[0]);
            if (r.checked) ok++;
            System.out.println((r.checked ? "OK   " : "--   ") + id + "   " + r.note);
            if (id.equals(show)) System.out.println("\n" + r.code);
            if (dump != null) Files.write(dump.resolve(id + ".cpp"), r.code.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("\n" + ok + " / " + pages.size() + " read their samples exactly");
    }
}
