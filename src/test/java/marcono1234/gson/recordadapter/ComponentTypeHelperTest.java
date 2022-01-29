package marcono1234.gson.recordadapter;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/** Covers cases which cannot be easily tested in {@link GenericsTest} */
class ComponentTypeHelperTest {
    private static Type[] getComponentTypes(Class<?> c) {
        return Arrays.stream(c.getRecordComponents())
            .map(RecordComponent::getGenericType)
            .toArray(Type[]::new);
    }

    private static Type[] getResolvedComponentTypes(TypeToken<? extends Record> typeToken) {
        return ComponentTypeHelper.resolveComponentTypes(typeToken, getComponentTypes(typeToken.getRawType()));
    }

    record N(int i) { }

    record WildcardsWithTypeVariableBounds<T>(
        List<? extends T> l1,
        List<? super T> l2
    ) { }

    @Test
    void testWildcardWithTypeVariableBound() {
        Type[] resolvedTypes = getResolvedComponentTypes(new TypeToken<WildcardsWithTypeVariableBounds<N>>() {});
        assertEquals(2, resolvedTypes.length);

        {
            ParameterizedType type0 = (ParameterizedType) resolvedTypes[0];
            assertNull(type0.getOwnerType());
            assertEquals(List.class, type0.getRawType());
            assertEquals(1, type0.getActualTypeArguments().length);
            WildcardType wildcard0 = (WildcardType) type0.getActualTypeArguments()[0];
            assertArrayEquals(new Type[0], wildcard0.getLowerBounds());
            assertArrayEquals(new Type[]{N.class}, wildcard0.getUpperBounds());
        }

        {
            ParameterizedType type1 = (ParameterizedType) resolvedTypes[1];
            assertNull(type1.getOwnerType());
            assertEquals(List.class, type1.getRawType());
            assertEquals(1, type1.getActualTypeArguments().length);
            WildcardType wildcard1 = (WildcardType) type1.getActualTypeArguments()[0];
            assertArrayEquals(new Type[]{N.class}, wildcard1.getLowerBounds());
            assertArrayEquals(new Type[]{Object.class}, wildcard1.getUpperBounds());
        }
    }

    // Use PER_CLASS to support non-static method for @MethodSource
    @TestInstance(PER_CLASS)
    private interface TypeImplTestBase {
        record TestData(
            /** {@link Type} instance created by this library */
            Type type,
            /** {@link Type} instance created by the JDK classes */
            Type jdkType,
            /** Expected {@code toString()} result */
            String expectedToString
        ) {
        }

        Stream<TestData> createArguments() throws Exception;

        default Stream<Arguments> provideArguments() throws Exception {
            return createArguments().map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("provideArguments")
        default void testHashCode(TestData testData) {
            assertEquals(testData.jdkType.hashCode(), testData.type.hashCode());
        }

        // Method intentionally tests `equals` implementation, suppress IntelliJ warnings
        @SuppressWarnings({"SimplifiableAssertion", "EqualsWithItself", "ConstantConditions"})
        @ParameterizedTest
        @MethodSource("provideArguments")
        default void testEquals(TestData testData) {
            Type actualType = testData.type;
            assertTrue(actualType.equals(actualType));
            assertFalse(actualType.equals(null));
            assertFalse(actualType.equals(String.class));

            Type jdkType = testData.jdkType;
            assertTrue(actualType.equals(jdkType));
            assertTrue(jdkType.equals(actualType));
        }

        @ParameterizedTest
        @MethodSource("provideArguments")
        default void testToString(TestData testData) {
            assertEquals(testData.expectedToString, testData.type.toString());
            assertEquals(testData.expectedToString, testData.type.getTypeName());
        }
    }

    @Nested
    class GenericArrayTypeImplTest<T> implements TypeImplTestBase {
        private List<T>[] f1;

        private Type getTypeVariable() {
            return getClass().getTypeParameters()[0];
        }

        private Type getFieldType(String fieldName) throws Exception {
            return getClass().getDeclaredField(fieldName).getGenericType();
        }

        @Override
        public Stream<TestData> createArguments() throws Exception {
            return Stream.of(
                new TestData(
                    new ComponentTypeHelper.GenericArrayTypeImpl(new ComponentTypeHelper.ParameterizedTypeImpl(List.class, null, new Type[] {getTypeVariable()})),
                    getFieldType("f1"),
                    "java.util.List<T>[]"
                )
            );
        }
    }

    @Nested
    class ParameterizedTypeImplTest<T> implements TypeImplTestBase {
        private List<T> f1;
        private Map<T, String> f2;
        private Map.Entry<Integer, Boolean> f3;

        @SuppressWarnings("InnerClassMayBeStatic")
        private class Inner {
        }

        private ParameterizedTypeImplTest<String>.Inner f4;

        @SuppressWarnings("InnerClassMayBeStatic")
        private class InnerWithParam<U> {
        }

        private ParameterizedTypeImplTest<String>.InnerWithParam<Boolean> f5;

        private Type getTypeVariable() {
            return getClass().getTypeParameters()[0];
        }

        private Type getFieldType(String fieldName) throws Exception {
            return getClass().getDeclaredField(fieldName).getGenericType();
        }

