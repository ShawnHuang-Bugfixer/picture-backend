package com.xin.picturebackend.utils;

/**
 * 工具类：计算图片主色调相似度
 *
 * @author 黄兴鑫
 * @since 2025/3/25 11:11
 */
public class ColorSimilarUtils {
    private ColorSimilarUtils() {
    }

    /**
     * 计算两种颜色的 RGB 值之间的欧几里得距离，并返回相似度。
     *
     * @param RGB1 第一种颜色的 RGB 值，格式为 0x000000
     * @param RGB2 第二种颜色的 RGB 值，格式为 0x000000
     * @return 相似度（距离越小，相似度越高）
     */
    public static double getImageSimilarity(String RGB1, String RGB2) {
        // 将 RGB 字符串转换为整数
        int rgb1 = Integer.decode(RGB1);
        int rgb2 = Integer.decode(RGB2);

        // 提取 R、G、B 分量
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;

        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;

        // 计算欧几里得距离
        double distance = Math.sqrt(
                Math.pow(r1 - r2, 2) +
                        Math.pow(g1 - g2, 2) +
                        Math.pow(b1 - b2, 2)
        );

        // 将距离转换为相似度（距离越小，相似度越高）
        // 最大可能的欧几里得距离是 sqrt(3 * 255^2) ≈ 441.67
        double maxDistance = Math.sqrt(3 * Math.pow(255, 2));

        return 1 - (distance / maxDistance);
    }

    public static void main(String[] args) {
        // 测试
        String color1 = "0xFF0000"; // 红色
        String color2 = "0x00FF00"; // 绿色
        double similarity = getImageSimilarity(color1, color2);
        System.out.println("颜色相似度: " + similarity);
    }
}
