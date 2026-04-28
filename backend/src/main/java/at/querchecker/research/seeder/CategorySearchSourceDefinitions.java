package at.querchecker.research.seeder;

import static at.querchecker.research.entity.SourceType.*;
import static java.util.Map.entry;

import at.querchecker.research.entity.SourceType;
import java.util.List;
import java.util.Map;

public final class CategorySearchSourceDefinitions {

  private CategorySearchSourceDefinitions() {}

  // record: domain, label, type, lookupEnabled, queryExcludes, searchResultCount
  // coreFields entfällt — wird von CategorySpecPreferenceSeeder (P2b) gesetzt
  public record SourceConfig(
    String domain,
    String label,
    SourceType type,
    boolean lookupEnabled,
    List<String> queryExcludes,
    int searchResultCount
  ) {}

  private static final int SNIPPETS_COUNT = 10; // alle Snippets ans LLM

  // Icecat-Standardausschlüsse — werden vom Seeder in category_search_source.query_excludes geschrieben
  // (gleiche Architektur wie SYSTEM_FIELDS in CategorySpecPreferenceSeeder → DB-Spalte)
  private static final List<String> ICECAT_EXCLUDES = List.of(
    "-filetype:pdf",
    "-\"user guide\"",
    "-\"quick start\"",
    "-\"Bedienungsanleitung\""
  );

  // FlatpanelsHD — nur TV-Database-Seiten, keine Artikel/Reviews
  private static final List<String> FLATPANELSHD_EXCLUDES = List.of(
    "-review.php",
    "-news.php",
    "-article.php",
    "-focus.php"
  );

  // Kategorienamen MÜSSEN exakt mit WhCategory.name übereinstimmen.
  // Mehrfacheinträge (z.B. "Tablets" auf level=1 und level=2) werden alle geseedet.
  // inheritFromParent wird automatisch aus dem Level der Kategorie bestimmt (level=1 → true).
  // System-Pflichtfelder sind NICHT hier — die kommen von CategorySpecPreferenceSeeder (P2b)
  public static final Map<String, List<SourceConfig>> CONFIGS = Map.ofEntries(
    // --- Lookup aktiviert ---
    entry(
      "Notebooks",
      List.of(
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("notebookcheck.net", "Notebookcheck", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Netbooks",
      List.of(
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("notebookcheck.net", "Notebookcheck", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Smartphones / Handys",
      List.of(
        // level=1 → Apple, Samsung etc. erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("gsmarena.com", "GSMArena", GSMARENA, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Tablets",
      List.of(
        // level=1 + level=2 – alle Treffer werden geseedet
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("gsmarena.com", "GSMArena", GSMARENA, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Monitore",
      List.of(
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig(
          "flatpanelshd.com",
          "FlatpanelsHD",
          FLATPANELSHD,
          true,
          FLATPANELSHD_EXCLUDES,
          SNIPPETS_COUNT
        ),
        new SourceConfig("prad.de", "PRAD", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Beamer / Diaprojektoren",
      List.of(
        // level=1 → Beamer (678) + Diaprojektoren (679) + Dias/Zubehör (680) erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("flatpanelshd.com", "FlatpanelsHD", FLATPANELSHD, false, FLATPANELSHD_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("prad.de", "PRAD", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Beamer",
      List.of(
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("flatpanelshd.com", "FlatpanelsHD", FLATPANELSHD, false, FLATPANELSHD_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("prad.de", "PRAD", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Drucker",
      List.of(
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT),
        new SourceConfig("druckerchannel.de", "Druckerchannel", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "PC-Komponenten",
      List.of(
        // level=1 → Grafikkarten, CPUs etc. erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Netzwerke",
      List.of(
        // level=1 → Server, Router etc. erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Kameras / Camcorder",
      List.of(
        // level=1 → Digitalkameras, Systemkameras etc. erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Fernseher",
      List.of(
        // level=1 → LCD-Fernseher, LED etc. erben
        new SourceConfig(
          "flatpanelshd.com",
          "FlatpanelsHD",
          FLATPANELSHD,
          true,
          FLATPANELSHD_EXCLUDES,
          SNIPPETS_COUNT
        ),
        new SourceConfig("whathifi.com", "What Hi-Fi", GENERIC, true, null, SNIPPETS_COUNT),
        new SourceConfig("displayspecifications.com", "DisplaySpecifications", GENERIC, true, null, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Konsolen",
      List.of(
        // level=1 → Playstation, Xbox, Nintendo erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    ),
    // --- Lookup aktiviert, keine System-Pflichtfelder (generische Extraktion) ---
    entry(
      "Adapter / Kabel",
      List.of(
        // level=1
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Software",
      List.of(
        // level=1
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    ),
    entry(
      "Spiele",
      List.of(
        // level=1 → PS/Xbox/PC-Spiele erben
        new SourceConfig("icecat.biz", "Icecat", ICECAT, true, ICECAT_EXCLUDES, SNIPPETS_COUNT)
      )
    )
  );
}
