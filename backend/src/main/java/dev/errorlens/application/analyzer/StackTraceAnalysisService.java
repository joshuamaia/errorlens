package dev.errorlens.application.analyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.errorlens.application.analyzer.StackTraceParser.Frame;
import dev.errorlens.application.analyzer.StackTraceParser.ParsedException;
import dev.errorlens.application.diagnosis.DiagnosisContext;
import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.application.diagnosis.DiagnosisResult;
import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.domain.exception.InvalidInputException;

/**
 * Análise de stack traces importados: cadeia de causas, root cause, frames por categoria,
 * classes envolvidas, primeiro frame da aplicação e diagnósticos.
 */
@Service
public class StackTraceAnalysisService {

    private final DiagnosisEngine engine;
    private final ErrorLensProperties properties;

    public StackTraceAnalysisService(DiagnosisEngine engine, ErrorLensProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    public StackTraceAnalysis analyze(String content, List<String> applicationPackages) {
        if (content == null || content.isBlank()) {
            throw new InvalidInputException("EMPTY_CONTENT", "Informe um stack trace para análise.");
        }
        if (content.length() > properties.analyzer().maxStacktraceChars()) {
            throw new InvalidInputException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE, "CONTENT_TOO_LARGE",
                    "O stack trace excede o limite de " + properties.analyzer().maxStacktraceChars() + " caracteres.");
        }
        List<String> packages = applicationPackages == null ? List.of()
                : applicationPackages.stream().map(String::trim).filter(p -> p.matches("[\\w.$]{1,200}")).toList();
        StackTraceParser.Result result = StackTraceParser.parse(content, packages);
        List<ParsedException> exceptions = result.exceptions();

        List<ExceptionView> views = exceptions.stream().map(StackTraceAnalysisService::view).toList();
        List<ParsedException> chain = mainChain(exceptions);
        ExceptionView primary = chain.isEmpty() ? null : views.get(chain.getFirst().index());
        ExceptionView root = chain.isEmpty() ? null : views.get(chain.getLast().index());

        FrameRef firstAppFrame = null;
        for (int i = chain.size() - 1; i >= 0 && firstAppFrame == null; i--) {
            ParsedException ex = chain.get(i);
            for (int f = 0; f < ex.frames().size(); f++) {
                if (ex.frames().get(f).application()) {
                    firstAppFrame = new FrameRef(ex.index(), f, ex.frames().get(f));
                    break;
                }
            }
        }

        Map<String, Long> categories = new TreeMap<>(exceptions.stream().flatMap(e -> e.frames().stream())
                .collect(Collectors.groupingBy(Frame::category, Collectors.counting())));
        Map<String, InvolvedClass> classes = new LinkedHashMap<>();
        exceptions.stream().flatMap(e -> e.frames().stream()).forEach(frame -> classes
                .computeIfAbsent(frame.className(), c -> new InvolvedClass(c, frame.category(), new ArrayList<>(),
                        frame.file()))
                .add(frame.method(), frame.line()));
        List<InvolvedClass> involved = classes.values().stream()
                .sorted((a, b) -> Boolean.compare(FrameClassifier.APPLICATION.equals(b.category()),
                        FrameClassifier.APPLICATION.equals(a.category())))
                .toList();

        int totalFrames = exceptions.stream().mapToInt(e -> e.frames().size()).sum();
        int appFrames = (int) exceptions.stream().flatMap(e -> e.frames().stream()).filter(Frame::application).count();
        int omitted = exceptions.stream().mapToInt(ParsedException::omittedFrames).sum();

        List<ExceptionInfo> chainInfo = chain.stream()
                .map(e -> new ExceptionInfo(e.className(), e.message()))
                .toList();
        List<DiagnosisResult> diagnoses = engine.diagnose(
                DiagnosisContext.ofExceptions(DiagnosisContext.Source.STACKTRACE, null, chainInfo));

        return new StackTraceAnalysis(views, primary, root, chain.size(), firstAppFrame, categories, involved,
                new Stats(result.totalLines(), totalFrames, appFrames, omitted, result.unparsedLines(),
                        (int) exceptions.stream().filter(e -> "SUPPRESSED".equals(e.relation())).count()),
                result.warnings(), diagnoses, DiagnosisEngine.DISCLAIMER);
    }

    /**
     * Cadeia principal: primeira exception lançada seguida de suas causas ("Caused by").
     */
    static List<ParsedException> mainChain(List<ParsedException> exceptions) {
        List<ParsedException> chain = new ArrayList<>();
        ParsedException current = exceptions.stream().filter(e -> "THROWN".equals(e.relation())).findFirst()
                .orElse(null);
        while (current != null) {
            chain.add(current);
            final int parent = current.index();
            current = exceptions.stream()
                    .filter(e -> "CAUSED_BY".equals(e.relation()) && e.parentIndex() != null
                            && e.parentIndex() == parent)
                    .findFirst().orElse(null);
        }
        return chain;
    }

    private static ExceptionView view(ParsedException e) {
        return new ExceptionView(e.index(), e.className(), e.simpleName(), e.message(), e.relation(), e.indent(),
                e.parentIndex(), e.frames(), e.omittedFrames());
    }

    public record ExceptionView(int index, String className, String simpleName, String message, String relation,
                                int depth, Integer parentIndex, List<Frame> frames, int omittedFrames) {
    }

    public record FrameRef(int exceptionIndex, int frameIndex, Frame frame) {
    }

    public record InvolvedClass(String className, String category, List<String> methods, String file) {
        void add(String method, Integer line) {
            String entry = line == null ? method : method + ":" + line;
            if (!methods.contains(entry)) {
                methods.add(entry);
            }
        }
    }

    public record Stats(int totalLines, int totalFrames, int applicationFrames, int omittedFrames,
                        int unparsedLines, int suppressedCount) {
    }

    public record StackTraceAnalysis(List<ExceptionView> exceptions, ExceptionView primary, ExceptionView rootCause,
                                     int chainLength, FrameRef firstApplicationFrame, Map<String, Long> frameCategories,
                                     List<InvolvedClass> involvedClasses, Stats stats, List<String> warnings,
                                     List<DiagnosisResult> diagnoses, String disclaimer) {
    }
}
