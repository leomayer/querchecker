package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;

import java.util.List;

public interface ExtractionModel {
    // Einzige Verbindung zur DlModelConfig in DB
    // getName() muss exakt mit DlModelConfig.modelName übereinstimmen
    String getName();

    // maxTokens kommt von außen aus DlModelConfig – nicht hardcodiert
    // modelVersion kommt aus DB – kein getVersion() nötig
    List<ExtractionResult> extract(ItemText input, String prompt, int maxTokens);
}
