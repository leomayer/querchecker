package at.querchecker.wh.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Inoffizielle Willhaben-JSON-API – Response-Strukturen.
 *
 * Alle Klassen sind in dieser Datei gebündelt, damit Anpassungen
 * (z.B. wenn Willhaben die Struktur ändert) an einem einzigen Ort erfolgen.
 *
 * Beispiel-Request:
 *   GET https://www.willhaben.at/webapi/iad/search/atz/seo/kaufen-und-verkaufen/marktplatz
 *       ?keyword=rtx+4070&rows=30&page=1
 *
 * Relevante Attribute-Namen (Stand 2025):
 *   HEADING      → Titel des Inserats
 *   PRICE        → Preis als Zahl (z.B. "350"), fehlt bei "Preis auf Anfrage"
 *   LOCATION     → Standort
 *   PUBLISHED    → ISO-Datum des Einstellzeitpunkts
 */
public final class WhApiResponse {

    private WhApiResponse() {}

    // -------------------------------------------------------------------------
    // Root
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Root {
        private AdvertSummaryList advertSummaryList;
        private NavigatorList navigatorList;
    }

    // -------------------------------------------------------------------------
    // AdvertSummaryList
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvertSummaryList {
        private List<Advert> advertSummary;
    }

    // -------------------------------------------------------------------------
    // Advert
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Advert {

        /** Willhaben-interne ID – stabiler Anker für Upsert-Logik. */
        private String id;

        /** Freitext-Beschreibung des Inserats. */
        private String description;

        @JsonProperty("attributes")
        private AttributesWrapper attributes;

        private List<Link> links;

        /**
         * Liest den ersten Wert eines benannten Attributs aus.
         *
         * @param name Attributname, z.B. "HEADING", "PRICE", "LOCATION"
         * @return erster Wert oder {@code null} wenn nicht vorhanden
         */
        public String getAttribute(String name) {
            if (attributes == null || attributes.getAttribute() == null) return null;
            return attributes.getAttribute().stream()
                    .filter(a -> name.equals(a.getName()))
                    .findFirst()
                    .map(a -> a.getValues() != null && !a.getValues().isEmpty()
                            ? a.getValues().get(0)
                            : null)
                    .orElse(null);
        }

        /**
         * Gibt den Self-Link des Inserats zurück (direkte URL auf willhaben.at).
         */
        public String getSelfLink() {
            if (links == null) return null;
            return links.stream()
                    .filter(l -> "self".equals(l.getRel()))
                    .findFirst()
                    .map(Link::getHref)
                    .orElse(null);
        }
    }

    // -------------------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributesWrapper {
        /** Willhaben liefert das Array unter dem Key "attribute" (Singular). */
        private List<Attribute> attribute;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attribute {
        private String name;
        private List<String> values;
    }

    // -------------------------------------------------------------------------
    // Link
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Link {
        private String rel;
        private String href;
    }

    // -------------------------------------------------------------------------
    // Navigator (Kategorie- und Standort-Facetten)
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NavigatorList {
        private List<Navigator> navigator;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Navigator {
        private String id;
        private String label;
        private List<NavigatorValue> navigatorValue;
    }

    /**
     * Ein einzelner Filterwert im Navigator, z.B. eine Kategorie oder ein Bundesland.
     * Enthält optional eine subNavigatorList für die nächste Ebene.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NavigatorValue {
        private String label;
        /** z.B. "ATTRIBUTE_TREE" für Kategorien, "areaId" für Standorte */
        private String urlParameterName;
        /** Der eigentliche ID-Wert, z.B. "5882" oder "900" */
        private String urlParameterValue;
        /** Nächste Ebene – null wenn kein Kindelement vorhanden */
        private NavigatorList subNavigatorList;
    }
}
