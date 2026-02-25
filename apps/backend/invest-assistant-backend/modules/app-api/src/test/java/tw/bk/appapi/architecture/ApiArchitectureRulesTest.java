package tw.bk.appapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tw.bk.appapi.ai.AiConversationController;

class ApiArchitectureRulesTest {
    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("tw.bk.appapi");

    @Test
    void appApiShouldNotDependOnPersistenceEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("tw.bk.appapi..")
                .should().dependOnClassesThat().resideInAPackage("tw.bk.apppersistence.entity..")
                .because("app-api should consume service or DTO layers, not persistence entities directly");

        rule.check(CLASSES);
    }

    @Test
    void aiConversationControllerShouldNotDependOnDomainSpecificModulesDirectly() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("AiConversationController")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "tw.bk.appportfolio..",
                        "tw.bk.appstocks..",
                        "tw.bk.apprag..")
                .because("AiConversationController should orchestrate chat flow only; domain context belongs to skills");

        rule.check(CLASSES);
    }

    @Test
    void aiConversationControllerShouldStayLean() {
        int maxConstructorParams = Arrays.stream(AiConversationController.class.getDeclaredConstructors())
                .mapToInt(Constructor::getParameterCount)
                .max()
                .orElse(0);
        long instanceFields = Arrays.stream(AiConversationController.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .count();

        assertTrue(maxConstructorParams <= 8,
                "AiConversationController constructor dependencies exceeded limit: " + maxConstructorParams);
        assertTrue(instanceFields <= 8,
                "AiConversationController instance fields exceeded limit: " + instanceFields);
    }
}
