package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wh_listing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String whId;

    // nullable – Willhaben liefert HEADING nicht immer
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal price;

    private String location;

    // nullable – Self-Link fehlt gelegentlich
    private String url;

    private LocalDateTime listedAt;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    private String thumbnailUrl;
}
