package marcono1234.gson.recordadapter;

import com.google.gson.FieldNamingPolicy;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RecordComponentNamingStrategyTest {
    /** Used for testing Record component name transformation, see below */
    @Target(ElementType.RECORD_COMPONENT)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExpectedName {
        String value();
    }

    /** Used for testing Record component name transformation, see below */
    private static class NamingTestExtension implements TestTemplateInvocationContextProvider {
        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(RecordComponentNamingTest.class))
                .orElse(false);
        }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
            RecordComponent[] recordComponents = context.getRequiredTestMethod()
                .getAnnotation(RecordComponentNamingTest.class).value().getRecordComponents();
            return Arrays.stream(recordComponents)
                .map(recordComponent -> {
                    String expectedName = recordComponent.getAnnotation(ExpectedName.class).value();
                    return new TestTemplateInvocationContext() {
                        @Override
                        public String getDisplayName(int invocationIndex) {
                            return recordComponent.getName();
                        }

                        @Override
                        public List<Extension> getAdditionalExtensions() {
                            return Collections.singletonList(new ParameterResolver() {
                                @Override
                                public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                                    return true;
                                }

                                @Override
                                public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                                    return switch (parameterContext.getIndex()) {
                                        case 0 -> recordComponent;
                                        case 1 -> expectedName;
                                        default -> throw new IllegalArgumentException("Unsupported parameter index");
                                    };
                                }
                            });
                        }
                    };
                });
        }
    }

    /** Used for testing Record component name transformation, see below */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @TestTemplate
    @ExtendWith(NamingTestExtension.class)
    private @interface RecordComponentNamingTest {
        Class<? extends Record> value();
    }

    @ParameterizedTest
    @EnumSource(FieldNamingPolicy.class)
    void testFromFieldNamingPolicy(FieldNamingPolicy fieldNamingPolicy) throws Exception {
        var namingStrategy = RecordComponentNamingStrategy.fromFieldNamingPolicy(fieldNamingPolicy);
        // Should match naming strategy constant with same name
        var expectedStrategy = RecordComponentNamingStrategy.class.getField(fieldNamingPolicy.name()).get(null);
        assertSame(expectedStrategy, namingStrategy);
    }

    record Identity(
        @ExpectedName("i")
        int i,
        @ExpectedName("AbCd")
        int AbCd,
        @ExpectedName("ABCD")
        int ABCD
    ) { }

    @RecordComponentNamingTest(Identity.class)
    void testIdentity(RecordComponent recordComponent, String expectedName) {
        assertEquals(expectedName, RecordComponentNamingStrategy.IDENTITY.translateName(recordComponent));
    }

    record LocaleUpperCase(
        @ExpectedName("I")
        int i
    ) { }

    @RecordComponentNamingTest(LocaleUpperCase.class)
    void testLocaleUpperCasing(RecordComponent recordComponent, String expectedName) {
        Locale oldLocale = Locale.getDefault();
        // Set Turkish as locale which has special case conversion rules
        Locale.setDefault(new Locale("tr"));
        try {
            String originalName = recordComponent.getName();
            // Verify that test is implemented correctly
            assertNotEquals(originalName.toUpperCase(Locale.ROOT), originalName.toUpperCase());

            assertEquals(
                expectedName,
                RecordComponentNamingStrategy.UPPER_CAMEL_CASE.translateName(recordComponent),
                "Case conversion should not be affected by default Locale"
            );
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    record LocaleLowerCase(
        @ExpectedName("i")
        int I
    ) { }

    @RecordComponentNamingTest(LocaleLowerCase.class)
    void testLocaleLowerCasing(RecordComponent recordComponent, String expectedName) {
        Locale oldLocale = Locale.getDefault();
        // Set Turkish as locale which has special case conversion rules
        Locale.setDefault(new Locale("tr"));
        try {
            String originalName = recordComponent.getName();
            // Verify that test is implemented correctly
            assertNotEquals(originalName.toLowerCase(Locale.ROOT), originalName.toLowerCase());

            assertEquals(
                expectedName,
                RecordComponentNamingStrategy.LOWER_CASE_WITH_DASHES.translateName(recordComponent),
                "Case conversion should not be affected by default Locale"
            );
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    record UpperCamelCase(
        @ExpectedName("I")
        int i,
        @ExpectedName("AbCd")
        int AbCd,
        @ExpectedName("ABCD")
        int ABCD,
        @ExpectedName("ABCD")
        int aBCD,
        @ExpectedName("_Test")
        int _test,
        // Non-letter with uppercase variant, see https://github.com/google/gson/issues/1965
        // Note: Ignore IDE errors, javac can compile this without issues
        @ExpectedName("\u2170")
        int \u2170,
        @ExpectedName("\u2170_")
        int \u2170_,
        @ExpectedName("_\u2170")
        int _\u2170
    ) { }

    @RecordComponentNamingTest(UpperCamelCase.class)
    void testUpperCamelCase(RecordComponent recordComponent, String expectedName) {
        assertEquals(expectedName, RecordComponentNamingStrategy.UPPER_CAMEL_CASE.translateName(recordComponent));
    }

    record UpperCamelCaseWithSpaces(
        @ExpectedName("I")
        int i,
        @ExpectedName("Ab Cd")
        int AbCd,
        @ExpectedName("A B C D")
        int ABCD,
        @ExpectedName("A B C D")
        int aBCD,
        @ExpectedName("_Te St")
        int _teSt
    ) { }

    @RecordComponentNamingTest(UpperCamelCaseWithSpaces.class)
    void testUpperCamelCaseWithSpaces(RecordComponent recordComponent, String expectedName) {
        assertEquals(expectedName, RecordComponentNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES.translateName(recordComponent));
    }

    record LowerCaseWithUnderscores(
        @ExpectedName("i")
        int i,
        @ExpectedName("ab_cd")
        int AbCd,
        @ExpectedName("a_b_c_d")
        int ABCD,
        @ExpectedName("a_b_c_d")
        int aBCD,
        @ExpectedName("_te_st")
        int _teSt
    ) { }

    @RecordComponentNamingTest(LowerCaseWithUnderscores.class)
    void testLowerCaseWithUnderscores(RecordComponent recordComponent, String expectedName) {
        assertEquals(expectedName, RecordComponentNamingStrategy.LOWER_CASE_WITH_UNDERSCORES.translateName(recordComponent));
    }

    record LowerCaseWithDashes(
        @ExpectedName("i")
        int i,
        @ExpectedName("ab-cd")
        int AbCd,
        @ExpectedName("a-b-c-d")
        int ABCD,
        @ExpectedName("a-b-c-d")
        int aBCD,
        @ExpectedName("_te-st")
        int _teSt
    ) { }

    @RecordComponentNamingTest(LowerCaseWithDashes.class)
    void testLowerCaseWithDashes(RecordComponent recordComponent, String expectedName) {
        assertEquals(expectedName, RecordComponentNamingStrategy.LOWER_CASE_WITH_DASHES.translateName(recordComponent));
    }

    record LowerCaseWithDots(
        @ExpectedName("i")
        int i,
        @ExpectedName("ab.cd")
        int AbCd,
        @ExpectedName("a.b.c.d")
        int ABCD,
        @ExpectedName("a.b.c.d")
        int aBCD,
        @ExpectedName("_te.st")
        int _teSt
    ) { }

    @RecordComponentNamingTest(LowerCaseWithDots.class)
    void testLowerCaseWithDots(RecordComponent recordComponent, String expectedName) {
        assertEquals(expectedName, RecordComponentNamingStrategy.LOWER_CASE_WITH_DOTS.translateName(recordComponent));
    }
}
