package dev.errorlens.application.analyzer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import dev.errorlens.domain.exception.ResourceNotFoundException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exemplos prontos (stack traces, respostas HTTP e logs) empacotados a partir da pasta
 * {@code examples/} do repositório.
 */
@Component
public class ExampleCatalog {

    private static final String BASE = "examples/";

    private final List<ExampleDescriptor> index;

    public ExampleCatalog(JsonMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource(BASE + "index.json").getInputStream()) {
            this.index = mapper.readValue(in, new TypeReference<>() {
            });
        }
    }

    public List<ExampleDescriptor> list(String type) {
        return index.stream().filter(e -> type == null || type.isBlank() || e.type().equalsIgnoreCase(type)).toList();
    }

    public Example get(String id) {
        ExampleDescriptor descriptor = index.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("EXAMPLE_NOT_FOUND", "Exemplo não encontrado."));
        try (InputStream in = new ClassPathResource(BASE + descriptor.file()).getInputStream()) {
            return new Example(descriptor, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ResourceNotFoundException("EXAMPLE_NOT_FOUND", "Arquivo de exemplo indisponível.");
        }
    }

    public record ExampleDescriptor(String id, String type, String title, String description, String file) {
    }

    public record Example(ExampleDescriptor descriptor, String content) {
    }
}
