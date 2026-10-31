package dev.errorlens.application.analyzer;

import java.util.List;
import java.util.Map;

/**
 * Classifica frames de stack trace por origem (aplicação, Spring, Hibernate, JDK, ...).
 */
public final class FrameClassifier {

    public static final String APPLICATION = "APPLICATION";
    public static final String SPRING = "SPRING";
    public static final String HIBERNATE = "HIBERNATE";
    public static final String JDK = "JDK";
    public static final String JAKARTA = "JAKARTA";
    public static final String SERVLET_CONTAINER = "SERVLET_CONTAINER";
    public static final String DATABASE_DRIVER = "DATABASE_DRIVER";
    public static final String JACKSON = "JACKSON";
    public static final String RESILIENCE4J = "RESILIENCE4J";
    public static final String LIBRARY = "LIBRARY";

    private static final Map<String, String> PREFIXES = new java.util.LinkedHashMap<>();

    static {
        PREFIXES.put("org.springframework.", SPRING);
        PREFIXES.put("org.hibernate.", HIBERNATE);
        PREFIXES.put("java.", JDK);
        PREFIXES.put("javax.", JDK);
        PREFIXES.put("jdk.", JDK);
        PREFIXES.put("sun.", JDK);
        PREFIXES.put("com.sun.", JDK);
        PREFIXES.put("jakarta.", JAKARTA);
        PREFIXES.put("org.apache.catalina.", SERVLET_CONTAINER);
        PREFIXES.put("org.apache.coyote.", SERVLET_CONTAINER);
        PREFIXES.put("org.apache.tomcat.", SERVLET_CONTAINER);
        PREFIXES.put("org.eclipse.jetty.", SERVLET_CONTAINER);
        PREFIXES.put("io.undertow.", SERVLET_CONTAINER);
        PREFIXES.put("org.postgresql.", DATABASE_DRIVER);
        PREFIXES.put("com.zaxxer.hikari.", DATABASE_DRIVER);
        PREFIXES.put("com.mysql.", DATABASE_DRIVER);
        PREFIXES.put("oracle.jdbc.", DATABASE_DRIVER);
        PREFIXES.put("net.ttddyy.dsproxy.", DATABASE_DRIVER);
        PREFIXES.put("com.fasterxml.jackson.", JACKSON);
        PREFIXES.put("tools.jackson.", JACKSON);
        PREFIXES.put("io.github.resilience4j.", RESILIENCE4J);
        for (String lib : List.of("io.micrometer.", "ch.qos.logback.", "org.slf4j.", "org.apache.", "net.bytebuddy.",
                "org.aspectj.", "io.opentelemetry.", "reactor.", "io.netty.", "kotlin.", "kotlinx.", "org.junit.",
                "org.mockito.", "com.google.", "org.flywaydb.", "io.swagger.", "org.springdoc.", "okhttp3.",
                "org.openpdf.", "lombok.")) {
            PREFIXES.put(lib, LIBRARY);
        }
    }

    private FrameClassifier() {
    }

    /**
     * Quando {@code applicationPackages} é informado, apenas esses pacotes são considerados aplicação;
     * caso contrário, qualquer pacote não reconhecido como framework/biblioteca é tratado como aplicação.
     */
    public static String classify(String className, List<String> applicationPackages) {
        if (className.contains("$$SpringCGLIB$$") || className.contains("$$EnhancerBySpringCGLIB$$")
                || className.startsWith("jdk.proxy") || className.startsWith("com.sun.proxy")
                || className.contains("$Proxy")) {
            return SPRING;
        }
        if (applicationPackages != null && !applicationPackages.isEmpty()) {
            for (String pkg : applicationPackages) {
                String prefix = pkg.endsWith(".") ? pkg : pkg + ".";
                if (!pkg.isBlank() && (className.startsWith(prefix) || className.equals(pkg))) {
                    return APPLICATION;
                }
            }
        }
        for (Map.Entry<String, String> entry : PREFIXES.entrySet()) {
            if (className.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return applicationPackages == null || applicationPackages.isEmpty() ? APPLICATION : LIBRARY;
    }
}
