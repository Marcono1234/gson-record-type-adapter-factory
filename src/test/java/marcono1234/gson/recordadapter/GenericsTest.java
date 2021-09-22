package marcono1234.gson.recordadapter;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static marcono1234.gson.recordadapter.RecordTypeAdapterFactoryTest.getDefaultAdapter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for generic Record classes */
class GenericsTest {
    record N(int i) { }
    record Generic<T>(T t) { }

    @Test
    void testRegularTypeArgument() throws IOException {
        TypeAdapter<Generic<N>> typeAdapter = getDefaultAdapter(new TypeToken<>() {});
        var object = new Generic<>(new N(1));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"t\":{\"i\":1}}", json);

        Generic<N> actual = typeAdapter.fromJson("{\"t\":{\"i\":1}}");
        assertEquals(object, actual);
    }

    @Test
    void testSelfRecursive() throws IOException {
        TypeAdapter<Generic<Generic<Generic<N>>>> typeAdapter = getDefaultAdapter(new TypeToken<>() {});
        var object = new Generic<>(new Generic<>(new Generic<>(new N(1))));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"t\":{\"t\":{\"t\":{\"i\":1}}}}", json);

        Generic<Generic<Generic<N>>> actual = typeAdapter.fromJson("{\"t\":{\"t\":{\"t\":{\"i\":1}}}}");
        assertEquals(object, actual);
    }

    record WildcardsWithTypeVariableBounds<T>(
        List<? extends T> l1,
        List<? super T> l2
    ) { }

    @Test
    void testWildcardWithTypeVariableBound() throws IOException {
        TypeAdapter<WildcardsWithTypeVariableBounds<N>> typeAdapter = getDefaultAdapter(new TypeToken<>() {});
        var object = new WildcardsWithTypeVariableBounds<>(List.of(new N(1)), List.of(new N(2)));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"l1\":[{\"i\":1}],\"l2\":[{\"i\":2}]}", json);

        // For `? super T` Object is the upper bound, so it is deserialized as Object
        var expected = new WildcardsWithTypeVariableBounds<>(List.of(new N(1)), List.of(Map.of("i", 2.0)));
        WildcardsWithTypeVariableBounds<N> actual = typeAdapter.fromJson("{\"l1\":[{\"i\":1}],\"l2\":[{\"i\":2}]}");
        assertEquals(expected, actual);
    }

    record WildcardWithoutUpperBound(Generic<?> g1, Generic<? super N> g2) { }

    @Test
    void testWildcardWithoutUpperBound() throws IOException {
        TypeAdapter<WildcardWithoutUpperBound> typeAdapter = getDefaultAdapter(WildcardWithoutUpperBound.class);
        String json = typeAdapter.toJson(new WildcardWithoutUpperBound(new Generic<>(new N(1)), new Generic<>(new N(2))));
        assertEquals("{\"g1\":{\"t\":{\"i\":1}},\"g2\":{\"t\":{\"i\":2}}}", json);

        WildcardWithoutUpperBound actual = typeAdapter.fromJson("{\"g1\":{\"t\":{\"i\":1}},\"g2\":{\"t\":{\"i\":2}}}");
        // Wildcard without upper bound was deserialized as Object
        assertTrue(actual.g1.t instanceof Map);
        assertTrue(actual.g2.t instanceof Map);
    }

    record WildcardWithUpperBound(Generic<? extends N> g) { }

    /**
     * Should use upper bound of wildcard for (de-)serialization
     */
    @Test
    void testWildcardWithUpperBound() throws IOException {
        TypeAdapter<WildcardWithUpperBound> typeAdapter = getDefaultAdapter(WildcardWithUpperBound.class);
        var object = new WildcardWithUpperBound(new Generic<>(new N(1)));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"g\":{\"t\":{\"i\":1}}}", json);

        WildcardWithUpperBound actual = typeAdapter.fromJson("{\"g\":{\"t\":{\"i\":1}}}");
        assertEquals(object, actual);
    }

    record GenericWithBound<T extends N>(T t) { }

    @Test
    void testRawForTypeVariableWithBound() throws IOException {
        // Uses raw type `GenericWithBound.class`
        @SuppressWarnings("rawtypes")
        TypeAdapter<GenericWithBound> typeAdapter = getDefaultAdapter(GenericWithBound.class);
        var object = new GenericWithBound<>(new N(1));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"t\":{\"i\":1}}", json);

        GenericWithBound<?> actual = typeAdapter.fromJson("{\"t\":{\"i\":1}}");
        assertEquals(object, actual);
    }

    record WildcardForTypeVariableWithBound(GenericWithBound<?> g) { }

    /**
     * Should use bound of type variable for (de-)serialization of wildcard without bounds
     */
    @Test
    void testWildcardForTypeVariableWithBound() throws IOException {
        TypeAdapter<WildcardForTypeVariableWithBound> typeAdapter = getDefaultAdapter(WildcardForTypeVariableWithBound.class);
        var object = new WildcardForTypeVariableWithBound(new GenericWithBound<>(new N(1)));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"g\":{\"t\":{\"i\":1}}}", json);

        WildcardForTypeVariableWithBound actual = typeAdapter.fromJson("{\"g\":{\"t\":{\"i\":1}}}");
        assertEquals(object, actual);
    }

    record RecursiveGeneric<T>(RecursiveGeneric<T> r, T t) { }

    @Test
    void testRecursive() throws IOException {
        TypeAdapter<RecursiveGeneric<N>> typeAdapter = getDefaultAdapter(new TypeToken<>() {});
        var object = new RecursiveGeneric<>(new RecursiveGeneric<>(null, new N(1)), new N(2));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"r\":{\"r\":null,\"t\":{\"i\":1}},\"t\":{\"i\":2}}", json);

        RecursiveGeneric<N> actual = typeAdapter.fromJson("{\"r\":{\"r\":null,\"t\":{\"i\":1}},\"t\":{\"i\":2}}");
        assertEquals(object, actual);
    }

    record SelfCyclic<C extends Generic<C>>(C c) { }

    // Not sure if it is actually possible to provide valid (non-raw or wildcard) type arguments for SelfCyclic

    @Test
    void testSelfCyclicRaw() throws IOException {
        @SuppressWarnings("rawtypes")
        TypeAdapter<SelfCyclic> typeAdapter = getDefaultAdapter(SelfCyclic.class);
        // Due to usage of raw types and unchecked type conversion can use N instead of Generic as argument
        @SuppressWarnings({"rawtypes", "unchecked"})
        var object = new SelfCyclic(new Generic<>(new Generic<>(new N(2))));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"c\":{\"t\":{\"t\":{\"i\":2}}}}", json);

        var actual = typeAdapter.fromJson("{\"c\":{\"t\":{\"t\":{\"i\":2}}}}");
        // Due to cycle, adapter will use Generic<Generic> as type, so N inside it will be deserialized as Object
        @SuppressWarnings({"rawtypes", "unchecked"})
        var expected = new SelfCyclic(new Generic<>(new Generic<>(Map.of("i", 2.0))));
        assertEquals(expected, actual);
    }

    record WildcardsForSelfCyclic(SelfCyclic<?> c) { }

    @Test
    void testSelfCyclicWildcard() throws IOException {
        TypeAdapter<WildcardsForSelfCyclic> typeAdapter = getDefaultAdapter(WildcardsForSelfCyclic.class);
        // Due to usage of raw types and unchecked type conversion can use N instead of Generic as argument
        @SuppressWarnings({"rawtypes", "unchecked"})
        var object = new WildcardsForSelfCyclic(new SelfCyclic(new Generic<>(new Generic<>(new N(2)))));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"c\":{\"c\":{\"t\":{\"t\":{\"i\":2}}}}}", json);

        WildcardsForSelfCyclic actual = typeAdapter.fromJson("{\"c\":{\"c\":{\"t\":{\"t\":{\"i\":2}}}}}");
        // Due to cycle, adapter will use Generic<Generic> as type, so N inside it will be deserialized as Object
        @SuppressWarnings({"rawtypes", "unchecked"})
        var expected = new WildcardsForSelfCyclic(new SelfCyclic(new Generic<>(new Generic<>(Map.of("i", 2.0)))));
        assertEquals(expected, actual);
    }

    record Cyclic<C1 extends Generic<C2>, C2 extends C1>(
        C1 c1,
        C2 c2
    ) { }

    // Not sure if it is actually possible to provide valid (non-raw or wildcard) type arguments for Cyclic

    @Test
    void testCyclicRaw() throws IOException {
        @SuppressWarnings("rawtypes")
        TypeAdapter<Cyclic> typeAdapter = getDefaultAdapter(Cyclic.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        var object = new Cyclic(new Generic<>(null), new Generic<>(null));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"c1\":{\"t\":null},\"c2\":{\"t\":null}}", json);

        var actual = typeAdapter.fromJson("{\"c1\":{\"t\":null},\"c2\":{\"t\":null}}");
        assertEquals(object, actual);
    }

    record WildcardsForCyclic(Cyclic<?, ?> c) { }

    @Test
    void testCyclicWildcard() throws IOException {
        TypeAdapter<WildcardsForCyclic> typeAdapter = getDefaultAdapter(WildcardsForCyclic.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        var object = new WildcardsForCyclic(new Cyclic(new Generic<>(null), new Generic<>(null)));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"c\":{\"c1\":{\"t\":null},\"c2\":{\"t\":null}}}", json);

        WildcardsForCyclic actual = typeAdapter.fromJson("{\"c\":{\"c1\":{\"t\":null},\"c2\":{\"t\":null}}}");
        assertEquals(object, actual);
    }

    record TypeVariableBoundChains<A1 extends A2, A2 extends M, M, B2 extends M, B1 extends B2>(
        A1 a1,
        A2 a2,
        M m,
        B2 b2,
        B1 b1
    ) { }

    // N should be used as type for all wildcards
    record WildcardsForBoundChains(TypeVariableBoundChains<?, ?, N, ?, ?> t) { }

    @Test
    void testWildcardBoundChains() throws IOException {
        TypeAdapter<WildcardsForBoundChains> typeAdapter = getDefaultAdapter(WildcardsForBoundChains.class);
        WildcardsForBoundChains object = new WildcardsForBoundChains(new TypeVariableBoundChains<>(new N(1), new N(2), new N(3), new N(4), new N(5)));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"t\":{\"a1\":{\"i\":1},\"a2\":{\"i\":2},\"m\":{\"i\":3},\"b2\":{\"i\":4},\"b1\":{\"i\":5}}}", json);

        WildcardsForBoundChains actual = typeAdapter.fromJson("{\"t\":{\"a1\":{\"i\":1},\"a2\":{\"i\":2},\"m\":{\"i\":3},\"b2\":{\"i\":4},\"b1\":{\"i\":5}}}");
        assertEquals(object, actual);
    }

    // M has upper bound `N`
    record TypeVariableBoundChainsFinalBound<A1 extends A2, A2 extends M, M extends N, B2 extends M, B1 extends B2>(
        A1 a1,
        A2 a2,
        M m,
        B2 b2,
        B1 b1
    ) { }

    @Test
    void testRawBoundChains() throws IOException {
        @SuppressWarnings("rawtypes")
        TypeAdapter<TypeVariableBoundChainsFinalBound> typeAdapter = getDefaultAdapter(TypeVariableBoundChainsFinalBound.class);
        var object = new TypeVariableBoundChainsFinalBound<>(new N(1), new N(2), new N(3), new N(4), new N(5));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"a1\":{\"i\":1},\"a2\":{\"i\":2},\"m\":{\"i\":3},\"b2\":{\"i\":4},\"b1\":{\"i\":5}}", json);

        var actual = typeAdapter.fromJson("{\"a1\":{\"i\":1},\"a2\":{\"i\":2},\"m\":{\"i\":3},\"b2\":{\"i\":4},\"b1\":{\"i\":5}}");
        assertEquals(object, actual);
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    class InnerWithTypeVariable<T extends N> {
        Generic<T> g;
        Generic<? extends T> g2;

        public InnerWithTypeVariable(Generic<T> g, Generic<T> g2) {
            this.g = g;
            this.g2 = g2;
        }
    }

    /** Unrelated type variables should be resolved using their bounds */
    @Test
    void testUnrelatedTypeVariable() throws IOException {
        // Type variable `T` of InnerWithTypeVariable has no type argument here
        @SuppressWarnings("rawtypes")
        TypeAdapter<InnerWithTypeVariable> typeAdapter = getDefaultAdapter(InnerWithTypeVariable.class);
        InnerWithTypeVariable<N> object = new InnerWithTypeVariable<>(new Generic<>(new N(1)), new Generic<>(new N(2)));
        String json = typeAdapter.toJson(object);
        assertEquals("{\"g\":{\"t\":{\"i\":1}},\"g2\":{\"t\":{\"i\":2}}}", json);

        InnerWithTypeVariable<?> actual = typeAdapter.fromJson("{\"g\":{\"t\":{\"i\":1}},\"g2\":{\"t\":{\"i\":2}}}");
        assertEquals(object.g, actual.g);
        assertEquals(object.g2, actual.g2);
    }
}
