package dev.errorlens.application.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de stack traces Java. Suporta cabeçalhos com "Exception in thread", "Caused by:",
 * "Suppressed:" (aninhados por indentação), "... N more" / "... N common frames omitted",
 * mensagens multilinha, sufixos do Logback ("~[jar:versão]") e stack traces truncados.
 */
public final class StackTraceParser {

    private static final Pattern HEADER = Pattern.compile(
            "^(?<indent>\\s*)(?:Exception in thread \"[^\"]*\"\\s+)?(?:(?<rel>Caused by|Suppressed):\\s+)?"
                    + "(?<cls>(?:[a-zA-Z_$][\\w$]*\\.)+[A-Z_$][\\w$]*)(?::\\s?(?<msg>.*))?$");
    private static final Pattern FRAME = Pattern.compile(
            "^(?<indent>\\s*)at\\s+(?:(?<module>[\\w.$-]+(?:@[\\w.+-]+)?)/)?(?<cls>[\\w$.<>/-]+)\\.(?<method>[\\w$<>-]+)"
                    + "\\((?<loc>[^)]*)\\)(?:\\s*~?\\[[^\\]]*\\])?\\s*$");
    private static final Pattern MORE = Pattern.compile(
            "^\\s*\\.\\.\\.\\s+(?<n>\\d+)\\s+(?:more|common frames omitted)\\s*$");

    private StackTraceParser() {
    }

    public static Result parse(String text, List<String> applicationPackages) {
        List<ParsedException> exceptions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Integer> openByIndent = new ArrayList<>();
        ParsedException current = null;
        int unparsed = 0;
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        for (String rawLine : lines) {
            String line = stripTrailing(rawLine);
            if (line.isBlank()) {
                continue;
            }
            Matcher frame = FRAME.matcher(line);
            if (frame.matches()) {
                if (current == null) {
                    current = synthetic(exceptions, warnings);
                    track(openByIndent, 0, current.index());
                }
                current.frames().add(frame(frame, line.strip(), applicationPackages));
                continue;
            }
            Matcher more = MORE.matcher(line);
            if (more.matches()) {
                if (current != null) {
                    current.omitted(current.omittedFrames() + Integer.parseInt(more.group("n")));
                }
                continue;
            }
            Matcher header = HEADER.matcher(line);
            if (header.matches() && looksLikeThrowable(header.group("cls"))) {
                int indent = indentLevel(header.group("indent"));
                String rel = header.group("rel");
                String relation = rel == null ? "THROWN" : rel.equals("Caused by") ? "CAUSED_BY" : "SUPPRESSED";
                Integer parent = null;
                if ("CAUSED_BY".equals(relation)) {
                    parent = openAt(openByIndent, indent);
                } else if ("SUPPRESSED".equals(relation)) {
                    parent = openAt(openByIndent, Math.max(0, indent - 1));
                }
                if (!"THROWN".equals(relation) && parent == null) {
                    warnings.add("'" + rel + "' encontrado sem exception anterior correspondente; tratado como "
                            + "início de nova cadeia.");
                }
                ParsedException parsed = new ParsedException(exceptions.size(), header.group("cls"),
                        header.group("msg") == null ? null : header.group("msg").strip(),
                        parent == null ? "THROWN" : relation, indent, parent, new ArrayList<>());
                exceptions.add(parsed);
                track(openByIndent, indent, parsed.index());
                current = parsed;
                continue;
            }
            if (current != null && current.frames().isEmpty()) {
                current.appendMessage(line.strip());
            } else {
                unparsed++;
            }
        }

        if (exceptions.isEmpty()) {
            warnings.add("Nenhuma exception ou frame Java reconhecido no conteúdo informado.");
        }
        for (ParsedException ex : exceptions) {
            if (ex.frames().isEmpty() && ex.omittedFrames() == 0) {
                warnings.add("A exception " + ex.simpleName() + " não possui frames: o stack trace pode estar "
                        + "truncado ou ter sido registrado sem pilha.");
            }
        }
        if (unparsed > 0) {
            warnings.add(unparsed + " linha(s) não reconhecida(s) foram ignoradas.");
        }
        return new Result(exceptions, warnings, lines.length, unparsed);
    }

