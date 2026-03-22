package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySpecPreference;
import at.querchecker.research.repository.CategorySpecPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategorySpecPreferenceServiceTest {

    @Mock CategorySpecPreferenceRepository repo;
    @InjectMocks CategorySpecPreferenceService service;

    private WhCategory category(String name, WhCategory parent) {
        WhCategory cat = new WhCategory();
        cat.setName(name);
        cat.setParent(parent);
        return cat;
    }

    private CategorySpecPreference pref(String... keys) {
        CategorySpecPreference p = new CategorySpecPreference();
        p.setFieldKeys(List.of(keys));
        return p;
    }

    // --- Vererbungslogik ---

    @Test
    void getPreferences_returnsOwnPref_whenDirectMatch() {
        WhCategory laptops = category("Laptops", null);
        when(repo.findByWhCategory(laptops)).thenReturn(Optional.of(pref("cpu", "ram")));

        assertThat(service.getPreferences(laptops)).containsExactly("cpu", "ram");
    }

    @Test
    void getPreferences_inheritsFromParent_whenNoOwnPref() {
        WhCategory elektronik = category("Elektronik", null);
        WhCategory laptops    = category("Laptops", elektronik);

        when(repo.findByWhCategory(laptops)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(elektronik)).thenReturn(Optional.of(pref("cpu", "ram")));

        assertThat(service.getPreferences(laptops)).containsExactly("cpu", "ram");
    }

    @Test
    void getPreferences_inheritsRecursively_acrossThreeLevels() {
        WhCategory root = category("Elektronik", null);
        WhCategory mid  = category("Computer", root);
        WhCategory leaf = category("Gaming-Laptops", mid);

        when(repo.findByWhCategory(leaf)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(mid)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(root)).thenReturn(Optional.of(pref("cpu", "gpu")));

        assertThat(service.getPreferences(leaf)).containsExactly("cpu", "gpu");
    }

    @Test
    void getPreferences_returnsEmpty_whenNoMatchInWholeHierarchy() {
        WhCategory root = category("Elektronik", null);
        WhCategory leaf = category("Laptops", root);

        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getPreferences(leaf)).isEmpty();
    }

    @Test
    void getPreferences_returnsEmpty_whenCategoryNull() {
        assertThat(service.getPreferences(null)).isEmpty();
    }

    @Test
    void getPreferences_prefersOwnOverParent_whenBothExist() {
        WhCategory parent = category("Elektronik", null);
        WhCategory child  = category("Laptops", parent);

        when(repo.findByWhCategory(child)).thenReturn(Optional.of(pref("display", "akku")));
        lenient().when(repo.findByWhCategory(parent)).thenReturn(Optional.of(pref("cpu", "ram")));

        assertThat(service.getPreferences(child)).containsExactly("display", "akku");
        verify(repo, never()).findByWhCategory(parent);
    }

    // --- Max-5-Validierung ---

    @Test
    void setPreferences_accepts_fiveKeywords() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.empty());
        assertThatNoException().isThrownBy(() ->
                service.setPreferences(cat, List.of("cpu", "ram", "display", "akku", "gpu")));
    }

    @Test
    void setPreferences_rejects_moreThanFiveKeywords() {
        WhCategory cat = category("Laptops", null);
        assertThatThrownBy(() ->
                service.setPreferences(cat, List.of("a", "b", "c", "d", "e", "f")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }

    @Test
    void setPreferences_accepts_emptyList() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.empty());
        assertThatNoException().isThrownBy(() ->
                service.setPreferences(cat, List.of()));
    }

    // --- getQueryKeywords / getMandatoryFields ---

    @Test
    void getQueryKeywords_returnsFirstFive_whenMoreThanFiveInherited() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(cat))
                .thenReturn(Optional.of(pref("a", "b", "c", "d", "e", "f", "g")));

        assertThat(service.getQueryKeywords(cat)).hasSize(5);
    }

    @Test
    void getMandatoryFields_returnsAll_regardlessOfCount() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(cat))
                .thenReturn(Optional.of(pref("a", "b", "c", "d", "e", "f", "g")));

        assertThat(service.getMandatoryFields(cat)).hasSize(7);
    }

    @Test
    void getQueryKeywords_returnsEmpty_whenNoPreferences() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getQueryKeywords(cat)).isEmpty();
    }
}
