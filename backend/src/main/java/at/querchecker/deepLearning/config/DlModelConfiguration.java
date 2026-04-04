package at.querchecker.deepLearning.config;

import at.querchecker.api.config.LlmMode;
import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.repository.DlModelConfigRepository;
import at.querchecker.deepLearning.extraction.ExtractionModel;
import at.querchecker.deepLearning.extraction.Llama32ExtractionModel;
import at.querchecker.deepLearning.extraction.LlmApiExtractionModel;
import at.querchecker.deepLearning.extraction.MdebertaExtractionModel;
import at.querchecker.deepLearning.extraction.NuExtract15ExtractionModel;
import at.querchecker.deepLearning.extraction.NuExtractExtractionModel;
import at.querchecker.deepLearning.extraction.Qwen25ExtractionModel;
import at.querchecker.deepLearning.service.DlPromptResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conditionally registers ExtractionModel singletons based on:
 * 1. LLM mode (API vs LOCAL) from application.yml
 * 2. Database configuration (active models)
 *
 * Runs after ApplicationContext is ready and database is fully initialized.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DlModelConfiguration {

    private final LlmProperties llmProperties;
    private final DlModelConfigRepository modelConfigRepo;
    private final ExtractionProviderRouter providerRouter;
    private final DlPromptResolver promptResolver;
    private final ApplicationContext applicationContext;

    // Map of model name to class for registration
    private static final Map<String, Class<? extends ExtractionModel>> MODEL_REGISTRY = new HashMap<>();

    static {
        MODEL_REGISTRY.put("groq", LlmApiExtractionModel.class);
        MODEL_REGISTRY.put("llama-3.2-3b", Llama32ExtractionModel.class);
        MODEL_REGISTRY.put("nuextract-1.5-tiny", NuExtractExtractionModel.class);
        MODEL_REGISTRY.put("nuextract-1.5", NuExtract15ExtractionModel.class);
        MODEL_REGISTRY.put("qwen2.5-3b", Qwen25ExtractionModel.class);
        MODEL_REGISTRY.put("mdeberta-v3-base-squad2", MdebertaExtractionModel.class);
    }

    /**
     * Registers extraction model singletons after ApplicationContext is ready.
     * At this point, database is accessible and all dependencies are initialized.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerModels() {
        log.info("Starting conditional model registration. LLM Mode: {}", llmProperties.getMode());

        if (llmProperties.getMode() == LlmMode.API) {
            registerApiModels();
        } else {
            registerLocalModels();
        }
    }

    /**
     * API mode: Register only LlmApiExtractionModel as a singleton.
     */
    private void registerApiModels() {
        log.info("API mode: Registering LlmApiExtractionModel");

        ExtractionModel groqModel = new LlmApiExtractionModel(providerRouter, promptResolver);
        ((ConfigurableApplicationContext) applicationContext).getBeanFactory().registerSingleton("groqExtractionModel", groqModel);

        log.info("API mode: Registered LlmApiExtractionModel");
    }

    /**
     * LOCAL mode: Query database for active models and register only those as singletons.
     */
    private void registerLocalModels() {
        List<DlModelConfig> activeModels = modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc();
        log.info("LOCAL mode: Found {} active models in database", activeModels.size());

        int registered = 0;
        for (DlModelConfig config : activeModels) {
            String modelName = config.getModelName();
            Class<? extends ExtractionModel> modelClass = MODEL_REGISTRY.get(modelName);

            if (modelClass != null) {
                try {
                    ExtractionModel modelInstance = instantiateModel(modelClass);
                    String beanName = modelName + "ExtractionModel";
                    ((ConfigurableApplicationContext) applicationContext).getBeanFactory().registerSingleton(beanName, modelInstance);
                    registered++;
                    log.debug("Registered local model: {} as bean '{}'", modelName, beanName);
                } catch (Exception e) {
                    log.error("Failed to instantiate model: {}", modelName, e);
                }
            } else {
                log.warn("Unknown model in registry: {}", modelName);
            }
        }

        log.info("LOCAL mode: Successfully registered {} out of {} active models",
            registered, activeModels.size());
    }

    /**
     * Instantiate a model based on its class.
     */
    private ExtractionModel instantiateModel(Class<? extends ExtractionModel> modelClass)
        throws Exception {
        if (modelClass == LlmApiExtractionModel.class) {
            return new LlmApiExtractionModel(providerRouter, promptResolver);
        } else {
            // Local models (Llama, Qwen, NuExtract, Mdeberta) use no-arg constructors
            return modelClass.getDeclaredConstructor().newInstance();
        }
    }
}
