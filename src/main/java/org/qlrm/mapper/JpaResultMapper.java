package org.qlrm.mapper;

import javax.persistence.Query;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JpaResultMapper extends ResultMapper {

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_BOX_TYPE_MAP = new HashMap<>();

    static {
        PRIMITIVE_TO_BOX_TYPE_MAP.put(int.class, Integer.class);
        PRIMITIVE_TO_BOX_TYPE_MAP.put(long.class, Long.class);
        PRIMITIVE_TO_BOX_TYPE_MAP.put(byte.class, Byte.class);
        PRIMITIVE_TO_BOX_TYPE_MAP.put(boolean.class, Boolean.class);
        PRIMITIVE_TO_BOX_TYPE_MAP.put(char.class, Character.class);
        PRIMITIVE_TO_BOX_TYPE_MAP.put(float.class, Float.class);
        PRIMITIVE_TO_BOX_TYPE_MAP.put(double.class, Double.class);
    }

    /**
     * Returns a list of objects from a {@link javax.persistence.Query}
     *
     * @param query {@link Query}
     * @param clazz {@link Class}
     * @param <T>   Type
     * @return List of objects
     */
    public <T> List<T> list(Query query, Class<T> clazz) {
        List<T> result = new ArrayList<>();

        List<?> list = postProcessResultList(query.getResultList());

        if (list != null && !list.isEmpty()) {
            Object objects = list.get(0);
            Constructor<?> ctor;
            if (objects instanceof Object[]) {
                ctor = findConstructor(clazz, (Object[]) objects);

                List<Object[]> objectArrays = (List<Object[]>) Objects.requireNonNull(list);
                for (Object[] obj : objectArrays) {
                    result.add(createInstance(ctor, obj));
                }
            } else {
                ctor = findConstructor(clazz, objects);

                for (Object obj : Objects.requireNonNull(list)) {
                    result.add(createInstance(ctor, new Object[]{obj}));
                }
            }
        }
        return result;
    }

    /**
     * Returns on object from {@link javax.persistence.Query}
     *
     * @param query {@link Query}
     * @param clazz {@link Class}
     * @param <T>   Type
     * @return List of objects
     */
    public <T> T uniqueResult(Query query, Class<T> clazz) {
        Object[] rec = postProcessSingleResult(query.getSingleResult());
        Constructor<?> ctor = findConstructor(clazz, rec);

        return createInstance(ctor, rec);
    }


    @SuppressWarnings("unchecked")
    private List<Object[]> postProcessResultList(List<?> rawResults) {
        List<Object[]> result = new ArrayList<>();

        if (rawResults.size() == 1) {
            for (Object rawResult : rawResults) {
                result.add(postProcessSingleResult(rawResult));
            }
        } else {
            result = (List<Object[]>) rawResults;
        }

        return result;
    }

    private Object[] postProcessSingleResult(Object rawResult) {
        return rawResult instanceof Object[] ? (Object[]) rawResult
                : new Object[]{rawResult};
    }

    private Constructor<?> findConstructor(Class<?> clazz, Object... args) {
        Constructor<?> result = null;
        final Constructor<?>[] ctors = clazz.getDeclaredConstructors();

        // More stable check
        if (ctors.length == 1 && ctors[0].getParameterTypes().length == args.length) {
            // If there is only one constructor we take that
            result = ctors[0];
        }
        if (ctors.length > 1) {
            NEXT_CONSTRUCTOR:
            for (Constructor<?> ctor : ctors) {
                final Class<?>[] parameterTypes = postProcessConstructorParameterTypes(ctor
                        .getParameterTypes());
                if (parameterTypes.length == args.length) {
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (args[i] != null) {
                            Class<?> argType = convertToBoxTypeIfPrimitive(args[i].getClass());
                            if (!parameterTypes[i].isAssignableFrom(argType)) {
                                continue NEXT_CONSTRUCTOR;
                            }
                        }
                    }
                    result = ctor;
                    break;
                }
            }
        }
        if (null == result) {
            StringBuilder sb = new StringBuilder("No constructor taking:\n");
            for (Object object : args) {
                if (object != null) {
                    sb.append("\t").append(object.getClass().getName())
                            .append("\n");
                }
            }
            throw new RuntimeException(sb.toString());
        }
        return result;
    }

    /**
     * <p>
     * According to the JLS primitive types are not assignable to their box type
     * counterparts. E. g. int.class.isAssignableFrom(Integer.class) returns
     * false.
     * </p>
     * <p>
     * In order to make the isAssignable check in findConstructors work with
     * primitives, the check uses this method to convert possible primitive
     * constructor argument types to their box type counterparts.
     * </p>
     */
    private Class<?>[] postProcessConstructorParameterTypes(Class<?>[] rawParameterTypes) {
        Class<?>[] result = new Class<?>[rawParameterTypes.length];
        for (int i = 0; i < rawParameterTypes.length; i++) {
            Class<?> currentType = rawParameterTypes[i];
            result[i] = convertToBoxTypeIfPrimitive(currentType);
        }

        return result;
    }

    /**
     * @return The box type matching the provided primitive type or
     * <code>primitiveType</code> if no match could be found (e.g. the provided value was not a primitive type).
     */
    private Class<?> convertToBoxTypeIfPrimitive(Class<?> primitiveType) {
        return PRIMITIVE_TO_BOX_TYPE_MAP.getOrDefault(primitiveType, primitiveType);
    }
}
