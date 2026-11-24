package com.example.httpreading.prompts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public final class PromptTemplates {
    private static final SpringTemplateEngine ENGINE = createEngine();
    private static final Map<String, String> TEMPLATES = new ConcurrentHashMap<>();

    private PromptTemplates() {
    }

    @SafeVarargs
    public static String render(String name, Map.Entry<String, Object>... parameters) {
        Context context = new Context();
        for (Map.Entry<String, Object> parameter : parameters) {
            context.setVariable(parameter.getKey(), parameter.getValue());
        }
        return ENGINE.process(name, context);
    }

    public static Map.Entry<String, Object> param(String name, Object value) {
        return Map.entry(name, value == null ? "null" : value);
    }

    private static SpringTemplateEngine createEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("prompts/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    public static String load(String name) {
        return TEMPLATES.computeIfAbsent(name, PromptTemplates::read);
    }

    private static String read(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream input = PromptTemplates.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing prompt template: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read prompt template: " + path, exception);
        }
    }
}
