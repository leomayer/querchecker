package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.entity.DlExtractionRun;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ExtractionTask implements Runnable {

    private final DlExtractionRun run;
    private final DlExtractionService extractionService;

    @Override
    public void run() {
        extractionService.runModel(run);
    }

    public DlExtractionRun getRun() {
        return run;
    }
}
