package dev.errorlens.infrastructure.security;

/**
 * Roles das identidades locais de laboratório.
 */
public final class Roles {

    /** Administração do laboratório (reset, exclusão de histórico). */
    public static final String ADMIN = "ADMIN";
    /** Acesso aos endpoints de diagnóstico, execução de cenários e analisadores. */
    public static final String ANALYST = "ANALYST";
    /** Somente leitura do dashboard e catálogo. */
    public static final String VIEWER = "VIEWER";
    /** Acesso ao relatório de auditoria protegido (cenários C01-C03). */
    public static final String AUDITOR = "AUDITOR";
    /** Identidade técnica do Scenario Runner para os endpoints de laboratório. */
    public static final String OPERATOR = "OPERATOR";

    private Roles() {
    }
}
