package dev.errorlens.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.errorlens.application.analyzer.StackTraceParser.ParsedException;

class StackTraceParserTest {

    private static final String CHAINED = """
            com.acme.InvoiceGenerationException: Falha ao gerar fatura
            \tat com.acme.OrderService.generate(OrderService.java:89)
            \tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
            \tSuppressed: java.io.IOException: falha ao fechar
            \t\tat com.acme.MetadataReader.close(MetadataReader.java:31)
            \t\t... 1 more
            Caused by: com.acme.InvoiceDataException: Metadado inválido
            \tat com.acme.Parser.parse(Parser.java:24)
            \t... 2 more
            Caused by: java.lang.NumberFormatException: For input string: "três"
            \tat java.base/java.lang.Integer.parseInt(Integer.java:662)
            \tat com.acme.Parser.parse(Parser.java:22) ~[app.jar:1.0]
            \t... 3 more
            """;

    @Test
    void parsesCausedBySuppressedAndOmittedFrames() {
        StackTraceParser.Result result = StackTraceParser.parse(CHAINED, List.of());

        assertThat(result.exceptions()).extracting(ParsedException::simpleName)
                .containsExactly("InvoiceGenerationException", "IOException", "InvoiceDataException",
                        "NumberFormatException");
        ParsedException suppressed = result.exceptions().get(1);
        assertThat(suppressed.relation()).isEqualTo("SUPPRESSED");
        assertThat(suppressed.parentIndex()).isZero();
        assertThat(suppressed.omittedFrames()).isEqualTo(1);
        assertThat(result.exceptions().get(2).parentIndex()).isZero();
        assertThat(result.exceptions().get(3).relation()).isEqualTo("CAUSED_BY");
        assertThat(result.exceptions().get(3).parentIndex()).isEqualTo(2);
        assertThat(result.exceptions().get(3).frames().getFirst().module()).isEqualTo("java.base");
        assertThat(result.exceptions().get(3).frames().get(1).line()).isEqualTo(22);
        assertThat(StackTraceAnalysisService.mainChain(result.exceptions())).extracting(ParsedException::simpleName)
                .containsExactly("InvoiceGenerationException", "InvoiceDataException", "NumberFormatException");
    }

    @Test
    void classifiesFramesByOrigin() {
        StackTraceParser.Result result = StackTraceParser.parse(CHAINED, List.of("com.acme"));
        var frames = result.exceptions().getFirst().frames();
        assertThat(frames.get(0).category()).isEqualTo(FrameClassifier.APPLICATION);
        assertThat(frames.get(0).application()).isTrue();
        assertThat(frames.get(1).category()).isEqualTo(FrameClassifier.SPRING);
        assertThat(FrameClassifier.classify("org.hibernate.Session", List.of())).isEqualTo(FrameClassifier.HIBERNATE);
        assertThat(FrameClassifier.classify("com.acme.Service$$SpringCGLIB$$0", List.of()))
                .isEqualTo(FrameClassifier.SPRING);
    }

    @Test
    void handlesTruncatedStackTraceWithoutHeader() {
        String truncated = """
                \tat com.acme.Client.call(Client.java:58)
                Caused by: java.net.SocketTimeoutException: Read timed out
                \tat java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:278)
                """;
        StackTraceParser.Result result = StackTraceParser.parse(truncated, List.of());
        assertThat(result.exceptions()).hasSize(2);
        assertThat(result.exceptions().getFirst().className()).isEqualTo("(exception desconhecida)");
        assertThat(result.warnings()).anyMatch(w -> w.contains("truncado"));
        assertThat(result.exceptions().get(1).parentIndex()).isZero();
    }

    @Test
    void keepsMultilineMessages() {
        String text = """
                org.postgresql.util.PSQLException: ERROR: duplicate key value
                  Detail: Key (email)=(x@y.dev) already exists.
                \tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2733)
                """;
        ParsedException ex = StackTraceParser.parse(text, List.of()).exceptions().getFirst();
        assertThat(ex.message()).contains("duplicate key").contains("Detail: Key");
        assertThat(ex.frames()).hasSize(1);
    }
}
