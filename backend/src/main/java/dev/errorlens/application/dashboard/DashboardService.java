package dev.errorlens.application.dashboard;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.application.execution.ExecutionMapper;
import dev.errorlens.application.execution.ExecutionViews.Summary;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.infrastructure.persistence.execution.ExecutionRepository;

/**
 * Indicadores do dashboard calculados diretamente no PostgreSQL a partir das execuções
 * persistidas. Nenhum número é fictício: sem execuções, os indicadores são zero.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final JdbcTemplate jdbc;
    private final ExecutionRepository executions;
    private final ExecutionMapper mapper;

    public DashboardService(JdbcTemplate jdbc, ExecutionRepository executions, ExecutionMapper mapper) {
        this.jdbc = jdbc;
        this.executions = executions;
        this.mapper = mapper;
    }

    public DashboardSummary summary(String rangeKey) {
        Range range = Range.of(rangeKey);
        Instant end = Instant.now();
        Instant start = end.minus(range.window());
        Totals current = totals(start, end);
        Totals previous = totals(start.minus(range.window()), start);
        List<Bucket> buckets = buckets(range, start, end);
        return new DashboardSummary(range.key(), start, end, current.total(), current.success(), current.failure(),
                current.degraded(), current.errorRate(), current.avgDurationMs(), current.p95DurationMs(),
                new Trends(pct(current.total(), previous.total()), pct(current.success(), previous.success()),
                        pct(current.failure(), previous.failure()),
                        previous.total() == 0 ? null : round(current.errorRate() - previous.errorRate()),
                        previous.avgDurationMs() == null || current.avgDurationMs() == null ? null
                                : pct(current.avgDurationMs(), previous.avgDurationMs())),
                new Sparklines(buckets.stream().map(Bucket::total).toList(),
                        buckets.stream().map(Bucket::success).toList(),
                        buckets.stream().map(Bucket::failure).toList(),
                        buckets.stream().map(b -> b.total() == 0 ? 0.0 : round(100.0 * b.failure() / b.total()))
                                .toList(),
                        buckets.stream().map(b -> b.avgDurationMs() == null ? 0.0 : b.avgDurationMs()).toList()));
    }

    public DashboardMetrics metrics(String rangeKey) {
        Range range = Range.of(rangeKey);
        Instant end = Instant.now();
        Instant start = end.minus(range.window());
        Timestamp from = Timestamp.from(start);
        Timestamp to = Timestamp.from(end);

        long total = jdbc.queryForObject("SELECT count(*) FROM executions WHERE started_at >= ? AND started_at < ?",
                Long.class, from, to);
        List<StatusCount> statuses = jdbc.query("""
                SELECT coalesce(http_status, 0) AS status, count(*) AS total FROM executions
                WHERE started_at >= ? AND started_at < ? GROUP BY coalesce(http_status, 0) ORDER BY status
                """, (rs, i) -> new StatusCount(rs.getInt("status"), rs.getLong("total"),
                percent(rs.getLong("total"), total)), from, to);
        long failures = jdbc.queryForObject("""
                SELECT count(*) FROM executions WHERE started_at >= ? AND started_at < ?
                  AND exception_class IS NOT NULL
                """, Long.class, from, to);
        List<ExceptionCount> exceptions = jdbc.query("""
                SELECT root_cause_class, exception_class, count(*) AS total FROM executions
                WHERE started_at >= ? AND started_at < ? AND exception_class IS NOT NULL
                GROUP BY root_cause_class, exception_class ORDER BY total DESC, exception_class LIMIT 8
                """, (rs, i) -> new ExceptionCount(simple(rs.getString("exception_class")),
                rs.getString("exception_class"), simple(rs.getString("root_cause_class")), rs.getLong("total"),
                percent(rs.getLong("total"), failures)), from, to);
        List<ScenarioCount> scenarios = jdbc.query("""
                SELECT e.scenario_code, s.name, count(*) AS total,
                       count(*) FILTER (WHERE e.outcome = 'FAILURE') AS failures
                FROM executions e JOIN scenarios s ON s.code = e.scenario_code
                WHERE e.started_at >= ? AND e.started_at < ?
                GROUP BY e.scenario_code, s.name ORDER BY total DESC, e.scenario_code LIMIT 8
                """, (rs, i) -> new ScenarioCount(rs.getString("scenario_code"), rs.getString("name"),
                rs.getLong("total"), rs.getLong("failures")), from, to);
        List<HistogramBin> histogram = jdbc.query("""
                SELECT bin, count(*) AS total FROM (
                  SELECT CASE WHEN duration_ms < 100 THEN 1 WHEN duration_ms < 300 THEN 2
                              WHEN duration_ms < 1000 THEN 3 WHEN duration_ms < 3000 THEN 4 ELSE 5 END AS bin
                  FROM executions WHERE started_at >= ? AND started_at < ? AND duration_ms IS NOT NULL) d
                GROUP BY bin ORDER BY bin
                """, (rs, i) -> new HistogramBin(rs.getInt("bin"), null, rs.getLong("total")), from, to);
        String[] labels = {"", "< 100 ms", "100-300 ms", "300 ms-1 s", "1-3 s", "> 3 s"};
        List<HistogramBin> fullHistogram = new ArrayList<>();
        for (int bin = 1; bin <= 5; bin++) {
            final int b = bin;
            long count = histogram.stream().filter(h -> h.bin() == b).mapToLong(HistogramBin::count).sum();
            fullHistogram.add(new HistogramBin(bin, labels[bin], count));
        }
        List<Summary> latest = executions.findAll(PageRequest.of(0, 10,
                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(mapper::summary).getContent();
        return new DashboardMetrics(range.key(), start, end, buckets(range, start, end), statuses, exceptions,
                scenarios, fullHistogram, latest);
    }

    private Totals totals(Instant start, Instant end) {
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE outcome = 'SUCCESS') AS success,
                       count(*) FILTER (WHERE outcome = 'FAILURE') AS failure,
                       count(*) FILTER (WHERE outcome = 'DEGRADED') AS degraded,
                       avg(duration_ms)::float8 AS avg_ms,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)::float8 AS p95_ms
                FROM executions WHERE started_at >= ? AND started_at < ?
                """, (rs, i) -> {
            long total = rs.getLong("total");
            long failure = rs.getLong("failure");
            Double avg = (Double) rs.getObject("avg_ms", Double.class);
            Double p95 = (Double) rs.getObject("p95_ms", Double.class);
            return new Totals(total, rs.getLong("success"), failure, rs.getLong("degraded"),
                    total == 0 ? 0.0 : round(100.0 * failure / total),
                    avg == null ? null : round(avg), p95 == null ? null : round(p95));
        }, Timestamp.from(start), Timestamp.from(end));
    }

    private List<Bucket> buckets(Range range, Instant start, Instant end) {
        double width = range.bucket().toSeconds();
        return jdbc.query("""
                WITH buckets AS (
                  SELECT generate_series(?::timestamptz, ?::timestamptz - make_interval(secs => ?),
                                         make_interval(secs => ?)) AS bucket_start)
                SELECT b.bucket_start,
                       count(e.id) AS total,
                       count(e.id) FILTER (WHERE e.outcome = 'SUCCESS') AS success,
                       count(e.id) FILTER (WHERE e.outcome = 'FAILURE') AS failure,
                       count(e.id) FILTER (WHERE e.outcome = 'DEGRADED') AS degraded,
                       avg(e.duration_ms)::float8 AS avg_ms
                FROM buckets b
                LEFT JOIN executions e ON e.started_at >= b.bucket_start
                     AND e.started_at < b.bucket_start + make_interval(secs => ?)
                GROUP BY b.bucket_start ORDER BY b.bucket_start
                """, (rs, i) -> {
            Double avg = (Double) rs.getObject("avg_ms", Double.class);
            return new Bucket(rs.getTimestamp("bucket_start").toInstant(), rs.getLong("total"),
                    rs.getLong("success"), rs.getLong("failure"), rs.getLong("degraded"),
                    avg == null ? null : round(avg));
        }, Timestamp.from(start), Timestamp.from(end), width, width, width);
    }

    private static Double pct(double current, double previous) {
        if (previous == 0) {
            return null;
        }
        return round(100.0 * (current - previous) / previous);
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : round(100.0 * part / total);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String simple(String className) {
        return className == null ? null : className.substring(className.lastIndexOf('.') + 1);
    }

    record Range(String key, Duration window, Duration bucket) {
        static Range of(String key) {
            String k = key == null ? "24h" : key.toLowerCase(Locale.ROOT);
            return switch (k) {
                case "1h" -> new Range(k, Duration.ofHours(1), Duration.ofMinutes(5));
                case "24h" -> new Range(k, Duration.ofHours(24), Duration.ofHours(1));
                case "7d" -> new Range(k, Duration.ofDays(7), Duration.ofHours(6));
                case "30d" -> new Range(k, Duration.of(30, ChronoUnit.DAYS), Duration.ofDays(1));
                default -> throw new InvalidInputException("INVALID_RANGE", "Período inválido. Use 1h, 24h, 7d ou 30d.");
            };
        }
    }

    private record Totals(long total, long success, long failure, long degraded, double errorRate,
                          Double avgDurationMs, Double p95DurationMs) {
    }

    public record Trends(Double totalChangePct, Double successChangePct, Double failureChangePct,
                         Double errorRateDeltaPp, Double avgDurationChangePct) {
    }

    public record Sparklines(List<Long> total, List<Long> success, List<Long> failure, List<Double> errorRate,
                             List<Double> avgDuration) {
    }

    public record DashboardSummary(String range, Instant from, Instant to, long total, long success, long failure,
                                   long degraded, double errorRate, Double avgDurationMs, Double p95DurationMs,
                                   Trends trends, Sparklines sparklines) {
    }

    public record Bucket(Instant start, long total, long success, long failure, long degraded, Double avgDurationMs) {
    }

    public record StatusCount(int status, long count, double percent) {
    }

    public record ExceptionCount(String exception, String className, String rootCause, long count, double percent) {
    }

    public record ScenarioCount(String code, String name, long count, long failures) {
    }

    public record HistogramBin(int bin, String label, long count) {
    }

    public record DashboardMetrics(String range, Instant from, Instant to, List<Bucket> timeseries,
                                   List<StatusCount> statusDistribution, List<ExceptionCount> topExceptions,
                                   List<ScenarioCount> topScenarios, List<HistogramBin> durationHistogram,
                                   List<Summary> latestExecutions) {
    }
}
