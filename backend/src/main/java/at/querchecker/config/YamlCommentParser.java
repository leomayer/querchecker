package at.querchecker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parst YAML-Dateien und extrahiert Inline-Kommentare pro Schlüssel-Pfad.
 *
 * Beispiel:
 *   api-key: BRAVE_PLACEHOLDER # Brave Search API-Key — https://api.search.brave.com
 *
 * Ergibt: "querchecker.api.limits.brave.api-key" → "Brave Search API-Key — https://api.search.brave.com"
 */
@Slf4j
@Component
public class YamlCommentParser {

    private static final Pattern KEY_VALUE_COMMENT = Pattern.compile(
        "^(\\s*)(\\S+):\\s*(?:\\S.*?)\\s*#\\s*(.+)$"
    );
    private static final Pattern KEY_ONLY = Pattern.compile(
        "^(\\s*)(\\S+):.*$"
    );

    /**
     * Liest eine YAML-Datei und gibt eine Map zurück:
     * YAML-Pfad (dot-separated) → Inline-Kommentar.
     *
     * Nur Leaf-Nodes mit Inline-Kommentar werden zurückgegeben.
     */
    public Map<String, String> parseComments(Path yamlFile) {
        Map<String, String> comments = new LinkedHashMap<>();
        if (!Files.exists(yamlFile)) {
            log.warn("[YamlCommentParser] Datei nicht gefunden: {}", yamlFile);
            return comments;
        }

        try {
            List<String> lines = Files.readAllLines(yamlFile);
            Deque<IndentKey> stack = new ArrayDeque<>();

            for (String line : lines) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }

                Matcher keyMatcher = KEY_ONLY.matcher(line);
                if (!keyMatcher.matches()) {
                    continue;
                }

                int indent = keyMatcher.group(1).length();
                String key = keyMatcher.group(2);

                // Pop stack entries with indent >= current (siblings or deeper)
                while (!stack.isEmpty() && stack.peek().indent >= indent) {
                    stack.pop();
                }

                stack.push(new IndentKey(indent, key));

                // Check for inline comment
                Matcher commentMatcher = KEY_VALUE_COMMENT.matcher(line);
                if (commentMatcher.matches()) {
                    String comment = commentMatcher.group(3).strip();
                    String path = buildPath(stack);
                    comments.put(path, comment);
                }
            }
        } catch (IOException e) {
            log.error("[YamlCommentParser] Fehler beim Lesen: {}", yamlFile, e);
        }

        return comments;
    }

    private String buildPath(Deque<IndentKey> stack) {
        List<String> parts = new ArrayList<>();
        for (IndentKey entry : stack) {
            parts.add(entry.key);
        }
        Collections.reverse(parts);
        return String.join(".", parts);
    }

    private record IndentKey(int indent, String key) {}
}
