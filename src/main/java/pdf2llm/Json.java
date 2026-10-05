package pdf2llm;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tiny pretty-printing JSON writer for maps, collections, records and scalars.
 * Records are serialised through their components, in declaration order.
 */
final class Json {
    private static final String INDENT = "  ";

    private Json() {
    }

    static String toJson(Object value) {
        var sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, Object value, int depth) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> quote(sb, s);
            case Boolean b -> sb.append(b.booleanValue());
            case Number n -> sb.append(n);
            case Collection<?> c -> writeList(sb, c, depth);
            case Map<?, ?> m -> writeMap(sb, m, depth);
            case Record r -> writeMap(sb, components(r), depth);
            default -> throw new IllegalArgumentException("Cannot serialise " + value.getClass().getName());
        }
    }

    private static Map<String, Object> components(Record record) {
        var fields = new LinkedHashMap<String, Object>();
        for (var component : record.getClass().getRecordComponents()) {
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                fields.put(component.getName(), accessor.invoke(record));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read record component " + component.getName(), e);
            }
        }
        return fields;
    }

    private static boolean isScalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static void writeList(StringBuilder sb, Collection<?> list, int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
        } else if (list.stream().allMatch(Json::isScalar)) {
            sb.append('[');
            var first = true;
            for (var item : list) {
                if (!first) sb.append(", ");
                first = false;
                write(sb, item, depth);
            }
            sb.append(']');
        } else {
            sb.append("[\n");
            var first = true;
            for (var item : list) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(INDENT.repeat(depth + 1));
                write(sb, item, depth + 1);
            }
            sb.append('\n').append(INDENT.repeat(depth)).append(']');
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append(INDENT.repeat(depth + 1));
            quote(sb, String.valueOf(entry.getKey()));
            sb.append(": ");
            write(sb, entry.getValue(), depth + 1);
        }
        sb.append('\n').append(INDENT.repeat(depth)).append('}');
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (ch < ' ') {
                        sb.append("\\u").append(Text.padded(ch, 4, 16));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }
}
