package com.example.ticket_system.config.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片压缩工具类
 * 
 * 功能：
 * 1. 压缩图片到指定大小（默认500KB以下）
 * 2. 调整分辨率到指定大小（默认约1MP，即1280x720或1024x1024）
 * 3. 保持图片质量在可接受范围内
 */
@Slf4j
public class ImageCompressor {
    
    // 目标文件大小：500KB
    private static final long TARGET_FILE_SIZE = 500 * 1024;
    
    // 最小压缩质量
    private static final float MIN_QUALITY = 0.3f;
    
    // 初始压缩质量
    private static final float INITIAL_QUALITY = 0.85f;
    
    /**
     * 压缩图片
     * 
     * @param file 原始图片文件
     * @return 压缩后的字节数组
     */
    public static byte[] compressImage(MultipartFile file) throws IOException {
        // 1. 读取原始图片
        byte[] originalBytes = file.getBytes();
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
        
        if (originalImage == null) {
            throw new IOException("无法读取图片文件");
        }
        
        log.info("原始图片信息: 尺寸={}x{}, 大小={}KB", 
            originalImage.getWidth(), 
            originalImage.getHeight(), 
            originalBytes.length / 1024);
        
        // 2. 计算目标尺寸（保持宽高比，总像素约1MP）
        int[] targetDimensions = calculateTargetDimensions(originalImage.getWidth(), originalImage.getHeight());
        int targetWidth = targetDimensions[0];
        int targetHeight = targetDimensions[1];
        
        log.info("目标图片尺寸: {}x{}", targetWidth, targetHeight);
        
        // 3. 先调整尺寸
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(originalImage)
            .size(targetWidth, targetHeight)
            .outputQuality(INITIAL_QUALITY)
            .toOutputStream(baos);
        
        byte[] compressedBytes = baos.toByteArray();
        
        log.info("尺寸调整后大小: {}KB", compressedBytes.length / 1024);
        
        // 4. 如果仍然超过目标大小，降低质量
        if (compressedBytes.length > TARGET_FILE_SIZE) {
            compressedBytes = adjustQuality(originalImage, targetWidth, targetHeight, compressedBytes);
        }
        
        log.info("最终压缩后大小: {}KB", compressedBytes.length / 1024);
        
        return compressedBytes;
    }
    
    /**
     * 计算目标尺寸（保持宽高比，总像素约1MP）
     * 
     * @param width 原始宽度
     * @param height 原始高度
     * @return [目标宽度, 目标高度]
     */
    private static int[] calculateTargetDimensions(int width, int height) {
        // 目标像素：约1MP (1,000,000 像素)
        double targetPixels = 1_000_000;
        
        // 如果原始图片已经小于目标像素，保持原尺寸
        long originalPixels = (long) width * height;
        if (originalPixels <= targetPixels) {
            return new int[]{width, height};
        }
        
        // 计算缩放比例
        double scale = Math.sqrt(targetPixels / originalPixels);
        
        int targetWidth = (int) (width * scale);
        int targetHeight = (int) (height * scale);
        
        // 确保是偶数（某些编码格式要求）
        targetWidth = targetWidth % 2 == 0 ? targetWidth : targetWidth + 1;
        targetHeight = targetHeight % 2 == 0 ? targetHeight : targetHeight + 1;
        
        return new int[]{targetWidth, targetHeight};
    }
    
    /**
     * 调整质量直到文件大小符合要求
     * 
     * @param originalImage 原始图片
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param currentBytes 当前字节数组
     * @return 压缩后的字节数组
     */
    private static byte[] adjustQuality(BufferedImage originalImage, int targetWidth, int targetHeight, byte[] currentBytes) throws IOException {
        float quality = INITIAL_QUALITY;
        byte[] result = currentBytes;
        
        // 逐步降低质量，直到文件大小符合要求或达到最小质量
        while (result.length > TARGET_FILE_SIZE && quality > MIN_QUALITY) {
            quality -= 0.05f; // 每次降低5%的质量
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.of(originalImage)
                .size(targetWidth, targetHeight)
                .outputQuality(quality)
                .toOutputStream(baos);
            
            result = baos.toByteArray();
            
            log.debug("调整质量: quality={}, size={}KB", quality, result.length / 1024);
        }
        
        if (result.length > TARGET_FILE_SIZE) {
            log.warn("无法将图片压缩到{}KB以下，当前大小={}KB", 
                TARGET_FILE_SIZE / 1024, result.length / 1024);
        }
        
        return result;
    }
    
    /**
     * 获取压缩后的文件格式
     * 
     * @param originalFilename 原始文件名
     * @return 文件扩展名（包含点号）
     */
    public static String getFileExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".jpg"; // 默认返回jpg
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        
        // 统一转换为jpg格式（压缩效果更好）
        if (ext.equals(".png") || ext.equals(".webp")) {
            return ".jpg";
        }
        
        return ext;
    }
}
