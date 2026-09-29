package com.tacz.guns.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks a gun pack's {@code dependencies} version requirement against a mod version.
 * <p>
 * Two syntaxes are accepted, because packs exist for both ecosystems:
 * <ul>
 *     <li><b>Maven ranges</b>, as written for TACZ on Forge: {@code [1.0,2.0)}, {@code [1.1.8,)},
 *     {@code (,1.0],[1.2,)}, {@code [1.0]}. A bare version such as {@code 1.0.4} is Maven's
 *     "recommended version" and accepts any version, exactly as TACZ on Forge did.</li>
 *     <li><b>Fabric/semver predicates</b>, as written for TACZ ports on Fabric: {@code *},
 *     {@code >=1.1.8}, {@code <2}, {@code =1.1.8}, {@code ~1.1}, {@code ^1.1}, {@code 1.1.x};
 *     space-separated predicates must all match.</li>
 * </ul>
 * Build metadata ({@code +...}) is ignored when comparing, so {@code 1.1.8+mc26.2} satisfies {@code >=1.1.8}.
 */
public final class VersionRequirement {
    private VersionRequirement() {
    }

    /**
     * @throws IllegalArgumentException if the requirement cannot be parsed
     */
    public static boolean matches(String requirement, String version) {
        String spec = requirement == null ? "" : requirement.trim();
        if (spec.isEmpty() || spec.equals("*")) {
            return true;
        }
        if (spec.startsWith("[") || spec.startsWith("(")) {
            return matchesMavenRanges(spec, version);
        }
        char first = spec.charAt(0);
        if (first == '>' || first == '<' || first == '=' || first == '~' || first == '^'
                || spec.contains(" ") || spec.toLowerCase(Locale.ROOT).endsWith(".x")) {
            return matchesPredicates(spec, version);
        }
        // Maven recommended version: no restriction.
        parse(spec);
        return true;
    }

    private static boolean matchesMavenRanges(String spec, String version) {
        List<String> ranges = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < spec.length(); i++) {
            char ch = spec.charAt(i);
            if (ch == '[' || ch == '(') {
                if (depth++ == 0) {
                    start = i;
                }
            } else if (ch == ']' || ch == ')') {
                if (--depth == 0) {
                    ranges.add(spec.substring(start, i + 1));
                } else if (depth < 0) {
                    throw new IllegalArgumentException("Unbalanced version range: " + spec);
                }
            } else if (depth == 0 && ch != ',' && !Character.isWhitespace(ch)) {
                throw new IllegalArgumentException("Unexpected character outside a version range: " + spec);
            }
        }
        if (depth != 0 || ranges.isEmpty()) {
            throw new IllegalArgumentException("Unbalanced version range: " + spec);
        }
        Version actual = parse(version);
        for (String range : ranges) {
            if (inMavenRange(range, actual)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inMavenRange(String range, Version actual) {
        boolean lowerInclusive = range.charAt(0) == '[';
        boolean upperInclusive = range.charAt(range.length() - 1) == ']';
        String body = range.substring(1, range.length() - 1).trim();
        int comma = body.indexOf(',');
        if (comma < 0) {
            if (!lowerInclusive || !upperInclusive) {
                throw new IllegalArgumentException("A single version must use [] brackets: " + range);
            }
            return actual.compareTo(parse(body)) == 0;
        }
        String lower = body.substring(0, comma).trim();
        String upper = body.substring(comma + 1).trim();
        if (!lower.isEmpty()) {
            int cmp = actual.compareTo(parse(lower));
            if (cmp < 0 || (cmp == 0 && !lowerInclusive)) {
                return false;
            }
        }
        if (!upper.isEmpty()) {
            int cmp = actual.compareTo(parse(upper));
            return cmp < 0 || (cmp == 0 && upperInclusive);
        }
        return true;
    }

    private static boolean matchesPredicates(String spec, String version) {
        Version actual = parse(version);
        for (String predicate : spec.split("\\s+")) {
            if (!matchesPredicate(predicate, actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPredicate(String predicate, Version actual) {
        if (predicate.equals("*")) {
            return true;
        }
        String operator;
        if (predicate.startsWith(">=") || predicate.startsWith("<=")) {
            operator = predicate.substring(0, 2);
        } else if (predicate.startsWith(">") || predicate.startsWith("<") || predicate.startsWith("=")
                || predicate.startsWith("~") || predicate.startsWith("^")) {
            operator = predicate.substring(0, 1);
        } else {
            operator = "";
        }
        String operand = predicate.substring(operator.length());
        String lower = operand.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".x")) {
            String prefix = operand.substring(0, operand.length() - 2);
            Version base = parse(prefix);
            return actual.startsWith(base);
        }
        Version target = parse(operand);
        int cmp = actual.compareTo(target);
        return switch (operator) {
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case "~" -> cmp >= 0 && actual.segment(0) == target.segment(0) && actual.segment(1) == target.segment(1);
            case "^" -> cmp >= 0 && actual.segment(0) == target.segment(0);
            default -> cmp == 0;
        };
    }

    private static Version parse(String text) {
        String value = text.trim();
        int plus = value.indexOf('+');
        if (plus >= 0) {
            value = value.substring(0, plus);
        }
        String preRelease = null;
        int dash = value.indexOf('-');
        if (dash >= 0) {
            preRelease = value.substring(dash + 1);
            value = value.substring(0, dash);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty version: " + text);
        }
        String[] parts = value.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid version number '" + parts[i] + "' in " + text, e);
            }
        }
        return new Version(numbers, preRelease);
    }

    private record Version(int[] numbers, String preRelease) implements Comparable<Version> {
        int segment(int index) {
            return index < numbers.length ? numbers[index] : 0;
        }

        boolean startsWith(Version prefix) {
            if (prefix.numbers.length > numbers.length) {
                return false;
            }
            for (int i = 0; i < prefix.numbers.length; i++) {
                if (numbers[i] != prefix.numbers[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int compareTo(Version other) {
            int length = Math.max(numbers.length, other.numbers.length);
            for (int i = 0; i < length; i++) {
                int cmp = Integer.compare(segment(i), other.segment(i));
                if (cmp != 0) {
                    return cmp;
                }
            }
            if (preRelease == null || other.preRelease == null) {
                // A release sorts after any pre-release of the same numbers.
                return preRelease == null ? (other.preRelease == null ? 0 : 1) : -1;
            }
            return preRelease.compareTo(other.preRelease);
        }
    }
}
