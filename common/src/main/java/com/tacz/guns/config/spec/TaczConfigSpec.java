package com.tacz.guns.config.spec;

import com.tacz.guns.GunMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A small, loader-independent replacement for Forge's {@code ForgeConfigSpec}, keeping the same
 * builder vocabulary ({@code push/pop/comment/define/defineInRange/defineEnum}) and value types, and
 * writing the same TOML layout Forge produced, so existing {@code tacz-*.toml} files keep working.
 */
public final class TaczConfigSpec {
    private final List<ConfigValue<?>> values;
    private final Map<String, List<String>> sectionComments;
    private final Object lock = new Object();
    private Path file;
    private boolean loaded;

    private TaczConfigSpec(List<ConfigValue<?>> values, Map<String, List<String>> sectionComments) {
        this.values = Collections.unmodifiableList(values);
        this.sectionComments = sectionComments;
        for (ConfigValue<?> value : values) {
            value.spec = this;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<ConfigValue<?>> getValues() {
        return values;
    }

    /**
     * Loads the file (creating it from defaults if missing), corrects invalid values and writes the result back.
     */
    public void load(Path path) {
        synchronized (lock) {
            this.file = path;
            Map<String, Object> data = Map.of();
            if (Files.isRegularFile(path)) {
                try {
                    data = TomlIO.read(Files.readString(path, StandardCharsets.UTF_8));
                } catch (IOException | IllegalArgumentException e) {
                    Path backup = path.resolveSibling(path.getFileName() + ".bak");
                    GunMod.LOGGER.warn("Config file {} is unreadable ({}); backing it up to {} and using defaults", path, e.getMessage(), backup);
                    try {
                        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException copyError) {
                        GunMod.LOGGER.warn("Failed to back up config file {}", path, copyError);
                    }
                }
            }
            applyData(data);
            loaded = true;
            writeFile();
        }
    }

    /**
     * Writes the current values to the file this spec was loaded from.
     */
    public void save() {
        synchronized (lock) {
            if (file != null) {
                writeFile();
            }
        }
    }

    /**
     * @return the current values in TOML form, e.g. to synchronise a server config to clients
     */
    public String serialize() {
        synchronized (lock) {
            return TomlIO.write(values, sectionComments);
        }
    }

    /**
     * Replaces the in-memory values with the given TOML text without touching the file.
     */
    public void deserialize(String toml) {
        synchronized (lock) {
            applyData(TomlIO.read(toml));
        }
    }

    public void resetToDefaults() {
        synchronized (lock) {
            for (ConfigValue<?> value : values) {
                value.reset();
            }
        }
    }

    private void applyData(Map<String, Object> data) {
        for (ConfigValue<?> value : values) {
            value.load(TomlIO.lookup(data, value.path));
        }
    }

    private void writeFile() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, TomlIO.write(values, sectionComments), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            GunMod.LOGGER.error("Failed to write config file {}", file, e);
        }
    }

    public static final class Builder {
        private final Deque<String> path = new ArrayDeque<>();
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private final Map<String, List<String>> sectionComments = new LinkedHashMap<>();
        private List<String> pendingComment = List.of();

        public Builder comment(String... lines) {
            List<String> comment = new ArrayList<>();
            for (String line : lines) {
                Collections.addAll(comment, line.split("\n", -1));
            }
            this.pendingComment = comment;
            return this;
        }

        /**
         * Accepted for source compatibility; translation keys are not used by the file format.
         */
        public Builder translation(String key) {
            return this;
        }

        /**
         * Accepted for source compatibility; world restarts are not tracked.
         */
        public Builder worldRestart() {
            return this;
        }

        public Builder push(String name) {
            for (String part : name.split("\\.")) {
                path.addLast(part);
            }
            if (!pendingComment.isEmpty()) {
                sectionComments.put(String.join(".", path), pendingComment);
                pendingComment = List.of();
            }
            return this;
        }

        public Builder push(List<String> names) {
            names.forEach(this::push);
            return this;
        }

        public Builder pop() {
            return pop(1);
        }

