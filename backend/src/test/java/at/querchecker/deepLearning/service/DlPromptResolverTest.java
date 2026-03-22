package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.entity.WhCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlPromptResolverTest {

    @Mock DlCategoryPromptRepository repo;
    @InjectMocks DlPromptResolver resolver;

    @Test
    void resolve_returnsOwnPrompt_whenDirectMatch() {
        WhCategory laptops = category("Laptops", null);
        DlCategoryPrompt prompt = prompt(PromptType.QUICK_FACTS, "Laptop-Prompt");
        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(prompt));

        assertThat(resolver.resolve(laptops, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Laptop-Prompt");
    }

    @Test
    void resolve_inheritsFromParent_whenNoOwnPrompt() {
        WhCategory root    = category("Elektronik", null);
        WhCategory laptops = category("Laptops", root);
        DlCategoryPrompt parentPrompt = prompt(PromptType.QUICK_FACTS, "Elektronik-Prompt");

        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.QUICK_FACTS))
            .thenReturn(Optional.empty());
        when(repo.findByWhCategoryAndPromptType(root, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(parentPrompt));

        assertThat(resolver.resolve(laptops, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Elektronik-Prompt");
    }

    @Test
    void resolve_inheritsRecursively_acrossThreeLevels() {
        WhCategory root = category("Elektronik", null);
        WhCategory mid  = category("Computer", root);
        WhCategory leaf = category("Gaming-Laptops", mid);
        DlCategoryPrompt rootPrompt = prompt(PromptType.QUICK_FACTS, "Root-Prompt");

        when(repo.findByWhCategoryAndPromptType(leaf, PromptType.QUICK_FACTS))
            .thenReturn(Optional.empty());
        when(repo.findByWhCategoryAndPromptType(mid, PromptType.QUICK_FACTS))
            .thenReturn(Optional.empty());
        when(repo.findByWhCategoryAndPromptType(root, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(rootPrompt));

        assertThat(resolver.resolve(leaf, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Root-Prompt");
    }

    @Test
    void resolve_returnsDefault_whenNoCategoryMatch() {
        WhCategory root = category("Elektronik", null);
        DlCategoryPrompt defaultPrompt = prompt(PromptType.QUICK_FACTS, "Default-Prompt");

        when(repo.findByWhCategoryAndPromptType(any(WhCategory.class), any()))
            .thenReturn(Optional.empty());
        when(repo.findDefaultByPromptType(PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(root, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Default-Prompt");
    }

    @Test
    void resolve_worksIndependentlyPerPromptType() {
        WhCategory laptops = category("Laptops", null);
        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.PRODUCT_NAME))
            .thenReturn(Optional.of(prompt(PromptType.PRODUCT_NAME, "Name-Prompt")));
        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(prompt(PromptType.QUICK_FACTS, "Facts-Prompt")));

        assertThat(resolver.resolve(laptops, PromptType.PRODUCT_NAME).getUserPrompt())
            .isEqualTo("Name-Prompt");
        assertThat(resolver.resolve(laptops, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Facts-Prompt");
    }

    @Test
    void resolve_throwsException_whenNoDefaultInDB() {
        when(repo.findDefaultByPromptType(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(null, PromptType.QUICK_FACTS))
            .isInstanceOf(IllegalStateException.class);
    }

    private WhCategory category(String name, WhCategory parent) {
        WhCategory c = new WhCategory();
        c.setName(name);
        c.setParent(parent);
        return c;
    }

    private DlCategoryPrompt prompt(PromptType type, String userPrompt) {
        return DlCategoryPrompt.builder().promptType(type).userPrompt(userPrompt).build();
    }
}
