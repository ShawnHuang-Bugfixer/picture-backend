package com.xin.picturebackend.utils;

import java.util.regex.Pattern;

/**
 * 邮箱工具类，用于校验邮箱格式是否合法
 *
 * @author 黄兴鑫
 * @since 2025/7/13 8:12
 */
public class EmailUtil {

    // 邮箱正则：支持常见邮箱（RFC 5322 简化版）
    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    /**
     * 校验邮箱格式
     *
     * @param email 待校验的邮箱
     * @return true：合法；false：非法
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.length() > 254) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 示例
     */
    public static void main(String[] args) {
        System.out.println(isValidEmail("user@example.com")); // true
        System.out.println(isValidEmail("abc@")); // false
    }
}
