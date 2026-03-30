package at.querchecker.api.extraction;

import at.querchecker.research.model.LenientStringMapDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

/**
 * Strukturiertes Ergebnis der PRODUCT_NAME-Extraktion.
 * extractedModel: Hersteller + Modellbezeichnung (Suchterm).
 * condensedSpec:  Wichtigste technische Eckdaten aus dem Inserat als flaches String-Map.
 */
public record ProductNameResult(
    String extractedModel,
    @JsonDeserialize(using = LenientStringMapDeserializer.class) Map<String, String> condensedSpec
) {}
