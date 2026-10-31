package dev.errorlens.application.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.application.execution.ExecutionQueryService;
import dev.errorlens.application.execution.ExecutionViews.Detail;
import dev.errorlens.application.execution.ExecutionViews.DiagnosisView;
import dev.errorlens.application.execution.ExecutionViews.StepView;
import dev.errorlens.application.execution.ExecutionViews.Summary;
import dev.errorlens.application.execution.ExecutionViews.Timeline;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.infrastructure.observability.SensitiveDataSanitizer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exporta relatórios técnicos de investigação em JSON, Markdown e PDF. Os dados já são
 * persistidos sanitizados; o conteúdo exportado é sanitizado novamente por segurança.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final ExecutionQueryService queries;
    private final JsonMapper jsonMapper;

    public ReportService(ExecutionQueryService queries, JsonMapper jsonMapper) {
        this.queries = queries;
        this.jsonMapper = jsonMapper;
    }

    public ReportFile export(Long id, String format) {
        Detail detail = queries.detail(id);
        Timeline timeline = queries.timeline(id);
        String base = "errorlens-execucao-" + id;
        return switch (format == null ? "json" : format.toLowerCase(Locale.ROOT)) {
            case "json" -> new ReportFile(base + ".json", "application/json",
                    jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report(detail, timeline)));
            case "markdown", "md" -> new ReportFile(base + ".md", "text/markdown; charset=utf-8",
                    markdown(detail, timeline).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "pdf" -> new ReportFile(base + ".pdf", "application/pdf", pdf(detail, timeline));
            default -> throw new InvalidInputException("INVALID_FORMAT", "Formato inválido. Use json, markdown ou pdf.");
        };
    }

    Map<String, Object> report(Detail d, Timeline timeline) {
        Summary s = d.summary();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("relatorio", "ErrorLens - Relatório de investigação");
        report.put("geradoEm", Instant.now().toString());
        report.put("identificacao", Map.of(
                "executionId", s.id(), "correlationId", s.correlationId(),
                "traceId", s.traceId() == null ? "" : s.traceId(),
                "cenario", s.scenarioCode() + " - " + s.scenarioName(), "modo", s.mode()));
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("endpoint", s.httpMethod() + " " + s.endpoint());
        resumo.put("statusHttp", s.httpStatus());
        resumo.put("statusEsperado", s.expectedStatus());
        resumo.put("resultado", s.outcome());
        resumo.put("duracaoMs", s.durationMs());
        resumo.put("inicio", s.startedAt().toString());
        resumo.put("errorCode", s.errorCode());
        report.put("resumo", resumo);
        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("classe", s.exceptionClass());
        exception.put("mensagem", sanitize(d.exceptionMessage()));
        exception.put("rootCause", s.rootCauseClass());
        exception.put("rootCauseMensagem", sanitize(d.rootCauseMessage()));
        exception.put("cadeia", d.exceptionChain());
        report.put("exception", exception);
        report.put("timeline", timeline.steps().stream().map(step -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", step.seq());
            m.put("camada", step.layerLabel());
            m.put("etapa", step.name());
            m.put("status", step.status());
            m.put("offsetMs", step.offsetMicros() / 1000.0);
            m.put("duracaoMs", step.durationMicros() == null ? null : step.durationMicros() / 1000.0);
            m.put("detalhe", sanitize(step.detail()));
            m.put("erro", step.errorClass());
            return m;
        }).toList());
        report.put("tentativas", d.attempts());
        report.put("verificacao", d.verification());
        report.put("diagnosticos", d.diagnoses());
        report.put("recomendacoes", d.diagnoses().stream().flatMap(x -> x.suggestions().stream()).distinct().toList());
        report.put("aviso", DiagnosisEngine.DISCLAIMER);
        return report;
    }

    String markdown(Detail d, Timeline timeline) {
        Summary s = d.summary();
        StringBuilder md = new StringBuilder();
        md.append("# ErrorLens — Relatório de investigação\n\n");
        md.append("> Execução #").append(s.id()).append(" • gerado em ").append(FMT.format(Instant.now()))
                .append("\n\n");
        md.append("## Identificação\n\n| Campo | Valor |\n|---|---|\n");
        row(md, "Cenário", s.scenarioCode() + " — " + s.scenarioName());
        row(md, "Modo", s.mode());
        row(md, "CorrelationId", "`" + s.correlationId() + "`");
        row(md, "TraceId", s.traceId() == null ? "—" : "`" + s.traceId() + "`");
        row(md, "Início", FMT.format(s.startedAt()));
        md.append("\n## Resumo\n\n| Campo | Valor |\n|---|---|\n");
        row(md, "Endpoint", "`" + s.httpMethod() + " " + s.endpoint() + "`");
        row(md, "Status HTTP", s.httpStatus() + " (esperado " + s.expectedStatus() + ")");
        row(md, "Resultado", s.outcome());
        row(md, "Duração", s.durationMs() + " ms");
        row(md, "errorCode", s.errorCode() == null ? "—" : s.errorCode());
        md.append("\n## Exception\n\n");
        if (s.exceptionClass() == null) {
            md.append("Nenhuma exception registrada.\n");
        } else {
            md.append("- **Exception:** `").append(s.exceptionClass()).append("`\n");
            md.append("- **Mensagem:** ").append(sanitize(d.exceptionMessage())).append('\n');
            md.append("- **Root cause:** `").append(s.rootCauseClass()).append("` — ")
                    .append(sanitize(d.rootCauseMessage())).append('\n');
            md.append("\n**Cadeia de causas:**\n\n");
            int level = 0;
            for (Map<String, Object> link : d.exceptionChain()) {
                md.append(level++ == 0 ? "1. " : "1. Caused by: ").append('`').append(link.get("className"))
                        .append("`: ").append(sanitize(String.valueOf(link.get("message")))).append('\n');
            }
        }
        md.append("\n## Timeline\n\n| # | Camada | Etapa | Status | Início (ms) | Duração (ms) |\n|---|---|---|---|---|---|\n");
        for (StepView step : timeline.steps()) {
            md.append("| ").append(step.seq()).append(" | ").append(step.layerLabel()).append(" | ")
                    .append("  ".repeat(Math.max(0, step.depth()))).append(escape(step.name())).append(" | ")
                    .append(step.status()).append(" | ").append(String.format(Locale.ROOT, "%.1f", step.offsetMicros() / 1000.0))
                    .append(" | ").append(step.durationMicros() == null ? "—"
                            : String.format(Locale.ROOT, "%.2f", step.durationMicros() / 1000.0)).append(" |\n");
        }
        if (d.verification() != null) {
            md.append("\n## Verificação automática\n\n- **").append(d.verification().get("name")).append(":** ")
                    .append(d.verification().get("conclusion")).append('\n');
        }
        md.append("\n## Diagnóstico\n\n");
        if (d.diagnoses().isEmpty()) {
            md.append("Nenhuma regra de diagnóstico correspondeu às evidências desta execução.\n");
        }
        for (DiagnosisView dx : d.diagnoses()) {
            md.append("### ").append(dx.rank()).append(". ").append(dx.title()).append(" (confiança ")
                    .append(dx.confidence()).append(", ").append(dx.score()).append("/100)\n\n");
            md.append(dx.description()).append("\n\n**Evidências:**\n\n");
            dx.evidences().forEach(e -> md.append("- ").append(e).append('\n'));
            md.append("\n**Possíveis causas:**\n\n");
            dx.possibleCauses().forEach(e -> md.append("- ").append(e).append('\n'));
            md.append("\n**Sugestões de investigação:**\n\n");
            dx.suggestions().forEach(e -> md.append("- ").append(e).append('\n'));
            md.append("\n**Possíveis correções:**\n\n");
            dx.fixes().forEach(e -> md.append("- ").append(e).append('\n'));
            if (dx.codeExample() != null) {
                md.append("\n```java\n").append(dx.codeExample()).append("\n```\n");
            }
            md.append('\n');
        }
        md.append("\n---\n_").append(DiagnosisEngine.DISCLAIMER).append("_\n");
        return md.toString();
    }

    byte[] pdf(Detail d, Timeline timeline) {
        Summary s = d.summary();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();
        Font title = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font h2 = new Font(Font.HELVETICA, 13, Font.BOLD);
        Font body = new Font(Font.HELVETICA, 9);
        Font mono = new Font(Font.COURIER, 8);
        Font bold = new Font(Font.HELVETICA, 9, Font.BOLD);

        document.add(new Paragraph(pdfText("ErrorLens — Relatório de investigação"), title));
        document.add(new Paragraph(pdfText("Execução #" + s.id() + " • gerado em " + FMT.format(Instant.now())), body));
        document.add(Chunk.NEWLINE);

        section(document, "Identificação e resumo", h2);
        PdfPTable table = new PdfPTable(new float[] {1.2f, 3f});
        table.setWidthPercentage(100);
        cell(table, "Cenário", bold);
        cell(table, s.scenarioCode() + " — " + s.scenarioName() + " (modo " + s.mode() + ")", body);
        cell(table, "CorrelationId", bold);
        cell(table, s.correlationId(), mono);
        cell(table, "TraceId", bold);
        cell(table, s.traceId() == null ? "—" : s.traceId(), mono);
        cell(table, "Endpoint", bold);
        cell(table, s.httpMethod() + " " + s.endpoint(), mono);
        cell(table, "Status HTTP", bold);
        cell(table, s.httpStatus() + " (esperado " + s.expectedStatus() + ") • " + s.outcome(), body);
        cell(table, "Duração", bold);
        cell(table, s.durationMs() + " ms", body);
        cell(table, "Exception", bold);
        cell(table, s.exceptionClass() == null ? "—" : s.exceptionClass(), mono);
        cell(table, "Root cause", bold);
        cell(table, s.rootCauseClass() == null ? "—" : s.rootCauseClass() + ": " + sanitize(d.rootCauseMessage()), mono);
        document.add(table);

        section(document, "Timeline", h2);
        PdfPTable steps = new PdfPTable(new float[] {0.4f, 1.3f, 3.2f, 0.9f, 0.9f});
        steps.setWidthPercentage(100);
        for (String header : List.of("#", "Camada", "Etapa", "Status", "Duração (ms)")) {
            cell(steps, header, bold);
        }
        for (StepView step : timeline.steps()) {
            cell(steps, String.valueOf(step.seq()), body);
            cell(steps, step.layerLabel(), body);
            cell(steps, "  ".repeat(Math.max(0, step.depth())) + step.name(), body);
            cell(steps, step.status(), body);
            cell(steps, step.durationMicros() == null ? "—"
                    : String.format(Locale.ROOT, "%.2f", step.durationMicros() / 1000.0), body);
        }
        document.add(steps);

        if (d.verification() != null) {
            section(document, "Verificação automática", h2);
            document.add(new Paragraph(pdfText(d.verification().get("name") + ": " + d.verification().get("conclusion")),
                    body));
        }

        section(document, "Diagnóstico", h2);
        if (d.diagnoses().isEmpty()) {
            document.add(new Paragraph(pdfText("Nenhuma regra correspondeu às evidências desta execução."), body));
        }
        for (DiagnosisView dx : d.diagnoses()) {
            document.add(new Paragraph(pdfText(dx.rank() + ". " + dx.title() + " — confiança " + dx.confidence()
                    + " (" + dx.score() + "/100)"), bold));
            document.add(new Paragraph(pdfText(dx.description()), body));
            list(document, "Evidências", dx.evidences(), bold, body);
            list(document, "Sugestões de investigação", dx.suggestions(), bold, body);
            list(document, "Possíveis correções", dx.fixes(), bold, body);
            document.add(Chunk.NEWLINE);
        }
        document.add(new Paragraph(pdfText(DiagnosisEngine.DISCLAIMER), new Font(Font.HELVETICA, 8, Font.ITALIC)));
        document.close();
        return out.toByteArray();
    }

    private static void section(Document document, String text, Font font) {
        Paragraph p = new Paragraph(pdfText(text), font);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        document.add(p);
    }

    private static void list(Document document, String title, List<String> items, Font bold, Font body) {
        if (items.isEmpty()) {
            return;
        }
        document.add(new Paragraph(pdfText(title + ":"), bold));
        for (String item : items) {
            document.add(new Paragraph(pdfText("  • " + item), body));
        }
    }

    private static void cell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(pdfText(text), font));
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    /**
     * Fontes padrão do PDF usam Windows-1252: caracteres fora dessa codificação são substituídos.
     */
    static String pdfText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("→", "->").replace("—", "-").replace("“", "\"").replace("”", "\"");
        CharsetEncoder encoder = Charset.forName("windows-1252").newEncoder();
        StringBuilder sb = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            sb.append(encoder.canEncode(ch) ? ch : "?");
        });
        return sb.toString();
    }

    private static String sanitize(String value) {
        return SensitiveDataSanitizer.text(value, 4000);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private static void row(StringBuilder md, String key, String value) {
        md.append("| ").append(key).append(" | ").append(escape(value)).append(" |\n");
    }

    public record ReportFile(String filename, String contentType, byte[] content) {
    }
}
