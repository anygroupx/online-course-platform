package com.course.platform.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 对外用户标识工具。
 *
 * <p>数据库自增 ID 仅用于内部关联；所有用户可见的身份标识均使用随机 UUID v4。</p>
 */
public final class PublicUidUtil {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    private PublicUidUtil() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String uid) {
        return uid != null && UUID_V4_PATTERN.matcher(uid.trim()).matches();
    }

    public static String normalize(String uid) {
        return uid == null ? null : uid.trim().toLowerCase();
    }
}
