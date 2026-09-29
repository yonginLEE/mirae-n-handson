package com.example.item;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 레거시 문항 검색(PHP 7.4)의 문자열 · 숫자 처리를 그대로 흉내 내는 도우미.
 * 이관 중에는 레거시 동작을 바꾸지 않으므로, Java 표준 함수로 바꾸면 결과가 달라지는 곳만 모았다.
 */
final class LegacyPhp {

    /** PHP {@code trim()} 기본 대상 — 공백, \t, \n, \r, \0, \x0B. 전각 공백 등은 지우지 않는다. */
    private static final String TRIM_CHARS = " \t\n\r\u0000\u000B";

    /** PHP 숫자 문자열 앞에 허용되는 공백 — " \t\n\r\v\f". */
    private static final String NUMERIC_LEADING_WHITESPACE = " \t\n\r\u000B\f";

    /** PHP 가 앞부분을 숫자로 읽는 형식(정수 · 소수 · 지수). */
    private static final Pattern NUMERIC_PREFIX =
        Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private LegacyPhp() {
    }

    /** PHP {@code trim()}. */
    static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && TRIM_CHARS.indexOf(s.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRIM_CHARS.indexOf(s.charAt(end - 1)) >= 0) {
            end--;
        }
        return s.substring(start, end);
    }

    /** PHP {@code mb_strlen($s, 'UTF-8')} — 코드포인트 수. */
    static int mbLength(String s) {
        return s.codePointCount(0, s.length());
    }

    /** PHP {@code mb_substr($s, 0, $n, 'UTF-8')} — 앞에서 코드포인트 n 개. */
    static String mbHead(String s, int n) {
        if (mbLength(s) <= n) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, n));
    }

    /** PHP {@code strtolower()} — ASCII 만 바꾼다. */
    static String asciiLower(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return sb.toString();
    }

    /** PHP {@code strtoupper()} — ASCII 만 바꾼다. */
    static String asciiUpper(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c);
        }
        return sb.toString();
    }

    /**
     * PHP {@code preg_match('/^...$/', $s)} — 수식어 없는 {@code $} 는 문자열 끝의 {@code \n} 하나 앞에서도 맞는다.
     * UNIX_LINES 로 줄 끝을 {@code \n} 만으로 한정하고 find() 로 검사한다.
     */
    static boolean pregMatch(Pattern anchored, String s) {
        return anchored.matcher(s).find();
    }

    /** {@link #pregMatch} 에 넘길 패턴을 만든다. */
    static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.UNIX_LINES);
    }

    /**
     * PHP 7.4 {@code (int)$string}.
     * 앞 공백을 건너뛰고 앞부분의 숫자만 읽는다("5abc" → 5, "3.9" → 3, "1e1" → 10, "abc" → 0).
     * 정수 범위를 넘으면 PHP_INT_MAX / PHP_INT_MIN 으로 포화하고, 무한대는 0 이다.
     */
    static long intval(String s) {
        int i = 0;
        while (i < s.length() && NUMERIC_LEADING_WHITESPACE.indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        Matcher m = NUMERIC_PREFIX.matcher(s.substring(i));
        if (!m.find()) {
            return 0L;
        }
        String number = m.group();
        boolean isInteger = m.group(2) == null && m.group(3) == null && !number.contains(".");
        if (isInteger) {
            return saturate(new BigDecimal(new BigInteger(number)));
        }
        double d = Double.parseDouble(number);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 0L;
        }
        return saturate(BigDecimal.valueOf(d));
    }

    private static long saturate(BigDecimal value) {
        BigInteger truncated = value.toBigInteger();
        if (truncated.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        if (truncated.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            return Long.MIN_VALUE;
        }
        return truncated.longValue();
    }
}
