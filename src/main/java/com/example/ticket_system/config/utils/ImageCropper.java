package com.example.ticket_system.config.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * 图片裁剪工具类
 * 
 * 功能：
 * 1. 将图片按中心裁剪为 2:3 比例
 * 2. 生成缩略图
 */
@Slf4j
public class ImageCropper {
    
    // 目标宽高比：2:3
    private static final double TARGET_RATIO = 2.0 / 3.0;
    
    /**
     * 从 URL 下载图片并裁剪为 2:3 比例（中心裁剪）
     * 
     * @param imageUrl 图片URL
     * @return 裁剪后的字节数组
     */
    public static byte[] cropToRatio(String imageUrl) throws IOException {
        // 1. 从 URL 读取图片
        URL url = new URL(imageUrl);
        BufferedImage originalImage = ImageIO.read(url);
        
        if (originalImage == null) {
            throw new IOException("无法读取图片: " + imageUrl);
        }
        
        log.info("原始图片信息: 尺寸={}x{}, 宽高比={}", 
            originalImage.getWidth(), 
            originalImage.getHeight(),
            (double) originalImage.getWidth() / originalImage.getHeight());
        
        // 2. 计算裁剪区域（中心裁剪）
        Rectangle cropRect = calculateCropRectangle(originalImage.getWidth(), originalImage.getHeight());
        
        log.info("裁剪区域: x={}, y={}, width={}, height={}", 
            cropRect.x, cropRect.y, cropRect.width, cropRect.height);
        
        // 3. 执行裁剪
        BufferedImage croppedImage = originalImage.getSubimage(
            cropRect.x, 
            cropRect.y, 
            cropRect.width, 
            cropRect.height
        );
        
        // 4. 压缩到合适大小（保持2:3比例，约500KB以下）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(croppedImage)
            .size(cropRect.width, cropRect.height)
            .outputQuality(0.85f)
            .outputFormat("jpg")
            .toOutputStream(baos);
        
        byte[] result = baos.toByteArray();
        
        log.info("裁剪后图片: 尺寸={}x{}, 大小={}KB", 
            croppedImage.getWidth(), 
            croppedImage.getHeight(),
            result.length / 1024);
        
        return result;
    }
    
    /**
     * 计算裁剪矩形（中心裁剪，保持2:3比例）
     * 
     * @param width 原始宽度
     * @param height 原始高度
     * @return 裁剪区域
     */
    private static Rectangle calculateCropRectangle(int width, int height) {
        double currentRatio = (double) width / height;
        
        int cropWidth, cropHeight, x, y;
        
        if (currentRatio > TARGET_RATIO) {
            // 当前图片太宽，需要裁剪宽度
            cropHeight = height;
            cropWidth = (int) (height * TARGET_RATIO);
            x = (width - cropWidth) / 2; // 中心裁剪
            y = 0;
        } else {
            // 当前图片太高，需要裁剪高度
            cropWidth = width;
            cropHeight = (int) (width / TARGET_RATIO);
            x = 0;
            y = (height - cropHeight) / 2; // 中心裁剪
        }
        
        return new Rectangle(x, y, cropWidth, cropHeight);
    }
}
