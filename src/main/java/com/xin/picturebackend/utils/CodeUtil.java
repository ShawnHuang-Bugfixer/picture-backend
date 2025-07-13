package com.xin.picturebackend.utils;

import java.security.SecureRandom;

/**
 *
 * @author 黄兴鑫
 * @since 2025/7/13 8:10
 */
public class CodeUtil {

    // 可选字符集（默认：0-9）
    private static final String DIGITS = "0123456789";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String ALL = DIGITS + LETTERS;

    // 安全随机数生成器（线程安全）
    private static final SecureRandom random = new SecureRandom();

    /**
     * 生成默认6位数字验证码
     */
    public static String generateCode(int length) {
        return generateCode(length, false);
    }

    /**
     * 生成验证码
     * @param length 验证码长度
     * @param useLetters 是否包含字母
     * @return 随机验证码字符串
     */
    public static String generateCode(int length, boolean useLetters) {
        StringBuilder sb = new StringBuilder(length);
        String chars = useLetters ? ALL : DIGITS;

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