        public Builder pop(int count) {
            if (count > path.size()) {
                throw new IllegalStateException("Attempted to pop " + count + " levels at " + path);
            }
            for (int i = 0; i < count; i++) {
                path.removeLast();
            }
            return this;
        }

        public BooleanValue define(String name, boolean defaultValue) {
            return add(new BooleanValue(fullPath(name), defaultValue));
        }

        public <T> ConfigValue<T> define(String name, T defaultValue) {
            Objects.requireNonNull(defaultValue, "defaultValue");
            return add(new ConfigValue<>(fullPath(name), defaultValue));
        }

        public IntValue defineInRange(String name, int defaultValue, int min, int max) {
            return add(new IntValue(fullPath(name), defaultValue, min, max));
        }

        public LongValue defineInRange(String name, long defaultValue, long min, long max) {
            return add(new LongValue(fullPath(name), defaultValue, min, max));
        }

        public DoubleValue defineInRange(String name, double defaultValue, double min, double max) {
            return add(new DoubleValue(fullPath(name), defaultValue, min, max));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String name, E defaultValue) {
            return add(new EnumValue<>(fullPath(name), defaultValue));
        }

        public TaczConfigSpec build() {
            if (!path.isEmpty()) {
                throw new IllegalStateException("Config builder has unbalanced push/pop: " + path);
            }
            return new TaczConfigSpec(values, sectionComments);
        }

        private List<String> fullPath(String name) {
            List<String> full = new ArrayList<>(path);
            Collections.addAll(full, name.split("\\."));
            return full;
        }

        private <V extends ConfigValue<?>> V add(V value) {
            value.comment = pendingComment;
            pendingComment = List.of();
            values.add(value);
            return value;
        }
    }

    public static class ConfigValue<T> implements Supplier<T> {
        final List<String> path;
        final T defaultValue;
        List<String> comment = List.of();
        TaczConfigSpec spec;
        private volatile T value;

