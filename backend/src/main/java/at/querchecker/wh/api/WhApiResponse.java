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
 * Relevante Attribute-Namen (Stand 2026):
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
        /** Gesamtanzahl der Treffer auf Willhaben (root-level). */
        private Integer rowsFound;
        /** Seit 2026: Navigatoren sind nach Gruppen (z.B. "Kategorie", "Standort") gegliedert. */
        private List<NavigatorGroup> navigatorGroups;
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

        private AdvertImageList advertImageList;

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
         * Gibt alle Werte eines benannten Attributs zurück (z.B. ALL_IMAGE_URLS).
         */
        public List<String> getAttributeValues(String name) {
            if (attributes == null || attributes.getAttribute() == null) return List.of();
            return attributes.getAttribute().stream()
                    .filter(a -> name.equals(a.getName()))
                    .findFirst()
                    .map(a -> a.getValues() != null ? a.getValues() : List.<String>of())
                    .orElse(List.of());
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

        /**
         * Gibt die Thumbnail-URL des ersten Bildes zurück (aus advertImageList).
         */
        public String getThumbnailUrl() {
            if (advertImageList == null || advertImageList.getAdvertImage() == null
                    || advertImageList.getAdvertImage().isEmpty()) return null;
            return advertImageList.getAdvertImage().get(0).getThumbnailImageUrl();
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
    // AdvertImageList
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvertImageList {
        private List<AdvertImage> advertImage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvertImage {
        private String mainImageUrl;
        private String thumbnailImageUrl;
        /** Original full-resolution image (no suffix, e.g. "…/1234_-5678.jpg"). */
        private String referenceImageUrl;
    }

    // -------------------------------------------------------------------------
    // Next.js data endpoint response  (_next/data/{buildId}/iad/{seoUrl}.json)
    // Top-level: { pageProps: { advertDetails: Advert } }
    // -------------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NextDataRoot {
        private NextDataPageProps pageProps;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NextDataPageProps {
        private Advert advertDetails;
    }

    // -------------------------------------------------------------------------
    // Navigator (Kategorie- und Standort-Facetten) – neue Struktur ab 2026
    // -------------------------------------------------------------------------

    /** Eine thematische Gruppe von Navigatoren, z.B. "Suchbegriff & Kategorie". */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NavigatorGroup {
        private String label;
        private List<Navigator> navigatorList;
    }

    /** Ein einzelner Navigator (z.B. Kategorie, Bundesland) innerhalb einer Gruppe. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Navigator {
        private String id;
        private String label;
        private List<GroupedPossibleValues> groupedPossibleValues;
    }

    /** Gruppe von Filterwerten innerhalb eines Navigators. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupedPossibleValues {
        private String label;
        private List<PossibleValue> possibleValues;
    }

    /**
     * Ein einzelner Filterwert (z.B. eine Kategorie oder ein Bundesland).
     * Enthält URL-Parameter für die Filterung sowie optionale Eltern-Referenz.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PossibleValue {
        private String label;
        private List<UrlParamRepresentation> urlParamRepresentationForValue;
        /** null bei Einträgen der obersten Ebene */
        private String parentId;
        private String parentLabel;

        /** Liefert den ersten URL-Parameter-Namen, z.B. "ATTRIBUTE_TREE" oder "areaId". */
        public String getUrlParameterName() {
            if (urlParamRepresentationForValue == null || urlParamRepresentationForValue.isEmpty()) return null;
            return urlParamRepresentationForValue.get(0).getUrlParameterName();
        }

        /** Liefert den ersten URL-Parameter-Wert, z.B. "6941" oder "1". */
        public String getUrlParameterValue() {
            if (urlParamRepresentationForValue == null || urlParamRepresentationForValue.isEmpty()) return null;
            return urlParamRepresentationForValue.get(0).getValue();
        }
    }

    /** URL-Parameter-Darstellung für einen Filterwert. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UrlParamRepresentation {
        private String urlParameterName;
        private String value;
    }
}
