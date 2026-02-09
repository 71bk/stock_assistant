package tw.bk.appapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class ApiArchitectureRulesTest {
    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("tw.bk.appapi");

    @Test
    void appApiShouldNotDependOnPersistenceEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("tw.bk.appapi..")
                .should().dependOnClassesThat().resideInAPackage("tw.bk.apppersistence.entity..")
                .because("app-api should consume service or DTO layers, not persistence entities directly");

        FreezingArchRule.freeze(rule).check(CLASSES);
    }
}