    private static ParsedException synthetic(List<ParsedException> exceptions, List<String> warnings) {
        warnings.add("O stack trace começa com frames sem cabeçalho de exception (conteúdo truncado no início).");
        ParsedException ex = new ParsedException(exceptions.size(), "(exception desconhecida)",
                "Cabeçalho ausente no conteúdo importado", "THROWN", 0, null, new ArrayList<>());
        exceptions.add(ex);
        return ex;
    }

    private static Frame frame(Matcher m, String raw, List<String> applicationPackages) {
        String cls = m.group("cls");
        String loc = m.group("loc");
        String file = null;
        Integer line = null;
        boolean nativeMethod = "Native Method".equals(loc);
        if (!nativeMethod && loc != null && !loc.isBlank() && !"Unknown Source".equals(loc)) {
            int colon = loc.lastIndexOf(':');
            if (colon > 0 && loc.substring(colon + 1).matches("\\d+")) {
                file = loc.substring(0, colon);
                line = Integer.parseInt(loc.substring(colon + 1));
            } else {
                file = loc;
            }
        }
        String category = FrameClassifier.classify(cls, applicationPackages);
        return new Frame(raw, cls, m.group("method"), file, line, m.group("module"), category,
                FrameClassifier.APPLICATION.equals(category), nativeMethod);
    }

    private static boolean looksLikeThrowable(String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        String lower = simple.toLowerCase(Locale.ROOT);
        return lower.endsWith("exception") || lower.endsWith("error") || lower.endsWith("throwable")
                || lower.contains("exception") || simple.equals("ServiceUnavailable")
                || simple.equals("InternalServerError") || simple.equals("BadGateway")
                || className.contains("$");
    }

    private static int indentLevel(String indent) {
        int level = 0;
        int spaces = 0;
        for (char c : indent.toCharArray()) {
            if (c == '\t') {
                level++;
            } else if (c == ' ') {
                spaces++;
            }
        }
        return level + spaces / 4;
    }

    private static void track(List<Integer> openByIndent, int indent, int index) {
        while (openByIndent.size() <= indent) {
            openByIndent.add(null);
        }
        openByIndent.set(indent, index);
        for (int i = indent + 1; i < openByIndent.size(); i++) {
            openByIndent.set(i, null);
        }
    }

    private static Integer openAt(List<Integer> openByIndent, int indent) {
        return indent < openByIndent.size() ? openByIndent.get(indent) : null;
    }

    private static String stripTrailing(String line) {
        return line.stripTrailing();
    }

    public record Frame(String raw, String className, String method, String file, Integer line, String module,
                        String category, boolean application, boolean nativeMethod) {
    }

    public static final class ParsedException {
        private final int index;
        private final String className;
        private String message;
        private final String relation;
        private final int indent;
        private final Integer parentIndex;
        private final List<Frame> frames;
        private int omittedFrames;

        ParsedException(int index, String className, String message, String relation, int indent,
                        Integer parentIndex, List<Frame> frames) {
            this.index = index;
            this.className = className;
            this.message = message;
            this.relation = relation;
            this.indent = indent;
            this.parentIndex = parentIndex;
            this.frames = frames;
        }

        void appendMessage(String continuation) {
            message = message == null || message.isEmpty() ? continuation : message + "\n" + continuation;
        }

        void omitted(int value) {
            omittedFrames = value;
        }

        public int index() {
            return index;
        }

        public String className() {
            return className;
        }

        public String simpleName() {
            String simple = className.substring(className.lastIndexOf('.') + 1);
            int inner = simple.lastIndexOf('$');
            return inner < 0 ? simple : simple.substring(inner + 1);
        }

        public String message() {
            return message;
        }

        public String relation() {
            return relation;
        }

        public int indent() {
            return indent;
        }

        public Integer parentIndex() {
            return parentIndex;
        }

        public List<Frame> frames() {
            return frames;
        }

        public int omittedFrames() {
            return omittedFrames;
        }
    }

    public record Result(List<ParsedException> exceptions, List<String> warnings, int totalLines, int unparsedLines) {
    }
}