        ConfigValue(List<String> path, T defaultValue) {
            this.path = List.copyOf(path);
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        @Override
        public T get() {
            return value;
        }

        public void set(T newValue) {
            this.value = correct(newValue);
        }

        public T getDefault() {
            return defaultValue;
        }

        public List<String> getPath() {
            return path;
        }

        /**
         * Writes the owning config file.
         */
        public void save() {
            if (spec != null) {
                spec.save();
            }
        }

        void reset() {
            this.value = defaultValue;
        }

        /**
         * Extra lines describing the accepted values, written under the comment.
         */
        List<String> constraintComment() {
            return List.of();
        }

        void load(Object raw) {
            T converted = raw == null ? null : convert(raw);
            this.value = converted == null ? defaultValue : correct(converted);
        }

        @SuppressWarnings("unchecked")
        T convert(Object raw) {
            if (defaultValue instanceof String) {
                return raw instanceof String ? (T) raw : null;
            }
            if (defaultValue instanceof List<?>) {
                return raw instanceof List<?> ? (T) raw : null;
            }
            return defaultValue.getClass().isInstance(raw) ? (T) raw : null;
        }

        T correct(T candidate) {
            return candidate == null ? defaultValue : candidate;
        }

        Object toFileValue() {
            return value;
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(List<String> path, boolean defaultValue) {
            super(path, defaultValue);
        }

        @Override
        Boolean convert(Object raw) {
            if (raw instanceof Boolean bool) {
                return bool;
            }
            if (raw instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
                return Boolean.parseBoolean(text);
            }
            return null;
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        private final int min;
        private final int max;

        IntValue(List<String> path, int defaultValue, int min, int max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        Integer convert(Object raw) {
            if (raw instanceof Number number) {
                double d = number.doubleValue();
                if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    return (int) d;
                }
            }
            return null;
        }

        @Override
        Integer correct(Integer candidate) {
            return candidate == null || candidate < min || candidate > max ? defaultValue : candidate;
        }

        @Override
        List<String> constraintComment() {
            return List.of("Range: " + min + " ~ " + max);
        }
    }

    public static final class LongValue extends ConfigValue<Long> {
        private final long min;
        private final long max;

        LongValue(List<String> path, long defaultValue, long min, long max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        Long convert(Object raw) {
            if (raw instanceof Number number) {
                double d = number.doubleValue();
                if (d == Math.rint(d)) {
                    return number.longValue();
                }
            }
            return null;
        }

        @Override
        Long correct(Long candidate) {
            return candidate == null || candidate < min || candidate > max ? defaultValue : candidate;
        }

        @Override
        List<String> constraintComment() {
            return List.of("Range: " + min + " ~ " + max);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        private final double min;
        private final double max;

        DoubleValue(List<String> path, double defaultValue, double min, double max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        Double convert(Object raw) {
            return raw instanceof Number number ? number.doubleValue() : null;
        }

        @Override
        Double correct(Double candidate) {
            return candidate == null || candidate.isNaN() || candidate < min || candidate > max ? defaultValue : candidate;
        }

        @Override
        List<String> constraintComment() {
            return List.of("Range: " + min + " ~ " + max);
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        private final Class<E> type;

        EnumValue(List<String> path, E defaultValue) {
            super(path, defaultValue);
            this.type = defaultValue.getDeclaringClass();
        }

        @Override
        E convert(Object raw) {
            if (raw instanceof String text) {
                for (E constant : type.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(text)) {
                        return constant;
                    }
                }
            } else if (raw instanceof Number number) {
                int ordinal = number.intValue();
                E[] constants = type.getEnumConstants();
                if (ordinal >= 0 && ordinal < constants.length) {
                    return constants[ordinal];
                }
            }
            return null;
        }

        @Override
        List<String> constraintComment() {
            List<String> names = new ArrayList<>();
            for (E constant : type.getEnumConstants()) {
                names.add(constant.name());
            }
            return List.of("Allowed Values: " + String.join(", ", names));
        }

        @Override
        Object toFileValue() {
            return get().name();
        }

        public Class<E> getEnumClass() {
            return type;
        }
    }

    /**
     * The subset of TOML that config files use: tables, dotted/quoted keys, strings, numbers,
     * booleans and (nested) arrays, with {@code #} comments.
     */
    static final class TomlIO {
        private TomlIO() {
        }

        static Object lookup(Map<String, Object> data, List<String> path) {
            Object current = data;
            for (String part : path) {
                if (!(current instanceof Map<?, ?> map)) {
                    return null;
                }
                current = map.get(part);
            }
            return current instanceof Map<?, ?> ? null : current;
        }

        static Map<String, Object> read(String text) {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> table = root;
            Parser parser = new Parser(text);
            while (true) {
                parser.skipWhitespaceCommentsAndNewlines();
                if (parser.atEnd()) {
                    return root;
                }
                if (parser.peek() == '[') {
                    parser.expect('[');
                    List<String> tablePath = parser.parseKey();
                    parser.skipInlineWhitespace();
                    parser.expect(']');
                    table = descend(root, tablePath);
                    parser.expectLineEnd();
                } else {
                    List<String> key = parser.parseKey();
                    parser.skipInlineWhitespace();
                    parser.expect('=');
                    parser.skipInlineWhitespace();
                    Object value = parser.parseValue();
                    Map<String, Object> target = descend(table, key.subList(0, key.size() - 1));
                    target.put(key.get(key.size() - 1), value);
                    parser.expectLineEnd();
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> descend(Map<String, Object> from, List<String> path) {
            Map<String, Object> current = from;
            for (String part : path) {
                Object next = current.computeIfAbsent(part, k -> new LinkedHashMap<String, Object>());
                if (!(next instanceof Map)) {
                    throw new IllegalArgumentException("Key '" + part + "' is both a value and a table");
                }
                current = (Map<String, Object>) next;
            }
            return current;
        }

        static String write(List<ConfigValue<?>> values, Map<String, List<String>> sectionComments) {
            StringBuilder out = new StringBuilder();
            String currentTable = null;
            for (ConfigValue<?> value : values) {
                List<String> tablePath = value.path.subList(0, value.path.size() - 1);
                String table = String.join(".", tablePath);
                if (!table.equals(currentTable)) {
                    currentTable = table;
                    if (!tablePath.isEmpty()) {
                        if (!out.isEmpty()) {
                            out.append('\n');
                        }
                        String indent = "\t".repeat(tablePath.size() - 1);
                        for (String line : sectionComments.getOrDefault(table, List.of())) {
                            out.append(indent).append('#').append(line).append('\n');
                        }
                        out.append(indent).append('[');
                        for (int i = 0; i < tablePath.size(); i++) {
                            if (i > 0) {
                                out.append('.');
                            }
                            out.append(formatKey(tablePath.get(i)));
                        }
                        out.append("]\n");
                    }
                }
                String indent = "\t".repeat(tablePath.size());
                for (String line : value.comment) {
                    out.append(indent).append('#').append(line).append('\n');
                }
                for (String line : value.constraintComment()) {
                    out.append(indent).append('#').append(line).append('\n');
                }
                out.append(indent).append(formatKey(value.path.get(value.path.size() - 1)))
                        .append(" = ").append(formatValue(value.toFileValue())).append('\n');
            }
            return out.toString();
        }

        private static String formatKey(String key) {
            return key.matches("[A-Za-z0-9_-]+") ? key : quote(key);
        }

        private static String formatValue(Object value) {
            if (value instanceof String text) {
                return quote(text);
            }
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
                return value.toString();
            }
            if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (Double.isNaN(d)) {
                    return "nan";
                }
                if (Double.isInfinite(d)) {
                    return d > 0 ? "inf" : "-inf";
                }
                return Double.toString(d);
            }
            if (value instanceof Enum<?> constant) {
                return quote(constant.name());
            }
            if (value instanceof List<?> list) {
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(formatValue(list.get(i)));
                }
                return out.append(']').toString();
            }
            return quote(String.valueOf(value));
        }

        private static String quote(String text) {
            StringBuilder out = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                switch (ch) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                        } else {
                            out.append(ch);
                        }
                    }
                }
            }
            return out.append('"').toString();
        }

        private static final class Parser {
            private final String text;
            private int pos;

            Parser(String text) {
                this.text = text.startsWith("﻿") ? text.substring(1) : text;
            }

            boolean atEnd() {
                return pos >= text.length();
            }

            char peek() {
                return text.charAt(pos);
            }

            void expect(char ch) {
                if (atEnd() || text.charAt(pos) != ch) {
                    throw error("Expected '" + ch + "'");
                }
                pos++;
            }

            void skipInlineWhitespace() {
                while (!atEnd() && (peek() == ' ' || peek() == '\t')) {
                    pos++;
                }
            }

            void skipComment() {
                if (!atEnd() && peek() == '#') {
                    while (!atEnd() && peek() != '\n') {
                        pos++;
                    }
                }
            }

            void skipWhitespaceCommentsAndNewlines() {
                while (!atEnd()) {
                    char ch = peek();
                    if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                        pos++;
                    } else if (ch == '#') {
                        skipComment();
                    } else {
                        return;
                    }
                }
            }

            void expectLineEnd() {
                skipInlineWhitespace();
                skipComment();
                if (atEnd()) {
                    return;
                }
                if (peek() == '\r') {
                    pos++;
                }
                if (atEnd() || peek() != '\n') {
                    throw error("Expected end of line");
                }
                pos++;
            }

            List<String> parseKey() {
                List<String> parts = new ArrayList<>();
                while (true) {
                    skipInlineWhitespace();
                    if (atEnd()) {
                        throw error("Expected key");
                    }
                    char ch = peek();
                    if (ch == '"') {
                        parts.add(parseBasicString());
                    } else if (ch == '\'') {
                        parts.add(parseLiteralString());
                    } else {
                        int start = pos;
                        while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '-')) {
                            pos++;
                        }
                        if (start == pos) {
                            throw error("Invalid key");
                        }
                        parts.add(text.substring(start, pos));
                    }
                    skipInlineWhitespace();
                    if (!atEnd() && peek() == '.') {
                        pos++;
                        continue;
                    }
                    return parts;
                }
            }

