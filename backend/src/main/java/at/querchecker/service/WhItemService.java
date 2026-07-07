package at.querchecker.service;

import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.service.DlOrchestrationService;
import at.querchecker.deepLearning.service.ItemTextService;
import at.querchecker.dto.WhCategoryDto;
import at.querchecker.dto.WhDetailDto;
import at.querchecker.dto.WhPreviewDto;
import at.querchecker.entity.WhCategory;
import at.querchecker.entity.WhItem;
import at.querchecker.entity.WhListing;
import at.querchecker.auth.QuerCheckerPrincipal;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.willHaben.WhConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhItemService {

  private final WhItemRepository whItemRepository;
  private final WhListingRepository whListingRepository;
  private final ItemTextService itemTextService;
  private final DlOrchestrationService dlOrchestrationService;
  private final WhListingRefreshService whListingRefreshService;

  @Transactional
  public WhDetailDto openDetail(Long whListingId) {
    WhItem item = getOrCreate(whListingId);
    LocalDateTime now = LocalDateTime.now();
    if (
      item.getLastViewedAt() == null ||
      java.time.Duration.between(item.getLastViewedAt(), now).getSeconds() > 60
    ) {
      item.setViewCount(item.getViewCount() + 1);
      item.setLastViewedAt(now);
      item.setUpdatedAt(now);
      whItemRepository.save(item);
    }

    WhListing listing = item.getWhListing();

    // Return cached data immediately — WH refresh (full description, images, category)
    // runs async in background and pushes a `listing-refreshed` SSE event when done.
    List<WhPreviewDto> previews = listing
      .getImagePaths()
      .stream()
      .map((p) ->
        WhPreviewDto.builder()
          .thumbUrl(WhConstants.WH_IMAGE_BASE + p + "_thumb.jpg")
          .fullUrl(WhConstants.WH_IMAGE_BASE + p + ".jpg")
          .build()
      )
      .collect(Collectors.toList());

    ItemText itemText = itemTextService.findOrCreateOrUpdate(listing);
    long itemId = item.getId();
    // openDetail() itself is public (normale Suche, Konzept Kap. 1) — die DL-Extraktion
    // ist aber ein AI-Zugriff und verbraucht Provider-Kontingent, daher nur für eine
    // gültige Session (USER/SUPERUSER) einplanen, nicht für GUEST.
    // Achtung: authentication ist NIE null an dieser Stelle — Springs eigener
    // AnonymousAuthenticationFilter füllt für GUEST automatisch ein authentifiziertes
    // AnonymousAuthenticationToken. Nur ein echter QuerCheckerPrincipal zählt als Session.
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean hasSession = authentication != null
      && authentication.getPrincipal() instanceof QuerCheckerPrincipal;
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          whListingRefreshService.refreshAsync(listing.getId(), itemId);
          if (hasSession) {
            dlOrchestrationService.scheduleExtraction(itemText);
          }
        }
      }
    );

    return toDto(item, listing, listing.getDescription(), previews);
  }

  @Transactional
  public WhDetailDto updateRating(Long whListingId, String rating) {
    WhItem item = getOrCreate(whListingId);
    item.setRating(rating);
    item.setUpdatedAt(LocalDateTime.now());
    return toDto(whItemRepository.save(item));
  }

  @Transactional
  public WhDetailDto updateNote(Long whListingId, String note) {
    WhItem item = getOrCreate(whListingId);
    item.setNote(note);
    item.setUpdatedAt(LocalDateTime.now());
    return toDto(whItemRepository.save(item));
  }

  @Transactional
  public WhDetailDto updateInterest(Long whListingId, String level) {
    WhItem item = getOrCreate(whListingId);
    item.setInterestLevel(level);
    item.setUpdatedAt(LocalDateTime.now());
    return toDto(whItemRepository.save(item));
  }

  private WhItem getOrCreate(Long whListingId) {
    return whItemRepository
      .findByWhListingId(whListingId)
      .orElseGet(() -> {
        WhListing listing = whListingRepository
          .findById(whListingId)
          .orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "WhListing not found: " + whListingId)
          );
        return whItemRepository.save(
          WhItem.builder().whListing(listing).createdAt(LocalDateTime.now()).build()
        );
      });
  }

  private static List<WhCategoryDto> buildCategoryPath(WhCategory category) {
    if (category == null) return List.of();
    List<WhCategoryDto> path = new ArrayList<>();
    WhCategory current = category;
    while (current != null) {
      path.add(
        0,
        WhCategoryDto.builder()
          .id(current.getId())
          .whId(current.getWhId())
          .name(current.getName())
          .level(current.getLevel())
          .build()
      );
      current = current.getParent();
    }
    return path;
  }

  private WhDetailDto toDto(
    WhItem entity,
    WhListing listing,
    String description,
    List<WhPreviewDto> previews
  ) {
    String fullUrl =
      listing.getUrl() != null ? WhConstants.WH_LISTING_BASE + listing.getUrl() : null;
    return WhDetailDto.builder()
      .whItemId(entity.getId())
      .id(listing.getId())
      .whId(listing.getWhId())
      .title(listing.getTitle())
      .price(listing.getPrice())
      .location(listing.getLocation())
      .url(fullUrl)
      .listedAt(listing.getListedAt())
      .fetchedAt(listing.getFetchedAt())
      .paylivery(listing.isPaylivery())
      .categoryPath(buildCategoryPath(listing.getWhCategory()))
      .description(description)
      .previews(previews)
      .note(entity.getNote())
      .viewCount(entity.getViewCount())
      .lastViewedAt(entity.getLastViewedAt())
      .rating(entity.getRating())
      .interestLevel(entity.getInterestLevel())
      .createdAt(entity.getCreatedAt())
      .updatedAt(entity.getUpdatedAt())
      .build();
  }

  /** For mutation responses — listing metadata not needed by frontend callers. */
  private WhDetailDto toDto(WhItem entity) {
    WhListing listing = entity.getWhListing();
    return toDto(entity, listing, null, null);
  }
}
