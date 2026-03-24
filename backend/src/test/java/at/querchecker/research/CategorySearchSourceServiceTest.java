package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.entity.SourceType;
import at.querchecker.research.repository.CategorySearchSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static at.querchecker.research.entity.SourceType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategorySearchSourceServiceTest {

    @Mock CategorySearchSourceRepository repo;
    @InjectMocks CategorySearchSourceService service;

    private WhCategory category(String name, WhCategory parent) {
        WhCategory cat = new WhCategory();
        cat.setName(name);
        cat.setParent(parent);
        return cat;
    }

    private CategorySearchSource source(WhCategory cat, int priority,
                                        String domain, SourceType type,
                                        boolean lookupEnabled) {
        return CategorySearchSource.builder()
                .whCategory(cat).priority(priority).siteDomain(domain)
                .sourceType(type).lookupEnabled(lookupEnabled).active(true)
                .searchResultCount(10).build();
    }

    @Test
    void findForCategory_returnsOwnSources_whenDirectMatch() {
        WhCategory tv = category("TV & Audio", null);
        List<CategorySearchSource> sources = List.of(
                source(tv, 1, "flatpanelshd.com", FLATPANELSHD, true),
                source(tv, 2, "whathifi.com",     GENERIC,      true));
        when(repo.findByWhCategoryAndActiveTrueOrderByPriorityAsc(tv))
                .thenReturn(sources);

        assertThat(service.findForCategory(tv)).hasSize(2);
        assertThat(service.findForCategory(tv).get(0).getSiteDomain())
                .isEqualTo("flatpanelshd.com");
    }

    @Test
    void findForCategory_filtersDisabledSources() {
        WhCategory cat = category("Kabel & Adapter", null);
        when(repo.findByWhCategoryAndActiveTrueOrderByPriorityAsc(cat))
                .thenReturn(List.of(source(cat, 1, "icecat.biz", ICECAT, false)));

        assertThat(service.findForCategory(cat)).isEmpty();
    }

    @Test
    void findForCategory_usesParentFallback_whenInheritFromParentTrue() {
        WhCategory elektronik = category("Elektronik", null);
        WhCategory neu        = category("NeueKat",    elektronik);
        CategorySearchSource parentSrc = source(elektronik, 1, "icecat.biz", ICECAT, true);
        parentSrc.setInheritFromParent(true);

        when(repo.findByWhCategoryAndActiveTrueOrderByPriorityAsc(neu)).thenReturn(List.of());
        when(repo.findByWhCategoryAndInheritFromParentTrueAndActiveTrueOrderByPriorityAsc(elektronik))
                .thenReturn(List.of(parentSrc));

        assertThat(service.findForCategory(neu)).hasSize(1);
        assertThat(service.findForCategory(neu).get(0).getSiteDomain())
                .isEqualTo("icecat.biz");
    }

    @Test
    void findForCategory_returnsEmpty_whenNoMatch() {
        WhCategory cat = category("Unbekannt", null);
        when(repo.findByWhCategoryAndActiveTrueOrderByPriorityAsc(cat)).thenReturn(List.of());

        assertThat(service.findForCategory(cat)).isEmpty();
    }

    @Test
    void findForCategory_returnsEmpty_whenCategoryIsNull() {
        assertThat(service.findForCategory(null)).isEmpty();
        verifyNoInteractions(repo);
    }
}