        @Override
        public Stream<TestData> createArguments() throws Exception {
            return Stream.of(
                new TestData(
                    new ComponentTypeHelper.ParameterizedTypeImpl(List.class, null, new Type[] {getTypeVariable()}),
                    getFieldType("f1"),
                    "java.util.List<T>"
                ),
                new TestData(
                    new ComponentTypeHelper.ParameterizedTypeImpl(Map.class, null, new Type[] {getTypeVariable(), String.class}),
                    getFieldType("f2"),
                    "java.util.Map<T, java.lang.String>"
                ),
                new TestData(
                    new ComponentTypeHelper.ParameterizedTypeImpl(Map.Entry.class, Map.class, new Type[] {Integer.class, Boolean.class}),
                    getFieldType("f3"),
                    "java.util.Map$Entry<java.lang.Integer, java.lang.Boolean>"
                ),
                new TestData(
                    new ComponentTypeHelper.ParameterizedTypeImpl(
                        Inner.class,
                        new ComponentTypeHelper.ParameterizedTypeImpl(
                            ParameterizedTypeImplTest.class,
                            ComponentTypeHelperTest.class,
                            new Type[] {String.class}
                        ),
                        new Type[0]
                    ),
                    getFieldType("f4"),
                    "marcono1234.gson.recordadapter.ComponentTypeHelperTest$ParameterizedTypeImplTest<java.lang.String>$Inner"
                ),
                new TestData(
                    new ComponentTypeHelper.ParameterizedTypeImpl(
                        InnerWithParam.class,
                        new ComponentTypeHelper.ParameterizedTypeImpl(
                            ParameterizedTypeImplTest.class,
                            ComponentTypeHelperTest.class,
                            new Type[] {String.class}
                        ),
                        new Type[] {Boolean.class}
                    ),
                    getFieldType("f5"),
                    "marcono1234.gson.recordadapter.ComponentTypeHelperTest$ParameterizedTypeImplTest<java.lang.String>$InnerWithParam<java.lang.Boolean>"
                )
            );
        }
    }

    @Nested
    class WildcardTypeImplTest implements TypeImplTestBase {
        private List<?> f1;
        @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
        private List<? extends Object> f2;
        private List<? extends String> f3;
        private List<? extends List<String>> f4;
        private List<? super Object> f5;
        private List<? super String> f6;
        private List<? super List<String>> f7;

        private Type getFieldWildcardType(String fieldName) throws Exception {
            return ((ParameterizedType) getClass().getDeclaredField(fieldName).getGenericType()).getActualTypeArguments()[0];
        }

        @Override
        public Stream<TestData> createArguments() throws Exception {
            return Stream.of(
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[0], new Type[] {Object.class}),
                    getFieldWildcardType("f1"),
                    "?"
                ),
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[0], new Type[] {Object.class}),
                    getFieldWildcardType("f2"),
                    "?"
                ),
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[0], new Type[] {String.class}),
                    getFieldWildcardType("f3"),
                    "? extends java.lang.String"
                ),
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[0], new Type[] {new ComponentTypeHelper.ParameterizedTypeImpl(List.class, null, new Type[] {String.class})}),
                    getFieldWildcardType("f4"),
                    "? extends java.util.List<java.lang.String>"
                ),
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[] {Object.class}, new Type[] {Object.class}),
                    getFieldWildcardType("f5"),
                    "? super java.lang.Object"
                ),
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[] {String.class}, new Type[] {Object.class}),
                    getFieldWildcardType("f6"),
                    "? super java.lang.String"
                ),
                new TestData(
                    new ComponentTypeHelper.WildcardTypeImpl(new Type[] {new ComponentTypeHelper.ParameterizedTypeImpl(List.class, null, new Type[] {String.class})}, new Type[] {Object.class}),
                    getFieldWildcardType("f7"),
                    "? super java.util.List<java.lang.String>"
                )
            );
        }

        @Test
        void test_GettersCloneArrays() {
            WildcardType type = new ComponentTypeHelper.WildcardTypeImpl(new Type[] {String.class}, new Type[] {Object.class});
            Type[] lowerBounds = type.getLowerBounds();
            Type[] upperBounds = type.getUpperBounds();

            assertArrayEquals(new Type[] {String.class}, lowerBounds);
            assertArrayEquals(new Type[] {Object.class}, upperBounds);

            // Should create new arrays
            assertNotSame(lowerBounds, type.getLowerBounds());
            assertNotSame(upperBounds, type.getUpperBounds());
        }

        @Test
        void testNew_NoUpperBounds() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> new ComponentTypeHelper.WildcardTypeImpl(new Type[0], new Type[0]));
            assertEquals("At least Object is required as upper bound", e.getMessage());
        }

        @Test
        void testNew_LowerBoundsWithNonObjectUpperBound() {
            Exception e = assertThrows(IllegalArgumentException.class, () -> new ComponentTypeHelper.WildcardTypeImpl(new Type[] {String.class}, new Type[] {Integer.class}));
            assertEquals("Malformed bounds: lower=[class java.lang.String], upper=[class java.lang.Integer]", e.getMessage());
        }
    }
}
