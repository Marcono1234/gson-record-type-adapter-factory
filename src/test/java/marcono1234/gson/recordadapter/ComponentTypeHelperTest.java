package marcono1234.gson.recordadapter;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}