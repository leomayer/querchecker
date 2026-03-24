package at.querchecker.research.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generisches Suchergebnis — quellunabhängige Abstraktion über BraveResult.
 * Wird in P8c als Ersatz für BraveResult eingeführt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String title;
    private String url;
    private String description;
    private List<String> extraSnippets;
}
