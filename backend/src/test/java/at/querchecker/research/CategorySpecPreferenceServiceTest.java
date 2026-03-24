package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySpecPreference;
import at.querchecker.research.entity.CategorySpecPreferenceField;
import at.querchecker.research.entity.FieldSource;
import at.querchecker.research.repository.CategorySpecPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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

    private CategorySpecPreference pref(List<CategorySpecPreferenceField> fields) {
        CategorySpecPreference p = new CategorySpecPreference();
        p.setFields(new ArrayList<>(fields));
        return p;
    }

    private CategorySpecPreferenceField field(String key, FieldSource source) {
        return CategorySpecPreferenceField.builder()
                .fieldKey(key).fieldSource(source).build();
    }

    // --- Vererbungslogik (getMandatoryFields) ---

    @Test
    void getMandatoryFields_returnsOwnFields_whenDirectMatch() {
        WhCategory laptops = category("Notebooks", null);
        when(repo.findByWhCategory(laptops)).thenReturn(Optional.of(pref(List.of(
                field("cpu", FieldSource.SYSTEM),
                field("ram", FieldSource.SYSTEM),
                field("oled", FieldSource.USER)))));

        assertThat(service.getMandatoryFields(laptops))
                .containsExactly("cpu", "ram", "oled");
    }

    @Test
    void getMandatoryFields_inheritsFromParent_whenNoOwnPref() {
        WhCategory elektronik = category("Elektronik", null);
        WhCategory laptops    = category("Notebooks", elektronik);

        when(repo.findByWhCategory(laptops)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(elektronik)).thenReturn(Optional.of(pref(List.of(
                field("cpu", FieldSource.SYSTEM),
                field("ram", FieldSource.SYSTEM)))));

        assertThat(service.getMandatoryFields(laptops)).containsExactly("cpu", "ram");
    }

    @Test
    void getMandatoryFields_inheritsRecursively_acrossThreeLevels() {
        WhCategory root = category("Elektronik", null);
        WhCategory mid  = category("Computer", root);
        WhCategory leaf = category("Gaming-Laptops", mid);

        when(repo.findByWhCategory(leaf)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(mid)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(root)).thenReturn(Optional.of(pref(List.of(
                field("cpu", FieldSource.SYSTEM),
                field("gpu", FieldSource.SYSTEM)))));

        assertThat(service.getMandatoryFields(leaf)).containsExactly("cpu", "gpu");
    }

    @Test
    void getMandatoryFields_returnsEmpty_whenNoMatchInHierarchy() {
        WhCategory root = category("Elektronik", null);
        WhCategory leaf = category("Notebooks", root);
        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getMandatoryFields(leaf)).isEmpty();
    }

    @Test
    void getMandatoryFields_returnsEmpty_whenCategoryNull() {
        assertThat(service.getMandatoryFields(null)).isEmpty();
    }

    @Test
    void getMandatoryFields_prefersOwnOverParent() {
        WhCategory parent = category("Elektronik", null);
        WhCategory child  = category("Notebooks", parent);

        when(repo.findByWhCategory(child)).thenReturn(Optional.of(pref(List.of(
                field("display", FieldSource.SYSTEM),
                field("akku",    FieldSource.USER)))));
        lenient().when(repo.findByWhCategory(parent)).thenReturn(Optional.of(pref(List.of(
                field("cpu", FieldSource.SYSTEM)))));

        assertThat(service.getMandatoryFields(child)).containsExactly("display", "akku");
        verify(repo, never()).findByWhCategory(parent);
    }

    // --- getMandatorySystemFields: nur SYSTEM-Felder ---

    @Test
    void getMandatorySystemFields_returnsOnlySystemFields() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(pref(List.of(
                field("cpu",  FieldSource.SYSTEM),
                field("ram",  FieldSource.SYSTEM),
                field("oled", FieldSource.USER),
                field("i7",   FieldSource.USER)))));

        assertThat(service.getMandatorySystemFields(cat)).containsExactly("cpu", "ram");
    }

    @Test
    void getMandatorySystemFields_excludesUserFields_noImpactFromValueKeywords() {
        // Kernfall: USER-Keywords (oled, thunderbolt4) dürfen nicht in die
        // Qualitätsbewertung einfließen — sie sind keine quickFacts-Keys
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(pref(List.of(
                field("cpu",         FieldSource.SYSTEM),
                field("oled",        FieldSource.USER),
                field("thunderbolt4",FieldSource.USER)))));

        assertThat(service.getMandatorySystemFields(cat)).containsExactly("cpu");
    }

    @Test
    void getMandatorySystemFields_returnsEmpty_whenNoSystemFields() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(pref(List.of(
                field("oled", FieldSource.USER)))));

        assertThat(service.getMandatorySystemFields(cat)).isEmpty();
    }

    @Test
    void getMandatorySystemFields_returnsEmpty_whenNoPreference() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getMandatorySystemFields(cat)).isEmpty();
    }

    // --- getQueryKeywords: USER fields only ---

    @Test
    void getQueryKeywords_returnsOnlyUserFields() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(pref(List.of(
                field("cpu",  FieldSource.SYSTEM),
                field("ram",  FieldSource.SYSTEM),
                field("oled", FieldSource.USER),
                field("i7",   FieldSource.USER)))));

        assertThat(service.getQueryKeywords(cat)).containsExactly("oled", "i7");
    }

    @Test
    void getQueryKeywords_limitsToFive_userFieldsOnly() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(pref(List.of(
                field("cpu", FieldSource.SYSTEM),
                field("a", FieldSource.USER), field("b", FieldSource.USER),
                field("c", FieldSource.USER), field("d", FieldSource.USER),
                field("e", FieldSource.USER), field("f", FieldSource.USER)))));

        assertThat(service.getQueryKeywords(cat)).hasSize(5);
    }

    @Test
    void getQueryKeywords_returnsEmpty_whenNoUserFields() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(pref(List.of(
                field("cpu", FieldSource.SYSTEM),
                field("ram", FieldSource.SYSTEM)))));

        assertThat(service.getQueryKeywords(cat)).isEmpty();
    }

    @Test
    void getQueryKeywords_returnsEmpty_whenNoPreference() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getQueryKeywords(cat)).isEmpty();
    }

    // --- setPreferences: USER fields only, SYSTEM untouched ---

    @Test
    void setPreferences_replacesUserFields_keepingSystemFields() {
        WhCategory cat = category("Notebooks", null);
        CategorySpecPreference existing = pref(new ArrayList<>(List.of(
                field("cpu", FieldSource.SYSTEM),
                field("ram", FieldSource.SYSTEM),
                field("old_user", FieldSource.USER))));
        when(repo.findByWhCategory(cat)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.setPreferences(cat, List.of("oled", "i7"));

        ArgumentCaptor<CategorySpecPreference> captor =
                ArgumentCaptor.forClass(CategorySpecPreference.class);
        verify(repo).save(captor.capture());

        CategorySpecPreference saved = captor.getValue();
        assertThat(saved.getFields().stream()
                .filter(f -> f.getFieldSource() == FieldSource.SYSTEM)
                .map(CategorySpecPreferenceField::getFieldKey))
                .containsExactly("cpu", "ram");
        assertThat(saved.getFields().stream()
                .filter(f -> f.getFieldSource() == FieldSource.USER)
                .map(CategorySpecPreferenceField::getFieldKey))
                .containsExactly("oled", "i7");
    }

    @Test
    void setPreferences_accepts_fiveUserKeywords() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThatNoException().isThrownBy(() ->
                service.setPreferences(cat, List.of("a", "b", "c", "d", "e")));
    }

    @Test
    void setPreferences_rejects_moreThanFiveKeywords() {
        WhCategory cat = category("Notebooks", null);
        assertThatThrownBy(() ->
                service.setPreferences(cat, List.of("a", "b", "c", "d", "e", "f")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }

    @Test
    void setPreferences_accepts_emptyList() {
        WhCategory cat = category("Notebooks", null);
        when(repo.findByWhCategory(cat)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThatNoException().isThrownBy(() ->
                service.setPreferences(cat, List.of()));
    }
}
