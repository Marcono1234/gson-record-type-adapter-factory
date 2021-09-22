package marcono1234.gson.recordadapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAdapterCreatorTest {
    static class DefaultConstructorInvokerTest {
        private final JsonAdapterCreator creator = JsonAdapterCreator.DEFAULT_CONSTRUCTOR_INVOKER;

        static class WithDefaultConstructor {
            // This field is used to verify that Unsafe was not used to create instance without calling constructor
            @SuppressWarnings("UnusedAssignment")
            boolean wasConstructorCalled = false;

            public WithDefaultConstructor() {
                wasConstructorCalled = true;
            }
        }

        @Test
        void test() throws Exception {
            var instance = creator.create(WithDefaultConstructor.class);
            assertTrue(instance.isPresent());
            assertTrue(((WithDefaultConstructor) instance.get()).wasConstructorCalled);
        }

        static class WithNotVisibleDefaultConstructor {
            private WithNotVisibleDefaultConstructor() { }
        }

        @Test
        void testNotVisible() throws Exception {
            var instance = creator.create(WithNotVisibleDefaultConstructor.class);
            assertFalse(instance.isPresent());
        }

        static class WithoutDefaultConstructor {
            @SuppressWarnings("unused")
            public WithoutDefaultConstructor(int i) { }
        }

        @Test
        void testNonExistent() throws Exception {
            var instance = creator.create(WithoutDefaultConstructor.class);
            assertFalse(instance.isPresent());
        }

        abstract static class AbstractClass {
            public AbstractClass() { }
        }

        @Test
        void testAbstractClass() throws Exception {
            var instance = creator.create(AbstractClass.class);
            assertFalse(instance.isPresent());
        }

        @SuppressWarnings("InnerClassMayBeStatic")
        class InnerClass {
            public InnerClass() { }
        }

        @Test
        void testInnerClass() throws Exception {
            var instance = creator.create(InnerClass.class);
            assertFalse(instance.isPresent());
        }

        static class ThrowingConstructor {
            public ThrowingConstructor() {
                throw new IllegalStateException("test");
            }
        }

        @Test
        void testThrowing() {
            Exception e = assertThrows(JsonAdapterCreator.AdapterCreationException.class, () -> creator.create(ThrowingConstructor.class));
            assertEquals("Failed invoking default constructor for " + ThrowingConstructor.class, e.getMessage());
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertEquals("test", e.getCause().getMessage());
        }
    }
}
