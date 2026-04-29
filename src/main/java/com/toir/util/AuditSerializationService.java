package com.toir.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditSerializationService {

    private static final int MAX_DEPTH = 3;
    private static final int MAX_COLLECTION_ITEMS = 50;

    private final ObjectMapper objectMapper;

    public <T> String toJson(T object) {
        if (object == null) return null;

        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            try {
                JsonNode safeNode = toAuditNode(object, new IdentityHashMap<>(), 0);
                return objectMapper.writeValueAsString(safeNode);
            } catch (Exception fallbackEx) {
                throw new RestException("Audit object serialization error, " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public String diff(Object oldObj, Object newObj) {
        try {
            if (oldObj == null && newObj == null) {
                return "{}";
            }
            JsonNode oldNode = toNode(oldObj);
            JsonNode newNode = toNode(newObj);

            if (!oldNode.isObject() || !newNode.isObject()) {
                if (Objects.equals(oldNode, newNode)) {
                    return "{}";
                }
                ObjectNode scalarDiff = objectMapper.createObjectNode();
                ObjectNode change = objectMapper.createObjectNode();
                change.set("old", oldNode);
                change.set("new", newNode);
                scalarDiff.set("value", change);
                return objectMapper.writeValueAsString(scalarDiff);
            }

            ObjectNode diff = objectMapper.createObjectNode();
            Set<String> fields = new HashSet<>();
            oldNode.fieldNames().forEachRemaining(fields::add);
            newNode.fieldNames().forEachRemaining(fields::add);

            for (String field : fields) {
                JsonNode oldValue = oldNode.get(field);
                JsonNode newValue = newNode.get(field);
                String oldStr = oldValue == null || oldValue.isNull() ? null : oldValue.toString();
                String newStr = newValue == null || newValue.isNull() ? null : newValue.toString();

                if (!Objects.equals(oldStr, newStr)) {
                    ObjectNode change = objectMapper.createObjectNode();
                    change.set("old", oldValue);
                    change.set("new", newValue);
                    diff.set(field, change);
                }
            }

            return objectMapper.writeValueAsString(diff);
        } catch (Exception e) {
            throw new RestException("Failed to build diff: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonNode toNode(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return objectMapper.readTree(trimmed);
                } catch (Exception ignored) {
                    // fall back to safe object conversion for plain strings
                }
            }
        }
        return toAuditNode(value, new IdentityHashMap<>(), 0);
    }

    private JsonNode toAuditNode(Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null) {
            return objectMapper.nullNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }

        Class<?> type = unwrapProxyClass(value.getClass());
        if (isSimpleType(type)) {
            return objectMapper.valueToTree(value);
        }
        if (depth >= MAX_DEPTH || visited.containsKey(value)) {
            return toReferenceNode(value);
        }
        visited.put(value, Boolean.TRUE);

        try {
            if (type.isArray()) {
                return toArrayNode(value, visited, depth + 1);
            }
            if (value instanceof Iterable<?> iterable) {
                return toIterableNode(iterable, visited, depth + 1);
            }
            if (value instanceof Map<?, ?> map) {
                return toMapNode(map, visited, depth + 1);
            }
            return toBeanNode(value, type, visited, depth + 1);
        } finally {
            visited.remove(value);
        }
    }

    private JsonNode toBeanNode(Object bean, Class<?> beanType, IdentityHashMap<Object, Boolean> visited, int depth) {
        ObjectNode node = objectMapper.createObjectNode();

        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(beanType, Object.class).getPropertyDescriptors()) {
                String name = pd.getName();
                Method getter = pd.getReadMethod();
                if (getter == null || "class".equals(name)) {
                    continue;
                }

                Object propVal;
                try {
                    if (!getter.canAccess(bean)) {
                        getter.setAccessible(true);
                    }
                    propVal = getter.invoke(bean);
                } catch (Exception ignored) {
                    continue;
                }

                if (propVal == null) {
                    node.putNull(name);
                    continue;
                }

                Class<?> propType = unwrapProxyClass(propVal.getClass());
                if (isSimpleType(propType)) {
                    node.set(name, objectMapper.valueToTree(propVal));
                } else if (propType.isArray()) {
                    node.set(name, toArrayNode(propVal, visited, depth));
                } else if (propVal instanceof Iterable<?> iterable) {
                    node.set(name, toIterableNode(iterable, visited, depth));
                } else if (propVal instanceof Map<?, ?> map) {
                    node.set(name, toMapNode(map, visited, depth));
                } else {
                    node.set(name, toReferenceNode(propVal));
                }
            }
        } catch (Exception e) {
            return toReferenceNode(bean);
        }

        if (node.isEmpty()) {
            return toReferenceNode(bean);
        }
        return node;
    }

    private ArrayNode toArrayNode(Object array, IdentityHashMap<Object, Boolean> visited, int depth) {
        ArrayNode arr = objectMapper.createArrayNode();
        int len = Array.getLength(array);
        int cap = Math.min(len, MAX_COLLECTION_ITEMS);
        for (int i = 0; i < cap; i++) {
            Object item = Array.get(array, i);
            arr.add(toCollectionElement(item, visited, depth));
        }
        if (len > cap) {
            arr.add("...truncated...");
        }
        return arr;
    }

    private ArrayNode toIterableNode(Iterable<?> iterable, IdentityHashMap<Object, Boolean> visited, int depth) {
        ArrayNode arr = objectMapper.createArrayNode();
        int count = 0;
        for (Object item : iterable) {
            if (count >= MAX_COLLECTION_ITEMS) {
                arr.add("...truncated...");
                break;
            }
            arr.add(toCollectionElement(item, visited, depth));
            count++;
        }
        return arr;
    }

    private JsonNode toMapNode(Map<?, ?> map, IdentityHashMap<Object, Boolean> visited, int depth) {
        ObjectNode node = objectMapper.createObjectNode();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (count >= MAX_COLLECTION_ITEMS) {
                node.put("_truncated", true);
                break;
            }
            String key = String.valueOf(entry.getKey());
            node.set(key, toCollectionElement(entry.getValue(), visited, depth));
            count++;
        }
        return node;
    }

    private JsonNode toCollectionElement(Object item, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (item == null) return objectMapper.nullNode();
        Class<?> t = unwrapProxyClass(item.getClass());
        if (isSimpleType(t)) {
            return objectMapper.valueToTree(item);
        }
        if (depth >= MAX_DEPTH) {
            return toReferenceNode(item);
        }
        return toAuditNode(item, visited, depth);
    }

    private JsonNode toReferenceNode(Object value) {
        ObjectNode ref = objectMapper.createObjectNode();
        Class<?> type = unwrapProxyClass(value.getClass());
        ref.put("_type", type.getSimpleName());
        Object id = extractId(value);
        if (id != null) {
            ref.set("id", objectMapper.valueToTree(id));
        }
        return ref;
    }

    private Object extractId(Object value) {
        try {
            Method idGetter = value.getClass().getMethod("getId");
            return idGetter.invoke(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Class<?> unwrapProxyClass(Class<?> type) {
        if (type == null) return Object.class;
        Class<?> current = type;
        while (current != null && current.getName().contains("$$")) {
            current = current.getSuperclass();
        }
        return current == null ? type : current;
    }

    private boolean isSimpleType(Class<?> type) {
        if (type == null) return true;
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Character.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type)
                || UUID.class.isAssignableFrom(type)
                || BigDecimal.class.isAssignableFrom(type)
                || BigInteger.class.isAssignableFrom(type)
                || java.util.Date.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type);
    }
}
