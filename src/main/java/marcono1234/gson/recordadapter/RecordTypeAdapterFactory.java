package marcono1234.gson.recordadapter;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gson {@link TypeAdapterFactory} implementation for Record classes (Java 16 feature).
 *
 * <p>This factory can either be {@linkplain com.google.gson.GsonBuilder#registerTypeAdapterFactory(TypeAdapterFactory) registered with a <code>GsonBuilder</code>}:
 * <pre>{@code
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapterFactory(RecordTypeAdapterFactory.DEFAULT)
 *     .create();
 * }</pre>
 * Or it can be referenced using a {@link JsonAdapter @JsonAdapter} annotation placed on a Record class:
 * <pre>{@code
 * @JsonAdapter(RecordTypeAdapterFactory.class)
 * record MyRecord(int i) { }
 * }</pre>
 *
 * <p>A default instance of this factory can be obtained from {@link #DEFAULT}, and customized factory instances
 * can be created using {@link #builder()}. The (default) implementation differs from Gson's reflection based
 * serialization in some aspects, but by using the factory builder almost identical behavior can be achieved.
 *
 * <p>For serialization the component accessor methods are used to obtain the component values, and for
 * deserialization the canonical constructor of the Record class is used to create the Record instance. Usage of
 * {@link SerializedName @SerializedName} and {@link JsonAdapter @JsonAdapter} on Record components is supported.
 * The type adapters created by this factory are 'null-safe'; trying to serialize a Java {@code null} will write
 * a JSON null, and trying to deserialize a JSON null will return a Java {@code null}.
 *
 * <p>The implementation is in parts based on the discussion in <a href="https://github.com/google/gson/issues/1794">Gson issue #1794</a>.
 *
 * @see com.google.gson.GsonBuilder#registerTypeAdapterFactory(TypeAdapterFactory)
 */
// Note: Does not work well together with how com.google.gson.internal.bind.TypeAdapterRuntimeTypeWrapper.write
// chooses adapter because it does not know that the adapters of this factory are also reflection-based
public class RecordTypeAdapterFactory implements TypeAdapterFactory {
    private static final boolean DEFAULT_SERIALIZE_RUNTIME_COMPONENT_TYPES = false;
    private static final boolean DEFAULT_ALLOW_MISSING_COMPONENT_VALUES = false;
    private static final boolean DEFAULT_ALLOW_UNKNOWN_PROPERTIES = false;
    private static final boolean DEFAULT_ALLOW_DUPLICATE_COMPONENT_VALUES = false;
    private static final boolean DEFAULT_ALLOW_JSON_NULL_FOR_PRIMITIVES = false;
    private static final RecordComponentNamingStrategy DEFAULT_NAMING_STRATEGY = RecordComponentNamingStrategy.IDENTITY;
    private static final JsonAdapterCreator DEFAULT_JSON_ADAPTER_CREATOR = JsonAdapterCreator.DEFAULT_CONSTRUCTOR_INVOKER;

    /**
     * Default instance of this factory. This instance
     * <ul>
     *     <li>always serializes the compile-time type of Record components (see also {@link Builder#serializeRuntimeComponentTypes() serializeRuntimeComponentTypes()})</li>
     *     <li>does not allow missing Record component values (see also {@link Builder#allowMissingComponentValues() allowMissingComponentValues()})</li>
     *     <li>does not allow unknown JSON properties (see also {@link Builder#allowUnknownProperties() allowUnknownProperties()})</li>
     *     <li>does not allow duplicate Record component values (see also {@link Builder#allowDuplicateComponentValues() allowDuplicateComponentValues()})</li>
     *     <li>does not allow JSON null for Record components of primitive types (see also {@link Builder#allowJsonNullForPrimitiveComponents() allowJsonNullForPrimitiveComponents()})</li>
     *     <li>uses {@link RecordComponentNamingStrategy#IDENTITY}, that means JSON property names will be the same as
     *          the Record component names</li>
     *     <li>uses only {@link JsonAdapterCreator#DEFAULT_CONSTRUCTOR_INVOKER} as adapter creator</li>
     * </ul>
     *
     * @see #builder()
     */
    public static final RecordTypeAdapterFactory DEFAULT = builder().create();

    /**
     * Creates a factory with the same configuration as {@link #DEFAULT}.
     *
     * @deprecated
     *      Use {@link #DEFAULT}. This constructor only exists to support instance creation when referenced
     *      by {@link JsonAdapter}.
     */
    @Deprecated
    public RecordTypeAdapterFactory() {
        this(
            DEFAULT_SERIALIZE_RUNTIME_COMPONENT_TYPES,
            DEFAULT_ALLOW_MISSING_COMPONENT_VALUES,
            DEFAULT_ALLOW_UNKNOWN_PROPERTIES,
            DEFAULT_ALLOW_DUPLICATE_COMPONENT_VALUES,
            DEFAULT_ALLOW_JSON_NULL_FOR_PRIMITIVES,
            DEFAULT_NAMING_STRATEGY,
            List.of(DEFAULT_JSON_ADAPTER_CREATOR)
        );
    }

    /**
     * Builder for creating customized {@code RecordTypeAdapterFactory} instances.
     * New builders can be obtained using {@link RecordTypeAdapterFactory#builder()}. Adapter factory
     * instances can be created by calling {@link #create()} on a builder.
     */
    public static class Builder {
        private boolean serializeRuntimeComponentTypes = DEFAULT_SERIALIZE_RUNTIME_COMPONENT_TYPES;
        private boolean allowMissingComponentValues = DEFAULT_ALLOW_MISSING_COMPONENT_VALUES;
        private boolean allowUnknownProperties = DEFAULT_ALLOW_UNKNOWN_PROPERTIES;
        private boolean allowDuplicateComponentValues = DEFAULT_ALLOW_DUPLICATE_COMPONENT_VALUES;
        private boolean allowJsonNullForPrimitives = DEFAULT_ALLOW_JSON_NULL_FOR_PRIMITIVES;
        private RecordComponentNamingStrategy namingStrategy = DEFAULT_NAMING_STRATEGY;
        private final List<JsonAdapterCreator> jsonAdapterCreators;

        private Builder() {
            jsonAdapterCreators = new ArrayList<>();
            jsonAdapterCreators.add(DEFAULT_JSON_ADAPTER_CREATOR);
        }

        /**
         * Enables usage of the runtime type of Record component values for serialization. This applies to
         * all types, unlike Gson's reflection based type adapter which is always enabled but only considers
         * certain types, such as type variables. Note that enabling this setting can make serialization
         * and deserialization asymmetric; an object can be serialized to JSON but trying to deserialize
         * the JSON data again might fail. If the value of a Record component is {@code null} the type
         * adapter for the compile-time type will be used to serialize the {@code null}.
         *
         * <p>Here is an example showing the effect of this setting:
         * <pre>{@code
         * class Base {
         *     public int b;
         *
         *     public Base(int b) {
         *         this.b = b;
         *     }
         * }
         *
         * class Sub extends Base {
         *     public int s;
         *
         *     public Sub(int b, int s) {
         *         super(b);
         *         this.s = s;
         *     }
         * }
         *
         * record MyRecord(Base obj) { }
         * }</pre>
         * Without usage of the runtime type, serializing {@code new MyRecord(new Sub(1, 2))} would give
         * the JSON output {@code {"obj":{"b":1}}}. However, when usage of the runtime type is enabled
         * the JSON output will be {@code {"obj":{"b":1,"s":2}}}.
         *
         * <p>By default {@code RecordTypeAdapterFactory} uses the compile-time type for serialization
         * (and deserialization).
         *
         * @return <i>this</i>
         */
        public Builder serializeRuntimeComponentTypes() {
            serializeRuntimeComponentTypes = true;
            return this;
        }

        /**
         * Configures the {@code RecordTypeAdapterFactory} to allow missing values for Record
         * components during deserialization. For components of primitive type the respective default
         * value for the primitive type ({@code 0} or {@code false}) will be used as component value
         * instead. For all other types {@code null} will be used.
         *
         * <p>By default missing component values will cause the deserialization to fail.
         *
         * @return <i>this</i>
         */
        public Builder allowMissingComponentValues() {
            allowMissingComponentValues = true;
            return this;
        }

        /**
         * Configures the {@code RecordTypeAdapterFactory} to allow JSON properties during deserialization
         * which do not correspond to any Record component. Note that for unknown properties no duplicate
         * checks are performed (see also {@link #allowDuplicateComponentValues()}).
         *
         * <p>By default unknown properties will cause the deserialization to fail.
         *
         * @return <i>this</i>
         */
        public Builder allowUnknownProperties() {
            allowUnknownProperties = true;
            return this;
        }

        /**
         * Configures the {@code RecordTypeAdapterFactory} to allow duplicate Record component values
         * during deserialization. When multiple JSON properties specify a value for the same Record
         * component only the value of the last occurring property will be used.
         *
         * <p>It is not recommended to allow duplicate component values in security critical contexts.
         * Other components an application is interacting with might handle duplicate values differently,
         * for example they might use the value of the first (instead of the last) occurring property.
         * An adversary could exploit this by creating forged JSON data with duplicate component values.
         *
         * <p>By default duplicate component values will cause the deserialization to fail.
         *
         * @return <i>this</i>
         */
        public Builder allowDuplicateComponentValues() {
            allowDuplicateComponentValues = true;
            return this;
        }

        /**
         * Configures the {@code RecordTypeAdapterFactory} to allow JSON null values for Record components of
         * primitive type during deserialization. The respective default value for the primitive type ({@code 0}
         * or {@code false}) will be used as component value instead. JSON null for boxed types such as
         * {@code Integer} is always allowed.
         *
         * <p>By default JSON null for components of primitive type will cause the deserialization to fail.
         *
         * @return <i>this</i>
         */
        public Builder allowJsonNullForPrimitiveComponents() {
            allowJsonNullForPrimitives = true;
            return this;
        }

        /**
         * Specifies the naming strategy the {@code RecordTypeAdapterFactory} should use for all Record
         * components which are not annotated with {@link SerializedName @SerializedName}.
         *
         * <p>By default {@link RecordComponentNamingStrategy#IDENTITY} is used, that means the JSON
         * property names will be the same as the Record component names.
         *
         * @param namingStrategy
         *      the naming strategy to use for component names
         * @return <i>this</i>
         */
        public Builder withComponentNamingStrategy(RecordComponentNamingStrategy namingStrategy) {
            this.namingStrategy = Objects.requireNonNull(namingStrategy);
            return this;
        }

        /**
         * Registers an adapter creator for classes referenced by {@link JsonAdapter @JsonAdapter}
         * on Record components. Creators are called by the factory in reverse registration order,
         * the last registered creator will be called first. If a creator is unable to create an
         * instance the next creator (if any) is called. If no creator is able to create an instance
         * the creation of the Record type adapter fails.
         *
         * <p>Registering a custom adapter creator can be useful for cases where a singleton instance
         * of an adapter should be used, or when the adapter constructor is not accessible to this
         * library.
         *
         * <p>By default only {@link JsonAdapterCreator#DEFAULT_CONSTRUCTOR_INVOKER} is registered,
         * and will always be called as fallback in case no custom creator is able to create an
         * instance.
         *
         * @param jsonAdapterCreator
         *      the adapter creator to register
         * @return <i>this</i>
         */
        public Builder registerJsonAdapterCreator(JsonAdapterCreator jsonAdapterCreator) {
            jsonAdapterCreators.add(Objects.requireNonNull(jsonAdapterCreator));
            return this;
        }

        /**
         * Creates a {@code RecordTypeAdapterFactory} using the current configuration of this
         * builder.
         *
         * @return
         *      The created type adapter factory
         */
        public RecordTypeAdapterFactory create() {
            // Copy to not be affected by subsequent modifications
            List<JsonAdapterCreator> jsonAdapterCreators = new ArrayList<>(this.jsonAdapterCreators);
            // Reverse so last registered creator is used first
            Collections.reverse(jsonAdapterCreators);
            return new RecordTypeAdapterFactory(
                serializeRuntimeComponentTypes,
                allowMissingComponentValues,
                allowUnknownProperties,
                allowDuplicateComponentValues,
                allowJsonNullForPrimitives,
                namingStrategy,
                jsonAdapterCreators
            );
        }
    }

    /**
     * Returns a new {@code RecordTypeAdapterFactory} builder. The builder uses {@link #DEFAULT} as
     * starting point and allows customizing the behavior of the created type adapter factory.
     *
     * @return
     *      A new type adapter factory builder
     * @see #DEFAULT
     */
    public static Builder builder() {
        return new Builder();
    }

    private final boolean serializeRuntimeComponentTypes;
    private final boolean allowMissingComponentValues;
    private final boolean allowUnknownProperties;
    private final boolean allowDuplicateComponentValues;
    private final boolean allowJsonNullForPrimitives;
    private final RecordComponentNamingStrategy namingStrategy;
    private final List<JsonAdapterCreator> jsonAdapterCreators;

    private RecordTypeAdapterFactory(
        boolean serializeRuntimeComponentTypes,
        boolean allowMissingComponentValues,
        boolean allowUnknownProperties,
        boolean allowDuplicateComponentValues,
        boolean allowJsonNullForPrimitives,
        RecordComponentNamingStrategy namingStrategy,
        List<JsonAdapterCreator> jsonAdapterCreators
    ) {
        this.serializeRuntimeComponentTypes = serializeRuntimeComponentTypes;
        this.allowMissingComponentValues = allowMissingComponentValues;
        this.allowUnknownProperties = allowUnknownProperties;
        this.allowDuplicateComponentValues = allowDuplicateComponentValues;
        this.allowJsonNullForPrimitives = allowJsonNullForPrimitives;
        this.namingStrategy = namingStrategy;
        this.jsonAdapterCreators = jsonAdapterCreators;
        assert !jsonAdapterCreators.isEmpty();
    }

    private static Field getComponentField(RecordComponent component) throws RecordTypeAdapterException {
        try {
            return component.getDeclaringRecord().getDeclaredField(component.getName());
        } catch (NoSuchFieldException e) {
            throw new RecordTypeAdapterException("Unexpected: Failed finding component field for " + component);
        }
    }

    private record ComponentNames(String serializationName, Set<String> deserializationNames) { }

    private ComponentNames getComponentNames(RecordComponent component) throws RecordTypeAdapterException {
        // SerializedName is not applicable to RECORD_COMPONENT, but according to the JLS because it is applicable
        // to METHOD and FIELD it is propagated to the members, see https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10.1
        // However, accessor method can be overridden in which case annotation is not propagated, so have to
        // get it from private component field
        SerializedName serializedName = getComponentField(component).getAnnotation(SerializedName.class);
        SerializedName accessorSerializedName = component.getAccessor().getAnnotation(SerializedName.class);
        if (serializedName == null) {
            if (accessorSerializedName != null) {
                throw new RecordTypeAdapterException("@SerializedName on accessor method is not supported; place it on the corresponding record component instead");
            }

            String name = Objects.requireNonNull(namingStrategy.translateName(component));
            // Set.of performs the null check
            return new ComponentNames(name, Set.of(name));
        }

        // Detect mismatching annotation on custom accessor
        if (accessorSerializedName != null && !serializedName.equals(accessorSerializedName)) {
            throw new RecordTypeAdapterException("Using different @SerializedName on accessor than on corresponding record component is not supported");
        }

        String name = serializedName.value();
        String[] alternates = serializedName.alternate();
        if (alternates.length == 0) {
            return new ComponentNames(name, Set.of(name));
        }

        Set<String> deserializationNames = new HashSet<>();
        deserializationNames.add(name);
        for (String alternate : alternates) {
            if (!deserializationNames.add(alternate)) {
                throw new RecordTypeAdapterException("Duplicate property name '" + alternate + "' for " + getComponentDisplayString(component));
            }
        }
        return new ComponentNames(name, deserializationNames);
    }

    private static boolean needsRuntimeTypeTypeAdapter(Type type) {
        if (type instanceof Class<?> c) {
            // Primitive and final classes cannot be subclassed
            return !c.isPrimitive() && !Modifier.isFinal(c.getModifiers());
        } else if (type instanceof ParameterizedType parameterizedType) {
            // RuntimeTypeTypeAdapter uses value.getClass(), so if raw type does not need
            // runtime-type type adapter, then parameterized does not need it either
            return needsRuntimeTypeTypeAdapter(parameterizedType.getRawType());
        } else if (type instanceof GenericArrayType arrayType) {
            // For arrays cannot assign array of supertype (e.g. String[] s = new Object[1]),
            // so only need to check whether the component type needs runtime-type type adapter
            return needsRuntimeTypeTypeAdapter(arrayType.getGenericComponentType());
        }
        return true;
    }

    // Matches behavior of com.google.gson.internal.bind.ReflectiveTypeAdapterFactory.createBoundField
    private TypeAdapter<?> getAdapter(RecordComponent component, Type componentType, Gson gson) throws RecordTypeAdapterException {
        TypeToken<?> componentTypeToken = TypeToken.get(componentType);
        // @JsonAdapter only has FIELD as target, so need to get annotation from component field
        JsonAdapter jsonAdapterAnnotation = getComponentField(component).getAnnotation(JsonAdapter.class);
        if (jsonAdapterAnnotation == null) {
            TypeAdapter<?> adapter = gson.getAdapter(componentTypeToken);
            if (serializeRuntimeComponentTypes && needsRuntimeTypeTypeAdapter(componentType)) {
                return new RuntimeTypeTypeAdapter<>(gson, adapter);
            } else {
                return adapter;
            }
        }

        Class<?> adapterType = jsonAdapterAnnotation.value();
        JsonAdapterCreator usedAdapterCreator = null;
        Object adapter = null;
        for (JsonAdapterCreator adapterCreator : jsonAdapterCreators) {
            Optional<Object> optAdapter;
            try {
                optAdapter = adapterCreator.create(adapterType);
            } catch (JsonAdapterCreator.AdapterCreationException e) {
                throw new RecordTypeAdapterException("Creator " + adapterCreator + " failed creating instance of adapter " + adapterType + " for " + getComponentDisplayString(component), e);
            }
            if (optAdapter.isPresent()) {
                adapter = optAdapter.get();
                usedAdapterCreator = adapterCreator;
                break;
            }
        }
        if (usedAdapterCreator == null) {
            String creatorsString = jsonAdapterCreators.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            throw new RecordTypeAdapterException("None of the creators can create an instance of adapter " + adapterType + " for " + getComponentDisplayString(component) + "; registered creators: " + creatorsString);
        }

        TypeAdapter<?> typeAdapter;
        if (adapter instanceof TypeAdapter) {
            typeAdapter = (TypeAdapter<?>) adapter;
        } else if (adapter instanceof TypeAdapterFactory factory) {
            typeAdapter = factory.create(gson, componentTypeToken);
            if (typeAdapter == null) {
                throw new RecordTypeAdapterException("Factory " + factory + " of type " + factory.getClass().getName() + " does not support type " + componentTypeToken + " of component " + getComponentDisplayString(component));
            }
        } else if (adapter instanceof JsonSerializer || adapter instanceof JsonDeserializer) {
            @SuppressWarnings("unchecked")
            JsonSerializer<Object> serializer = adapter instanceof JsonSerializer ? (JsonSerializer<Object>) adapter : null;
            @SuppressWarnings("unchecked")
            JsonDeserializer<Object> deserializer = adapter instanceof JsonDeserializer ? (JsonDeserializer<Object>) adapter : null;
            @SuppressWarnings("unchecked")
            TypeAdapter<?> r = new TreeTypeAdapter<>(serializer, deserializer, gson, (TypeToken<Object>) componentTypeToken);
            typeAdapter = r;
        } else {
            throw new RecordTypeAdapterException("Adapter " + adapter + " of type " + adapter.getClass().getName() + " created by " + usedAdapterCreator + " for " + getComponentDisplayString(component) + " is not supported");
        }

        return jsonAdapterAnnotation.nullSafe() ? typeAdapter.nullSafe() : typeAdapter;
    }

    private static String getComponentDisplayString(RecordComponent component) {
        return component.getDeclaringRecord().getName() + "." + component.getName();
    }

    /**
     * {@inheritDoc}
     *
     * @throws RecordTypeAdapterException
     *      If creation of the type adapter fails, for example because the Record class is
     *      incorrectly annotated or its canonical constructor is not accessible.
     */
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) throws RecordTypeAdapterException {
        Class<?> rawType = type.getRawType();
        if (!rawType.isRecord()) {
            return null;
        }

        RecordComponent[] components = rawType.getRecordComponents();
        Constructor<?> constructor = getCanonicalConstructor(rawType, components);

        Type[] componentTypes = new Type[components.length];
        Method[] accessors = new Method[components.length];
        String[] componentSerializationNames = new String[components.length];
        Map<String, Integer> componentDeserializationNames = new HashMap<>();
        TypeAdapter<?>[] componentAdapters = new TypeAdapter<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            componentTypes[i] = component.getGenericType();
            Method accessor = component.getAccessor();
            try {
                accessor.setAccessible(true);
            } catch (InaccessibleObjectException e) {
                // Should be impossible because getting canonical constructor would already have thrown
                // InaccessibleObjectException, but throw descriptive exception here nonetheless
                throw new RecordTypeAdapterException("Cannot access accessor method for " + getComponentDisplayString(component) + "; either change the visibility of the record class to `public` or open it to this library", e);
            }
            accessors[i] = accessor;

            ComponentNames componentNames = getComponentNames(component);
            String serializationName = componentNames.serializationName;
            for (int j = 0; j < i; j++) {
                if (componentSerializationNames[j].equals(serializationName)) {
                    throw new RecordTypeAdapterException("Property name '" + serializationName + "' for " + getComponentDisplayString(component) + " clashes with name of other component");
                }
            }
            componentSerializationNames[i] = serializationName;
            for (String deserializeName : componentNames.deserializationNames) {
                if (componentDeserializationNames.put(deserializeName, i) != null) {
                    throw new RecordTypeAdapterException("Property name '" + deserializeName + "' for " + getComponentDisplayString(component) + " clashes with name of other component");
                }
            }
        }

        componentTypes = ComponentTypeHelper.resolveComponentTypes(type, componentTypes);
        for (int i = 0; i < components.length; i++) {
            componentAdapters[i] = getAdapter(components[i], componentTypes[i], gson);
        }

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                out.beginObject();
                for (int i = 0; i < accessors.length; i++) {
                    Object componentValue;
                    try {
                        // Use accessor instead of underlying field because accessor is public but underlying
                        // field is private; additionally accessor might transform result
                        componentValue = accessors[i].invoke(value);
                    } catch (IllegalAccessException | IllegalArgumentException | NullPointerException e) {
                        // TODO: Gson has no well fitting exception class?
                        throw new JsonParseException("Failed getting component value", e);
                    } catch (InvocationTargetException e) {
                        // TODO: Gson has no well fitting exception class?
                        throw new JsonParseException("Failed getting component value", e.getCause());
                    }

                    out.name(componentSerializationNames[i]);
                    @SuppressWarnings("unchecked")
                    TypeAdapter<Object> adapter = (TypeAdapter<Object>) componentAdapters[i];
                    adapter.write(out, componentValue);
                }
                out.endObject();
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.skipValue();
                    return null;
                }

                Object[] values = new Object[components.length];
                boolean[] hasValue = new boolean[values.length];
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    Integer i = componentDeserializationNames.get(name);
                    if (i == null) {
                        if (allowUnknownProperties) {
                            in.skipValue();
                            continue;
                        }
                        throw new JsonParseException("Unknown property '" + name + "' for " + rawType + " at JSON path " + in.getPath());
                    }
                    RecordComponent component = components[i];
                    if (!allowDuplicateComponentValues && hasValue[i]) {
                        // Uses component name because especially when using @SerializedName it might not be
                        // obvious why a duplicate value exists
                        throw new JsonParseException("Duplicate value for " + getComponentDisplayString(component) + " provided by property '" + name + "' at JSON path " + in.getPath());
                    }
                    Class<?> componentType = component.getType();
                    boolean isPrimitive = componentType.isPrimitive();
                    if (!allowJsonNullForPrimitives && isPrimitive && in.peek() == JsonToken.NULL) {
                        throw new JsonParseException("JSON null is not allowed for primitive " + getComponentDisplayString(component) + " provided by property '" + name + "' at JSON path " + in.getPath());
                    }
                    Object value = componentAdapters[i].read(in);
                    // Either JSON null, or user specified custom adapter for primitive type returning null
                    if (isPrimitive && value == null) {
                        value = getPrimitiveDefaultValue(componentType);
                    }
                    values[i] = value;
                    hasValue[i] = true;
                }

                for (int i = 0; i < components.length; i++) {
                    if (!hasValue[i]) {
                        if (!allowMissingComponentValues) {
                            // JSON path here refers to last property
                            throw new JsonParseException("Missing value for " + getComponentDisplayString(components[i]) + "; last property is at JSON path " + in.getPath());
                        }
                        Class<?> componentType = components[i].getType();
                        if (componentType.isPrimitive()) {
                            values[i] = getPrimitiveDefaultValue(componentType);
                        }
                    }
                }

                in.endObject();
                try {
                    @SuppressWarnings("unchecked")
                    T result = (T) constructor.newInstance(values);
                    return result;
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
                    throw new JsonParseException("Failed creating record instance for " + rawType, e);
                } catch (InvocationTargetException e) {
                    throw new JsonParseException("Failed creating record instance for " + rawType, e.getCause());
                }
            }
        };
    }

    private static Constructor<?> getCanonicalConstructor(Class<?> recordType, RecordComponent[] components) throws RecordTypeAdapterException {
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }

        Constructor<?> constructor;
        // Get canonical constructor, see also https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10.4
        try {
            constructor = recordType.getDeclaredConstructor(types);
        } catch (NoSuchMethodException e) {
            throw new RecordTypeAdapterException("Unexpected: Failed finding canonical constructor for " + recordType, e);
        }
        try {
            constructor.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            // Constructor has the same visibility as record, so might not be accessible
            throw new RecordTypeAdapterException("Cannot access canonical constructor of " + recordType + "; either change the visibility of the record class to `public` or open it to this library", e);
        }
        return constructor;
    }

    private static final Byte DEFAULT_BYTE = (byte) 0;
    private static final Short DEFAULT_SHORT = (short) 0;
    private static final Integer DEFAULT_INT = 0;
    private static final Long DEFAULT_LONG = 0L;
    private static final Float DEFAULT_FLOAT = 0f;
    private static final Double DEFAULT_DOUBLE = 0d;
    private static final Character DEFAULT_CHAR = '\0';

    private static Object getPrimitiveDefaultValue(Class<?> c) {
        if (c == byte.class) return DEFAULT_BYTE;
        if (c == short.class) return DEFAULT_SHORT;
        if (c == int.class) return DEFAULT_INT;
        if (c == long.class) return DEFAULT_LONG;
        if (c == float.class) return DEFAULT_FLOAT;
        if (c == double.class) return DEFAULT_DOUBLE;
        if (c == boolean.class) return Boolean.FALSE;
        if (c == char.class) return DEFAULT_CHAR;
        throw new AssertionError("Not primitive: " + c);
    }
}
