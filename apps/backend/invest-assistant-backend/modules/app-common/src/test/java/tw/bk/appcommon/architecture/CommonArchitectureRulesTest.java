package tw.bk.appcommon.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class CommonArchitectureRulesTest {
    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("tw.bk.appcommon");

    @Test
    void appCommonShouldNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("tw.bk.appcommon..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "tw.bk.appapi..",
                        "tw.bk.appauth..",
                        "tw.bk.appportfolio..",
                        "tw.bk.appstocks..",
                        "tw.bk.appocr..",
                        "tw.bk.appai..",
                        "tw.bk.apprag..")
                .because("app-common must remain reusable and independent from business modules");

        rule.check(CLASSES);
    }
}