            Object parseValue() {
                if (atEnd()) {
                    throw error("Expected value");
                }
                char ch = peek();
                if (ch == '"') {
                    return parseBasicString();
                }
                if (ch == '\'') {
                    return parseLiteralString();
                }
                if (ch == '[') {
                    return parseArray();
                }
                if (text.startsWith("true", pos)) {
                    pos += 4;
                    return Boolean.TRUE;
                }
                if (text.startsWith("false", pos)) {
                    pos += 5;
                    return Boolean.FALSE;
                }
                return parseNumber();
            }

            private List<Object> parseArray() {
                expect('[');
                List<Object> list = new ArrayList<>();
                while (true) {
                    skipWhitespaceCommentsAndNewlines();
                    if (atEnd()) {
                        throw error("Unterminated array");
                    }
                    if (peek() == ']') {
                        pos++;
                        return list;
                    }
                    list.add(parseValue());
                    skipWhitespaceCommentsAndNewlines();
                    if (!atEnd() && peek() == ',') {
                        pos++;
                    } else if (!atEnd() && peek() != ']') {
                        throw error("Expected ',' or ']' in array");
                    }
                }
            }

            private Object parseNumber() {
                int start = pos;
                while (!atEnd() && "+-0123456789._eEinfa".indexOf(peek()) >= 0) {
                    pos++;
                }
                String token = text.substring(start, pos).replace("_", "");
                if (token.isEmpty()) {
                    throw error("Invalid value");
                }
                switch (token) {
                    case "inf", "+inf" -> {
                        return Double.POSITIVE_INFINITY;
                    }
                    case "-inf" -> {
                        return Double.NEGATIVE_INFINITY;
                    }
                    case "nan", "+nan", "-nan" -> {
                        return Double.NaN;
                    }
                    default -> {
                    }
                }
                try {
                    if (token.contains(".") || token.contains("e") || token.contains("E")) {
                        return Double.parseDouble(token);
                    }
                    long l = Long.parseLong(token);
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return (int) l;
                    }
                    return l;
                } catch (NumberFormatException e) {
                    throw error("Invalid number '" + token + "'");
                }
            }

