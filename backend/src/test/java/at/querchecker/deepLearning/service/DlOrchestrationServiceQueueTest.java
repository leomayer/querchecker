package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.deepLearning.repository.DlModelConfigRepository;
import at.querchecker.entity.AppConfig;
import at.querchecker.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlOrchestrationServiceQueueTest {

    @Mock DlModelConfigRepository modelConfigRepo;
    @Mock DlExtractionRunRepository runRepo;
    @Mock DlExtractionTermRepository termRepo;
    @Mock DlPromptResolver promptResolver;
    @Mock DlExtractionService extractionService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AppConfigRepository appConfigRepo;
    @InjectMocks DlOrchestrationService service;

    @BeforeEach
    void setUp() {
        when(appConfigRepo.findById("dl.queue.limit"))
            .thenReturn(Optional.of(appConfig("dl.queue.limit", "3")));
        lenient().when(promptResolver.resolve(any(ItemText.class))).thenReturn("prompt");
        lenient().when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        // Default: no existing runs
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), any(), any())).thenReturn(false);
        when(runRepo.existsByItemTextAndModelConfigAndStatusIn(any(), any(), anyList())).thenReturn(false);
        service.init();
    }

    @Test
    void scheduleExtraction_newItemLandsAtFrontOfQueue() throws InterruptedException {
        DlModelConfig mc = modelConfig("gelectra", 10);
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));

        ExtractionModel model = mockModel("gelectra");
        service.models = List.of(model);

        // Block the executor so the queue builds up
        CountDownLatch latch = new CountDownLatch(1);
        service.getQueue().addFirst(() -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        service.scheduleExtraction(itemText(1L)); // Item A
        service.scheduleExtraction(itemText(2L)); // Item B → must land in front

        ExtractionTask first = (ExtractionTask) service.getQueue().peekFirst();
        assertThat(first).isNotNull();
        assertThat(first.getRun().getItemText().getId()).isEqualTo(2L);

        latch.countDown();
    }

    @Test
    void scheduleExtraction_excessTasksAtBackBecomeCancelled() {
        DlModelConfig mc1 = modelConfig("gelectra", 10);
        DlModelConfig mc2 = modelConfig("bert", 20);
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc())
            .thenReturn(List.of(mc1, mc2));

        ExtractionModel m1 = mockModel("gelectra");
        ExtractionModel m2 = mockModel("bert");
        service.models = List.of(m1, m2);

        // Block executor so no tasks are consumed during the test
        CountDownLatch latch = new CountDownLatch(1);
        service.getQueue().addFirst(() -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // Pre-fill queue to limit=3 with real ExtractionTasks so they can be CANCELLED on overflow
        service.getQueue().add(dummyExtractionTask());
        service.getQueue().add(dummyExtractionTask());
        service.getQueue().add(dummyExtractionTask());

        // New item with 2 models → 2 tasks added → total 5 (+ 1 blocking) → 2 trimmed from back
        service.scheduleExtraction(itemText(1L));

        verify(runRepo, atLeastOnce()).save(argThat(run ->
            run.getStatus() == ExtractionStatus.CANCELLED));

        latch.countDown();
    }

    @Test
    void scheduleExtraction_executionOrderPreserved() {
        DlModelConfig gelectra = modelConfig("gelectra", 10);
        DlModelConfig bert = modelConfig("bert", 20);
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc())
            .thenReturn(List.of(gelectra, bert));

        service.models = List.of(mockModel("gelectra"), mockModel("bert"));

        // Block executor so the worker thread doesn't consume tasks before peekFirst()
        CountDownLatch latch = new CountDownLatch(1);
        service.getQueue().addFirst(() -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        service.scheduleExtraction(itemText(1L));

        ExtractionTask first = (ExtractionTask) service.getQueue().peekFirst();
        assertThat(first).isNotNull();
        assertThat(first.getRun().getModelConfig().getModelName()).isEqualTo("gelectra");

        latch.countDown();
    }

    @Test
    void scheduleExtraction_skipsDuplicateInitPending() {
        DlModelConfig mc = modelConfig("gelectra", 10);
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatusIn(any(), eq(mc), anyList())).thenReturn(true);

        // Model list is irrelevant — INIT/PENDING check short-circuits before models are checked
        service.models = List.of();
        service.scheduleExtraction(itemText(1L));

        verify(runRepo, never()).save(argThat(run -> run.getStatus() == ExtractionStatus.INIT));
    }

    @Test
    void scheduleExtraction_cancelledRunAllowsNewInit() {
        DlModelConfig mc = modelConfig("gelectra", 10);
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        // No DONE, no INIT/PENDING → CANCELLED run exists but is skipped by the StatusIn check
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), eq(mc), eq(ExtractionStatus.DONE)))
            .thenReturn(false);
        when(runRepo.existsByItemTextAndModelConfigAndStatusIn(any(), eq(mc), anyList()))
            .thenReturn(false);

        service.models = List.of(mockModel("gelectra"));
        service.scheduleExtraction(itemText(1L));

        verify(runRepo, atLeastOnce()).save(argThat(run ->
            run.getStatus() == ExtractionStatus.INIT));
    }

    @Test
    void scheduleExtraction_withinLimit_noCancellation() {
        DlModelConfig mc = modelConfig("gelectra", 10);
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        service.models = List.of(mockModel("gelectra"));

        service.scheduleExtraction(itemText(1L));

        verify(runRepo, never()).save(argThat(run ->
            run.getStatus() == ExtractionStatus.CANCELLED));
    }

    // --- Helpers ---

    private DlModelConfig modelConfig(String name, int order) {
        return DlModelConfig.builder().modelName(name).executionOrder(order).active(true).build();
    }

    private ItemText itemText(Long id) {
        ItemText it = new ItemText();
        it.setId(id);
        it.setTitle("title");
        it.setDescription("desc");
        return it;
    }

    private AppConfig appConfig(String key, String value) {
        return AppConfig.builder().key(key).value(value).updatedAt(java.time.LocalDateTime.now()).build();
    }

    private ExtractionModel mockModel(String name) {
        ExtractionModel m = mock(ExtractionModel.class);
        when(m.getName()).thenReturn(name);
        return m;
    }

    private ExtractionTask dummyExtractionTask() {
        DlModelConfig mc = DlModelConfig.builder().modelName("dummy").executionOrder(99).build();
        DlExtractionRun run = DlExtractionRun.builder().status(ExtractionStatus.INIT).modelConfig(mc).build();
        return new ExtractionTask(run, extractionService);
    }
}
