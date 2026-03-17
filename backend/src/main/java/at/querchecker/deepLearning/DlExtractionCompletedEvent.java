package at.querchecker.deepLearning;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DlExtractionCompletedEvent {
    private final Long itemTextId;
    private final String modelName;
}