            private String parseBasicString() {
                expect('"');
                StringBuilder out = new StringBuilder();
                while (true) {
                    if (atEnd()) {
                        throw error("Unterminated string");
                    }
                    char ch = text.charAt(pos++);
                    if (ch == '"') {
                        return out.toString();
                    }
                    if (ch == '\\') {
                        if (atEnd()) {
                            throw error("Unterminated escape");
                        }
                        char esc = text.charAt(pos++);
                        switch (esc) {
                            case 'n' -> out.append('\n');
                            case 't' -> out.append('\t');
                            case 'r' -> out.append('\r');
                            case 'b' -> out.append('\b');
                            case 'f' -> out.append('\f');
                            case '"' -> out.append('"');
                            case '\\' -> out.append('\\');
                            case 'u', 'U' -> {
                                int length = esc == 'u' ? 4 : 8;
                                if (pos + length > text.length()) {
                                    throw error("Invalid unicode escape");
                                }
                                out.appendCodePoint(Integer.parseInt(text.substring(pos, pos + length), 16));
                                pos += length;
                            }
                            default -> throw error("Invalid escape \\" + esc);
                        }
                    } else if (ch == '\n') {
                        throw error("Newline in string");
                    } else {
                        out.append(ch);
                    }
                }
            }

            private String parseLiteralString() {
                expect('\'');
                int end = text.indexOf('\'', pos);
                if (end < 0) {
                    throw error("Unterminated literal string");
                }
                String value = text.substring(pos, end);
                pos = end + 1;
                return value;
            }

            private IllegalArgumentException error(String message) {
                int line = 1;
                for (int i = 0; i < Math.min(pos, text.length()); i++) {
                    if (text.charAt(i) == '\n') {
                        line++;
                    }
                }
                return new IllegalArgumentException(message + " at line " + line);
            }
        }
    }
}
