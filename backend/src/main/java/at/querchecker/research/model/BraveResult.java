package at.querchecker.research.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Einzelnes Suchergebnis aus der Brave Search API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BraveResult {
    private String title;
    private String url;
    private String description;
    private List<String> extraSnippets;
}
