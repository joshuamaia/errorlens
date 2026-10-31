package dev.errorlens.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Simula dependências externas (score de crédito, perfil, pagamentos, recomendações) com
 * comportamentos configuráveis por chave de simulação: resposta imediata, lenta, erros 500/503,
 * falha nas primeiras N tentativas e indisponibilidade controlada.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DependencySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DependencySimulatorApplication.class, args);
    }
}
