package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QuercheckerNoteDto {

    private Long id;
    private Long whListingId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
