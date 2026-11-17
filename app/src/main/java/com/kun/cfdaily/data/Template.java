package com.kun.cfdaily.data;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a C++ starter for a Codeforces problem: the input is read for you and only the logic is left.
 * The Input section of the statement is read sentence by sentence, then the reading plan is replayed
 * on the sample input; if every token lines up, the template is marked as checked.
 */
public class Template {

    public static class Result {
        public String code;
        /** True when the reading code consumes the sample input exactly. */
        public boolean checked;
        public String note;
    }

    // ------------------------------------------------------------------ fetching

    private static final Map<String, String> PAGES = new HashMap<>();

    /** The problem page, kept in memory for the session. Null when it can't be loaded. */
    public static String page(int contestId, String index) {
        String key = contestId + index;
        synchronized (PAGES) {
            if (PAGES.containsKey(key)) return PAGES.get(key);
        }
        String[] hosts = {"https://codeforces.com", "https://mirror.codeforces.com"};
        for (String host : hosts) {
            try {
                String html = fetch(host + "/contest/" + contestId + "/problem/" + index);
                if (html.contains("input-specification")) {
                    synchronized (PAGES) {
                        PAGES.put(key, html);
                    }
                    return html;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** The statement as plain text (math left as LaTeX), for the AI helpers. Null when it can't be loaded. */
    public static String statement(int contestId, String index) {
        String html = page(contestId, index);
        if (html == null) return null;
        return statementFromHtml(html);
    }

    public static String statementFromHtml(String html) {
        int i = html.indexOf("<div class=\"problem-statement\">");
        if (i < 0) return null;
        int j = html.indexOf("<script", i);
        String part = j < 0 ? html.substring(i) : html.substring(i, j);
        part = part.replaceAll("<div class=\"section-title\">", "\n\n## ")
                .replaceAll("(?i)</p>|</li>|<pre[^>]*>|</pre>", "\n");
        String text = strip(part).replace("$$$", "$").replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n(\\s*\\n)+", "\n\n").trim();
        return text.length() > 12000 ? text.substring(0, 12000) : text;
    }

    /** header: the user's own top part (includes, macros), or null for the default one. */
    public static Result build(int contestId, String index, String[] tags, String header) {
        String html = page(contestId, index);
        if (html == null) {
            Result r = fromHtml("", tags, header);
            r.note = "Couldn't load the statement. Basic template.";
            return r;
        }
        return fromHtml(html, tags, header);
    }

    private static String fetch(String spec) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(spec).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        try {
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            InputStreamReader r = new InputStreamReader(in, "UTF-8");
            char[] buf = new char[16384];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // ------------------------------------------------------------------ plan

    private static final int SCALARS = 0, ARRAY = 1, ROWS = 2, GRID = 3, MATRIX = 4, EDGES = 5, QUERIES = 6;
    private static final String INT = "int", LL = "ll", STR = "string", CHR = "char", DBL = "double";

    private static class Op {
        int kind;
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        String len;
        String len2;
        boolean typed;
    }

    private static class Item {
        String name;
        String type;
        String len; // set for arrays
        String len2; // set for rows of a matrix
    }

    private static final Pattern MATH = Pattern.compile("\\u0001(\\d+)\\u0002");
    private static final Pattern ARR = Pattern.compile("^([A-Za-z]+)_\\{?1\\}?\\s*,?\\s*.*?(?:\\\\[lcv]?dots|\\.\\.\\.)\\s*,?\\s*\\1_\\{?([^{}]+?)\\}?$");
    /** Same, with a braced last index that has braces inside: b_{\frac{n(n-1)}{2}}. */
    private static final Pattern ARR_BRACED = Pattern.compile("^([A-Za-z]+)_\\{?1\\}?\\s*,?\\s*.*?(?:\\\\[lcv]?dots|\\.\\.\\.)\\s*,?\\s*\\1_\\{(.+)\\}$");
    private static final Pattern ARR2 = Pattern.compile("^([A-Za-z]+)_\\{\\s*i\\s*,\\s*1\\s*\\}.*\\1_\\{\\s*i\\s*,\\s*([^{}]+?)\\s*\\}$");
    private static final Pattern IDENT = Pattern.compile("^([A-Za-z]{1,4})(?:_\\{?([A-Za-z0-9]{1,3})\\}?)?'?$");
    private static final Pattern EXP = Pattern.compile("10\\^\\{?\\s*(\\d+)\\s*\\}?");
    private static final String[] COUNT_WORDS = {"integer", "number", "space", "non-negative", "nonnegative", "positive", "distinct", "pairwise",
            "character", "letter", "element", "string", "line", "word", "symbol", "digit", "value", "quer", "edge", "real", "point", "pair", "row", "column", "lowercase", "uppercase", "binary", "more", "test"};

    private static String strip(String html) {
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("</div>", "\n").replaceAll("<[^>]+>", " ");
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").replace("&mdash;", "—").replace("&amp;", "&").replace(' ', ' ');
        return s;
    }

    private static String clean(String math) {
        String m = math.replaceAll("\\\\(?:mathit|mathrm|mathbf|texttt|text|textbf|operatorname|mathtt)\\s*\\{([^{}]*)\\}", "$1");
        return m.replaceAll("[\\u00a0\\u2000-\\u200b\\u202f]", " ").replace("\\,", "").replace("\\!", "").replace("~", " ").trim();
    }

    private static boolean relational(String c) {
        return c.matches(".*(\\\\le|\\\\ge|\\\\lt|\\\\gt|\\\\ne|\\\\in|<|>|=|\\\\neq|\\\\leq|\\\\geq|\\\\cdot\\s*10|\\\\pmod|\\\\mod).*");
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    /** Old statements mark math with tex-span, italics and sub/sup tags; rewrite them as $$$...$$$ like new ones. */
    private static String oldMath(String html) {
        Matcher m = Pattern.compile("<span class=\"tex-span\">(.*?)</span>", Pattern.DOTALL).matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String in = m.group(1)
                    .replaceAll("<sub[^>]*>", "_{").replace("</sub>", "}")
                    .replaceAll("<sup[^>]*>", "^{").replace("</sup>", "}")
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("([A-Za-z]+)\\[([^\\]]+)\\]", "$1_{$2}")
                    .replace("&nbsp;", " ").replace("≤", " \\le ").replace("≥", " \\ge ").replace("≠", " \\ne ")
                    .replace("&lt;", " \\lt ").replace("&gt;", " \\gt ").replace("·", " \\cdot ").replace("×", " \\times ").replace("−", "-")
                    .replace("$", "");
            m.appendReplacement(sb, Matcher.quoteReplacement("$$$" + in.trim() + "$$$"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String endWord(String before) {
        String b = before.trim();
        Matcher m = Pattern.compile("([a-z\\-]+)\\W*$").matcher(b);
        return m.find() ? m.group(1) : "";
    }

    private static String startWord(String after) {
        Matcher m = Pattern.compile("^\\W*?([a-z\\-]+)").matcher(after);
        return m.find() ? m.group(1) : "";
    }

    /** A name right after one of these words refers back to something already read. */
    private static final Set<String> REFERENCE_WORDS = new HashSet<>(java.util.Arrays.asList(
            "length", "size", "than", "modulo", "from", "to", "with", "exactly", "most", "least", "on", "in", "of", "by"));

    private static final Set<String> RESERVED = new HashSet<>(java.util.Arrays.asList(
            "int", "ll", "do", "if", "for", "and", "or", "not", "xor", "auto", "char", "long", "case", "new", "try", "max", "min", "abs", "gcd", "swap", "main", "solve", "adj", "MOD"));

    private static String cname(String base, String sub) {
        String n = base + (sub == null ? "" : sub);
        return RESERVED.contains(n) ? n + "_" : n;
    }

    private static String sizeExpr(String raw) {
        String e = clean(raw).replaceAll("\\\\[dt]?frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}", "(($1)/($2))").replace("\\cdot", "*").replace("\\times", "*").replace("{", "(").replace("}", ")").replaceAll("\\s+", "");
        // 2n becomes 2*n
        e = e.replaceAll("(\\d)([A-Za-z(])", "$1*$2");
        e = e.replaceAll("([A-Za-z])_\\(?(\\d)\\)?", "$1$2");
        return e.replace("+", " + ").replace("-", " - ").replace("*", " * ").replace("/", " / ").trim();
    }

    /** Integer type from the constraints in a sentence. */
    private static String intType(String sentence, List<String> maths, String lower) {
        int best = 0;
        for (String m : maths) {
            Matcher em = EXP.matcher(m);
            while (em.find()) best = Math.max(best, Integer.parseInt(em.group(1)));
            if (m.matches(".*\\d{11,}.*")) best = Math.max(best, 11);
        }
        if (has(lower, "real number", "floating", "decimal")) return DBL;
        return best >= 10 ? LL : INT;
    }

    private static class Parsed {
        List<Op> ops = new ArrayList<>();
        String testVar;
        boolean stoppedEarly;
    }

    private static Parsed parse(String specHtml, String[] tags) {
        Parsed out = new Parsed();
        Set<String> defined = new HashSet<>();
        String specLower = strip(specHtml).toLowerCase(Locale.US);
        boolean graphTags = false;
        if (tags != null) for (String t : tags) if (t.contains("graph") || t.contains("tree") || t.contains("dfs")) graphTags = true;

        String body = oldMath(specHtml).replaceAll("<div class=\"section-title\">.*?</div>", "");
        // Paragraphs and list items each describe a line or a group of lines.
        String[] paras = body.split("(?i)</p>|</li>|<ul>|</ul>");
        String carryRows = null;
        boolean first = true;
        for (String pHtml : paras) {
            String raw = strip(pHtml).replace('\n', ' ').trim();
            if (raw.length() == 0) continue;
            List<String> maths = new ArrayList<>();
            StringBuffer masked = new StringBuffer();
            Matcher mm = Pattern.compile("\\$\\$\\$(.+?)\\$\\$\\$").matcher(raw);
            while (mm.find()) {
                mm.appendReplacement(masked, "\u0001" + maths.size() + "\u0002");
                maths.add(mm.group(1));
            }
            mm.appendTail(masked);
            String text = masked.toString();
            for (String sentence : text.split("(?<=[.;:])\\s+(?=[A-Z])")) {
                String lower = sentence.toLowerCase(Locale.US);
                if (lower.startsWith("it is guaranteed") || lower.startsWith("the sum of") || lower.startsWith("sum of") || lower.startsWith("note") || lower.startsWith("it's guaranteed")
                        || lower.startsWith("additional constraint") || lower.startsWith("hacks") || lower.startsWith("in hacks") || lower.startsWith("all ") || lower.startsWith("for each test case, all")) continue;
                if (!has(lower, "contain", "line", "given", "follow", "denot", "input", "describ", "consist", "integer", "string")) continue;

                List<String> sMaths = new ArrayList<>();
                Matcher cm = MATH.matcher(sentence);
                while (cm.find()) sMaths.add(maths.get(Integer.parseInt(cm.group(1))));

                // Which chunk, if any, says how many lines follow.
                String rows = null;
                int rowsChunk = -1;
                List<int[]> spans = new ArrayList<>();
                cm = MATH.matcher(sentence);
                while (cm.find()) spans.add(new int[]{cm.start(), cm.end(), Integer.parseInt(cm.group(1))});
                for (int k = 0; k < spans.size(); k++) {
                    String after = sentence.substring(spans.get(k)[1]).toLowerCase(Locale.US);
                    String before = sentence.substring(0, spans.get(k)[0]).toLowerCase(Locale.US);
                    if (after.matches("^\\s*(more\\s+)?lines.*") && !before.trim().endsWith("consists of") && !before.trim().endsWith("of") && !(after.contains("test") && out.ops.isEmpty())) {
                        rows = sizeExpr(maths.get(spans.get(k)[2]));
                        rowsChunk = k;
                        break;
                    }
                }
                Matcher fixed = Pattern.compile("\\b(two|three|four|five|six) lines\\b").matcher(lower);
                if (rows == null && fixed.find() && wordCount(lower) > 0) {
                    rows = String.valueOf(java.util.Arrays.asList("two", "three", "four", "five", "six").indexOf(fixed.group(1)) + 2);
                }
                if (rows == null && carryRows != null) rows = carryRows;
                carryRows = null;

                List<Item> items = new ArrayList<>();
                String lastCount = null;
                String lastType = null;
                boolean queries = rows != null && has(lower + " " + specLower, "quer", "operation", "request", "event") && has(lower, "quer", "operation", "request", "event", "type", "form");
                boolean typedQueries = queries && has(lower, "type", "one of the following", "of the form", "format");
                for (int k = 0; k < spans.size(); k++) {
                    if (k == rowsChunk) continue;
                    String rawChunk = maths.get(spans.get(k)[2]);
                    // \texttt{a}, 'a' and "abc" are literal characters, not names.
                    if (rawChunk.contains("\\texttt") || rawChunk.contains("\\mathtt") || rawChunk.contains("'") || rawChunk.contains("\"") || rawChunk.contains("\\tt")) continue;
                    String c = clean(rawChunk);
                    int prevEnd = k == 0 ? 0 : spans.get(k - 1)[1];
                    String before = sentence.substring(prevEnd, spans.get(k)[0]).toLowerCase(Locale.US);
                    String beforeAll = sentence.substring(0, spans.get(k)[0]).toLowerCase(Locale.US);
                    String after = sentence.substring(spans.get(k)[1], k + 1 < spans.size() ? spans.get(k + 1)[0] : sentence.length()).toLowerCase(Locale.US);
                    if (relational(c)) continue;
                    if (!items.isEmpty() && !has(before, "contain") && (before.trim().endsWith("(") || has(before, "—", " – ", " - ", "where", "which", "denot", "number of", "the size", "the length", ", the ", " is ", " are ", "if ", "such that"))) break;
                    String sw = startWord(after);
                    boolean isCount = false;
                    for (String w : COUNT_WORDS) if (sw.startsWith(w)) isCount = true;
                    String ew = endWord(before);
                    boolean stringy = has(before, "string", "word", "name", "text") && !has(before, "strings", "integer");
                    boolean chary = has(before, "character", "letter", "symbol") && !has(before, "characters", "letters", "symbols");

                    Matcher a2 = ARR2.matcher(c);
                    Matcher a1 = ARR.matcher(c);
                    if (!a1.matches()) a1 = ARR_BRACED.matcher(c);
                    if (a2.matches() && rows != null) {
                        Item it = new Item();
                        it.name = cname(a2.group(1), null);
                        it.len2 = sizeExpr(a2.group(2));
                        it.type = intType(sentence, sMaths, lower);
                        if (!defined.contains(it.name)) items.add(it);
                        continue;
                    }
                    Matcher bits = Pattern.compile("^([A-Za-z]+)_\\{?(\\d)\\d?\\}?\\s*,?.*(?:dots|\\.\\.\\.).*$").matcher(c);
                    if (!a1.matches() && bits.matches() && has(beforeAll, "string")) {
                        Item it = new Item();
                        it.name = cname(bits.group(1) + bits.group(2), null);
                        it.type = STR;
                        if (!defined.contains(it.name)) items.add(it);
                        continue;
                    }
                    if (a1.matches()) {
                        Item it = new Item();
                        it.name = cname(a1.group(1), null);
                        if (stringy || (has(beforeAll, "a string", "the string", "string ") && !has(beforeAll, "strings", "integers"))) {
                            it.type = STR;
                        } else {
                            it.len = sizeExpr(a1.group(2));
                            it.type = has(beforeAll, "strings", "words") ? STR : intType(sentence, sMaths, lower);
                        }
                        for (int q = items.size() - 1; q >= 0; q--) if (items.get(q).name.equals(it.name) && items.get(q).len == null) items.remove(q);
                        if (!defined.contains(it.name)) items.add(it);
                        continue;
                    }
                    // a_1, a_2, ..., a_n written as separate chunks
                    Matcher one = Pattern.compile("^([A-Za-z]+)_\\{?1\\}?$").matcher(c);
                    if (one.matches() && k + 1 < spans.size()) {
                        String base = one.group(1);
                        int end = -1;
                        String len = null;
                        for (int q = k + 1; q < spans.size() && q <= k + 4; q++) {
                            Matcher tail = Pattern.compile("^" + base + "_\\{?([^{}]+?)\\}?$").matcher(clean(maths.get(spans.get(q)[2])));
                            String cq = clean(maths.get(spans.get(q)[2]));
                            if (tail.matches() && !tail.group(1).matches("\\d+")) {
                                end = q;
                                len = tail.group(1);
                                break;
                            }
                            if (!cq.matches("^(" + base + "_\\{?\\d+\\}?|\\\\[lcv]?dots|\\.\\.\\.)$")) break;
                        }
                        if (end > 0) {
                            Item it = new Item();
                            it.name = cname(base, null);
                            it.len = sizeExpr(len);
                            it.type = has(beforeAll, "strings", "words") ? STR : intType(sentence, sMaths, lower);
                            if (!defined.contains(it.name)) items.add(it);
                            k = end;
                            continue;
                        }
                    }
                    if (isCount) {
                        lastCount = sizeExpr(c);
                        continue;
                    }
                    if (REFERENCE_WORDS.contains(ew)) {
                        if (!(ew.equals("by") && has(before, "integer", "number"))) continue;
                    }
                    // Plain names, maybe several in one chunk: n, m, k
                    String[] parts = c.split("\\s*,\\s*|\\s+");
                    boolean all = true;
                    List<Item> found = new ArrayList<>();
                    for (String part : parts) {
                        Matcher id = IDENT.matcher(part.replace("'", ""));
                        if (!id.matches() || part.startsWith("\\")) {
                            all = false;
                            break;
                        }
                        String base = id.group(1), sub = id.group(2);
                        if (base.length() > 1 && sub == null && !base.equals(base.toLowerCase(Locale.US)) && base.length() > 2) {
                            all = false;
                            break;
                        }
                        if (sub == null && (base.equals("i") || base.equals("j"))) continue;
                        Item it = new Item();
                        boolean perRow = sub != null && sub.matches("[ij]");
                        it.name = cname(base, perRow || sub == null ? null : sub);
                        String joiner = before.trim();
                        boolean plural = has(before, "strings", "words") && !has(before, "integer");
                        if (lastType != null && (joiner.equals("and") || joiner.equals(",") || joiner.equals(", and") || joiner.length() == 0)) it.type = lastType;
                        else if (after.trim().startsWith("of length")) it.type = STR;
                        else it.type = stringy || plural ? STR : chary ? CHR : intType(sentence, sMaths, lower);
                        lastType = it.type;
                        if (perRow && rows == null && lastCount != null) it.len = lastCount;
                        found.add(it);
                    }
                    if (!all) continue;
                    for (Item it : found) {
                        if (defined.contains(it.name) && !(rows != null && it.name.equals(it.name))) continue;
                        if (defined.contains(it.name)) continue;
                        boolean dup = false;
                        for (Item x : items) if (x.name.equals(it.name)) dup = true;
                        if (!dup) items.add(it);
                    }
                }

                // The first line of a multi-test input.
                if (first && out.testVar == null && rows == null && items.size() == 1 && items.get(0).len == null
                        && has(specLower, "test case", "testcase", "test-case", "sets of input", "input data sets", "test sets") && (items.get(0).name.equalsIgnoreCase("t") || has(lower, "test"))) {
                    out.testVar = items.get(0).name;
                    defined.add(out.testVar);
                    first = false;
                    continue;
                }
                // "contains one string consisting of six digits": a string with no name.
                boolean namelessString = lower.matches(".*\\b(a|one|single|the|non-empty) string\\b.*")
                        || (lower.contains("line") && has(lower, "consists of the letters", "consisting of the letters", "consists of characters", "consists of lowercase", "consists of uppercase")
                        || (lower.contains("line") && has(lower, "letter", "character") && has(lower, "consist")));
                if (rows == null && items.isEmpty() && namelessString && !has(lower, "strings", "integer")) {
                    Item it = new Item();
                    it.name = defined.contains("s") ? "s2" : "s";
                    it.type = STR;
                    items.add(it);
                }
                if (rows == null && items.isEmpty() && lastCount != null && has(lower, "integers", "numbers") && !lastCount.equals(out.testVar)) {
                    Item it = new Item();
                    it.name = lower.contains("permutation") && !defined.contains("p") ? "p" : !defined.contains("a") ? "a" : "b";
                    it.len = lastCount;
                    it.type = intType(sentence, sMaths, lower);
                    items.add(it);
                }
                if (rows == null && items.isEmpty()) continue;
                first = false;

                if (rows != null && out.testVar != null && rows.equals(out.testVar) && out.ops.isEmpty()) {
                    // "The next t lines each contain..." means one line per test.
                    rows = null;
                }
                if (rows != null && items.isEmpty() && rowsChunk >= 0 && rowsChunk == spans.size() - 1 && wordCount(lower) == 0 && !has(lower, "string", "character", "integer", "quer", "symbol", "word", "letter", "digit")) {
                    // Rows announced here, described in the next sentence.
                    carryRows = rows;
                    continue;
                }

                if (rows == null) {
                    Op cur = null;
                    for (Item it : items) {
                        defined.add(it.name);
                        if (it.len != null) {
                            Op op = new Op();
                            op.kind = ARRAY;
                            op.names.add(it.name);
                            op.types.add(it.type);
                            op.len = it.len;
                            out.ops.add(op);
                            cur = null;
                        } else {
                            if (cur == null) {
                                cur = new Op();
                                cur.kind = SCALARS;
                                out.ops.add(cur);
                            }
                            cur.names.add(it.name);
                            cur.types.add(it.type);
                        }
                    }
                    continue;
                }

                Op op = new Op();
                op.len = rows;
                if (queries) {
                    op.kind = QUERIES;
                    op.typed = typedQueries;
                    for (Item it : items) {
                        if (it.len != null || it.len2 != null) continue;
                        if (typedQueries) break;
                        op.names.add(it.name);
                        op.types.add(it.type);
                    }
                    out.ops.add(op);
                    out.stoppedEarly = true;
                    return out;
                }
                if (items.size() == 1 && items.get(0).len2 != null) {
                    op.kind = MATRIX;
                    op.names.add(items.get(0).name);
                    op.types.add(items.get(0).type);
                    op.len2 = items.get(0).len2;
                } else if (items.size() == 1 && items.get(0).len == null && lastCount != null && !items.get(0).type.equals(STR)) {
                    // "Each of the next n lines contains m integers ... a": a matrix.
                    op.kind = MATRIX;
                    op.names.add(items.get(0).name);
                    op.types.add(items.get(0).type);
                    op.len2 = lastCount;
                } else if (items.isEmpty() && wordCount(lower) >= 4 || items.isEmpty() && wordCount(lower) > 0 && has(lower, "matrix", "grid", "table")) {
                    op.kind = MATRIX;
                    op.names.add(defined.contains("a") ? "mat" : "a");
                    op.types.add(intType(sentence, sMaths, lower));
                    op.len2 = String.valueOf(wordCount(lower));
                } else if (items.isEmpty() || (items.size() == 1 && items.get(0).len != null)) {
                    boolean chars = has(lower, "character", "string", "symbol", "letter", "'", "\"", "digit", "row", "word", "of length", "consisting of", "grid", "matrix", "binary")
                            && !has(lower, "integers", "numbers");
                    if (chars || (items.size() == 1 && items.get(0).type.equals(STR))) {
                        op.kind = GRID;
                        String nm = items.isEmpty() ? (lower.contains("string") ? "s" : "g") : items.get(0).name;
                        if (defined.contains(nm)) nm = "g";
                        op.names.add(nm);
                        op.types.add(STR);
                    } else if (lastCount != null || items.size() == 1) {
                        op.kind = MATRIX;
                        String nm = items.isEmpty() ? (defined.contains("a") ? "mat" : "a") : items.get(0).name;
                        op.names.add(nm);
                        op.types.add(intType(sentence, sMaths, lower));
                        op.len2 = items.isEmpty() ? lastCount : items.get(0).len;
                    } else if (wordCount(lower) > 0) {
                        // "Then n lines contain three integers each": columns with no names.
                        op.kind = ROWS;
                        String type = intType(sentence, sMaths, lower);
                        for (char ch = 'a'; op.names.size() < wordCount(lower) && ch <= 'z'; ch++) {
                            String nm = String.valueOf(ch);
                            if (defined.contains(nm) || nm.equals("i") || nm.equals("j")) continue;
                            op.names.add(nm);
                            op.types.add(type);
                        }
                    } else {
                        out.stoppedEarly = true;
                        return out;
                    }
                } else {
                    boolean edge = (items.size() == 2 || items.size() == 3) && (has(lower, "edge", "road", "connect", "wire", "tunnel", "path between", "bidirectional")
                            || (graphTags && pairish(items.get(0).name, items.get(1).name)));
                    if (edge && items.get(0).len == null && items.get(1).len == null) {
                        op.kind = EDGES;
                        op.typed = !has(lower, "directed edge", "from vertex", "arc", "one-way") || has(lower, "undirected", "bidirectional");
                    } else {
                        op.kind = ROWS;
                    }
                    for (Item it : items) {
                        if (it.len != null) {
                            out.stoppedEarly = true;
                            return out;
                        }
                        op.names.add(it.name);
                        op.types.add(it.type);
                    }
                }
                for (String n : op.names) defined.add(n);
                out.ops.add(op);
            }
        }
        return out;
    }

    /** "three integers" gives 3; 0 when no such count is written out. */
    private static int wordCount(String lower) {
        Matcher m = Pattern.compile("\\b(two|three|four|five|six|[2-6])\\s+(?:space-separated\\s+|non-negative\\s+|positive\\s+)?(?:integers|numbers)").matcher(lower);
        if (!m.find()) return 0;
        String w = m.group(1);
        String[] words = {"two", "three", "four", "five", "six"};
        for (int i = 0; i < words.length; i++) if (words[i].equals(w)) return i + 2;
        return Integer.parseInt(w);
    }

    private static boolean pairish(String a, String b) {
        String p = a + b;
        return p.equals("uv") || p.equals("xy") || p.equals("ab") || p.equals("pq") || p.equals("st");
    }

    // ------------------------------------------------------------------ sample check

    private static class Expr {
        final String s;
        int i;
        final Map<String, Long> env;

        Expr(String s, Map<String, Long> env) {
            this.s = s.replace(" ", "");
            this.env = env;
        }

        long parse() {
            long v = term();
            while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                char o = s.charAt(i++);
                long r = term();
                v = o == '+' ? v + r : v - r;
            }
            return v;
        }

        long term() {
            long v = atom();
            while (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
                char o = s.charAt(i++);
                long r = atom();
                v = o == '*' ? v * r : v / r;
            }
            return v;
        }

        long atom() {
            if (i >= s.length()) throw new IllegalStateException();
            char c = s.charAt(i);
            if (c == '(') {
                i++;
                long v = parse();
                i++;
                return v;
            }
            int st = i;
            if (Character.isDigit(c)) {
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                return Long.parseLong(s.substring(st, i));
            }
            while (i < s.length() && Character.isLetterOrDigit(s.charAt(i))) i++;
            Long v = env.get(s.substring(st, i));
            if (v == null) throw new IllegalStateException();
            return v;
        }
    }

    private static long eval(String e, Map<String, Long> env) {
        Expr x = new Expr(e, env);
        long v = x.parse();
        if (x.i != x.s.length() || v < 0 || v > 10_000_000) throw new IllegalStateException();
        return v;
    }

    private static int pos;

    private static void take(String[] tok, String type, Map<String, Long> env, String name) {
        if (pos >= tok.length) throw new IllegalStateException();
        String t = tok[pos++];
        if (type.equals(INT) || type.equals(LL)) {
            if (!t.matches("-?\\d+")) throw new IllegalStateException();
            if (name != null) env.put(name, Long.parseLong(t.length() > 17 ? "0" : t));
        } else if (type.equals(CHR)) {
            if (t.length() != 1) throw new IllegalStateException();
        } else if (type.equals(DBL)) {
            if (!t.matches("-?[\\d.]+(e-?\\d+)?")) throw new IllegalStateException();
        }
    }

    private static boolean replay(Parsed p, String sample) {
        String[] tok = sample.trim().split("\\s+");
        if (tok.length == 0 || tok[0].length() == 0) return false;
        pos = 0;
        try {
            Map<String, Long> env = new HashMap<>();
            long tests = 1;
            if (p.testVar != null) {
                take(tok, INT, env, p.testVar);
                tests = env.get(p.testVar);
            }
            for (long t = 0; t < tests; t++) {
                for (Op op : p.ops) {
                    if (op.kind == SCALARS) {
                        for (int k = 0; k < op.names.size(); k++) take(tok, op.types.get(k), env, op.names.get(k));
                    } else if (op.kind == ARRAY) {
                        long n = eval(op.len, env);
                        for (long k = 0; k < n; k++) take(tok, op.types.get(0), env, null);
                    } else if (op.kind == GRID) {
                        long n = eval(op.len, env);
                        for (long k = 0; k < n; k++) take(tok, STR, env, null);
                    } else if (op.kind == MATRIX) {
                        long n = eval(op.len, env), m = eval(op.len2, env);
                        for (long k = 0; k < n * m; k++) take(tok, op.types.get(0), env, null);
                    } else if (op.kind == ROWS || op.kind == EDGES || (op.kind == QUERIES && !op.typed && !op.names.isEmpty())) {
                        long n = eval(op.len, env);
                        for (long k = 0; k < n; k++)
                            for (int c = 0; c < op.names.size(); c++) take(tok, op.types.get(c), env, null);
                    } else {
                        return false;
                    }
                }
            }
            return pos == tok.length;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ code

    private static String decl(String type) {
        return type.equals(LL) ? "ll" : type;
    }

    private static void line(StringBuilder sb, int depth, String s) {
        for (int i = 0; i < depth; i++) sb.append("    ");
        sb.append(s).append('\n');
    }

    /** Declarations grouped by type: "int n, k;" then "string s;". */
    private static void declare(StringBuilder sb, int depth, List<String> names, List<String> types, String suffix, boolean vector) {
        List<String> order = new ArrayList<>();
        Map<String, StringBuilder> byType = new HashMap<>();
        for (int k = 0; k < names.size(); k++) {
            String t = types.get(k);
            if (!byType.containsKey(t)) {
                byType.put(t, new StringBuilder());
                order.add(t);
            }
            StringBuilder b = byType.get(t);
            if (b.length() > 0) b.append(", ");
            b.append(names.get(k)).append(suffix);
        }
        for (String t : order) line(sb, depth, (vector ? "vector<" + decl(t) + "> " : decl(t) + " ") + byType.get(t) + ";");
    }

    private static String reads(List<String> names, String index) {
        StringBuilder b = new StringBuilder("cin");
        for (String n : names) b.append(" >> ").append(n).append(index);
        return b.append(';').toString();
    }

    private static String vertexCount(Parsed p, Op edges) {
        for (Op op : p.ops) {
            if (op == edges) break;
            if (op.kind == SCALARS && op.names.contains("n")) return "n";
        }
        for (Op op : p.ops) {
            if (op == edges) break;
            if (op.kind == SCALARS) return op.names.get(0);
        }
        return "n";
    }

    private static String loopVar(Op op) {
        return op.names.contains("i") ? "idx" : "i";
    }

    private static String generate(Parsed p, boolean yesNo, String mod, boolean usesLl, String header) {
        StringBuilder body = new StringBuilder();
        boolean queryLoop = false;
        for (int k = 0; k < p.ops.size(); k++) {
            Op op = p.ops.get(k);
            if (body.length() > 0) body.append('\n');
            String i = loopVar(op);
            String loop = "for (int " + i + " = 0; " + i + " < " + op.len + "; " + i + "++) {";
            switch (op.kind) {
                case SCALARS:
                    declare(body, 1, op.names, op.types, "", false);
                    line(body, 1, reads(op.names, ""));
                    break;
                case ARRAY:
                    line(body, 1, "vector<" + decl(op.types.get(0)) + "> " + op.names.get(0) + "(" + op.len + ");");
                    line(body, 1, loop);
                    line(body, 2, "cin >> " + op.names.get(0) + "[" + i + "];");
                    line(body, 1, "}");
                    break;
                case GRID:
                    line(body, 1, "vector<string> " + op.names.get(0) + "(" + op.len + ");");
                    line(body, 1, loop);
                    line(body, 2, "cin >> " + op.names.get(0) + "[" + i + "];");
                    line(body, 1, "}");
                    break;
                case MATRIX: {
                    String t = decl(op.types.get(0));
                    String nm = op.names.get(0);
                    line(body, 1, "vector<vector<" + t + ">> " + nm + "(" + op.len + ", vector<" + t + ">(" + op.len2 + "));");
                    line(body, 1, loop);
                    line(body, 2, "for (int j = 0; j < " + op.len2 + "; j++) {");
                    line(body, 3, "cin >> " + nm + "[" + i + "][j];");
                    line(body, 2, "}");
                    line(body, 1, "}");
                    break;
                }
                case ROWS: {
                    String suffix = "(" + op.len + ")";
                    declare(body, 1, op.names, op.types, suffix, true);
                    line(body, 1, loop);
                    line(body, 2, reads(op.names, "[" + i + "]"));
                    line(body, 1, "}");
                    break;
                }
                case EDGES: {
                    String n = vertexCount(p, op);
                    boolean weighted = op.names.size() == 3;
                    String u = op.names.get(0), v = op.names.get(1);
                    if (u.equals(v)) v = v + "2";
                    String w = weighted ? op.names.get(2) : null;
                    String wt = weighted ? decl(op.types.get(2)) : null;
                    line(body, 1, weighted ? "vector<vector<pair<int, " + wt + ">>> adj(" + n + " + 1);" : "vector<vector<int>> adj(" + n + " + 1);");
                    line(body, 1, loop);
                    if (weighted) {
                        line(body, 2, "int " + u + ", " + v + ";");
                        line(body, 2, wt + " " + w + ";");
                        line(body, 2, "cin >> " + u + " >> " + v + " >> " + w + ";");
                        line(body, 2, "adj[" + u + "].push_back({" + v + ", " + w + "});");
                        if (op.typed) line(body, 2, "adj[" + v + "].push_back({" + u + ", " + w + "});");
                    } else {
                        line(body, 2, "int " + u + ", " + v + ";");
                        line(body, 2, "cin >> " + u + " >> " + v + ";");
                        line(body, 2, "adj[" + u + "].push_back(" + v + ");");
                        if (op.typed) line(body, 2, "adj[" + v + "].push_back(" + u + ");");
                    }
                    line(body, 1, "}");
                    break;
                }
                case QUERIES: {
                    line(body, 1, "// Write your logic here");
                    body.append("\n");
                    line(body, 1, loop);
                    if (op.typed || op.names.isEmpty()) {
                        line(body, 2, "int type;");
                        line(body, 2, "cin >> type;");
                        line(body, 2, "// read the rest of this query by its type");
                    } else {
                        declare(body, 2, op.names, op.types, "", false);
                        line(body, 2, reads(op.names, ""));
                    }
                    body.append("\n");
                    line(body, 2, "// answer this query");
                    line(body, 1, "}");
                    queryLoop = true;
                    break;
                }
            }
        }
        if (p.ops.isEmpty()) line(body, 1, "// Read the input here");
        if (!queryLoop) {
            body.append('\n');
            line(body, 1, "// Write your logic here");
        }
        if (yesNo) line(body, 1, "// cout << (ok ? \"YES\" : \"NO\") << '\\n';");

        StringBuilder sb = new StringBuilder();
        boolean own = header != null && header.trim().length() > 0;
        if (own) sb.append(header.replaceAll("\\s+$", "")).append('\n');
        else sb.append("#include <bits/stdc++.h>\nusing namespace std;\n");
        // A custom header may already define ll or MOD; only add what is missing.
        boolean hasLl = own && Pattern.compile("\\bll\\s*=|long long\\s+ll\\b|define\\s+ll\\b").matcher(header).find();
        boolean hasMod = own && Pattern.compile("\\bMOD\\b").matcher(header).find();
        if ((usesLl || (mod != null && !hasMod)) && !hasLl) sb.append("using ll = long long;\n");
        if (mod != null && !hasMod) sb.append("\nconst ll MOD = ").append(mod).append(";\n");
        sb.append("\nvoid solve() {\n").append(body).append("}\n\n");
        sb.append("int main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n\n");
        if (p.testVar != null) {
            String t = p.testVar.equals("solve") ? "tc" : p.testVar;
            sb.append("    int ").append(t).append(";\n    cin >> ").append(t).append(";\n\n    while (").append(t).append("--) {\n        solve();\n    }\n");
        } else {
            sb.append("    solve();\n");
        }
        sb.append("\n    return 0;\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ entry

    public static Result fromHtml(String html, String[] tags) {
        return fromHtml(html, tags, null);
    }

    public static Result fromHtml(String html, String[] tags, String header) {
        String spec = between(html, "class=\"input-specification\">", "<div class=\"output-specification\">");
        String output = between(html, "<div class=\"output-specification\">", "<div class=\"sample-tests\">");
        List<String> samples = new ArrayList<>();
        Matcher sm = Pattern.compile("<div class=\"input\">\\s*<div class=\"title\">Input</div>\\s*<pre[^>]*>(.*?)</pre>", Pattern.DOTALL).matcher(html);
        while (sm.find()) samples.add(strip(sm.group(1)));

        Parsed p = parse(spec == null ? "" : spec, tags);
        // Without a test count in the statement, a sample whose first line is one number followed by blocks may still be multi-test,
        // but guessing would be worse than leaving it alone.
        boolean checked = !samples.isEmpty() && !p.ops.isEmpty();
        for (String s : samples) if (!replay(p, s)) checked = false;

        String outLower = output == null ? "" : strip(output).toLowerCase(Locale.US);
        boolean yesNo = outLower.contains("yes") && outLower.contains("no");
        String mod = null;
        if (outLower.contains("998244353") || outLower.contains("998\\,244\\,353") || outLower.contains("998 244 353")) mod = "998244353";
        else if (outLower.matches("(?s).*(10\\^9\\s*\\+\\s*7|10\\^\\{9\\}\\s*\\+\\s*7|1000000007|1\\\\,000\\\\,000\\\\,007).*")) mod = "1e9 + 7";
        boolean usesLl = false;
        for (Op op : p.ops) for (String t : op.types) if (t.equals(LL)) usesLl = true;

        Result r = new Result();
        r.code = generate(p, yesNo, mod, usesLl, header);
        r.checked = checked;
        if (spec == null) r.note = "Couldn't find the input format. Basic template.";
        else if (checked) r.note = "Reads the sample input exactly";
        else if (p.stoppedEarly) r.note = "Queries or uneven lines: finish the reading part";
        else r.note = "Check the reading part against the statement";
        return r;
    }

    private static String between(String s, String a, String b) {
        int i = s.indexOf(a);
        if (i < 0) return null;
        int j = s.indexOf(b, i);
        return j < 0 ? s.substring(i + a.length()) : s.substring(i + a.length(), j);
    }
}
