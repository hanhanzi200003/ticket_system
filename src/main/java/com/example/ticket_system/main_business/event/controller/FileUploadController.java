package com.example.ticket_system.main_business.event.controller;

import com.example.ticket_system.config.utils.ImageCompressor;
import com.example.ticket_system.config.utils.MinioUtil;
import com.example.ticket_system.config.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器（商家端）
 */
@RestController
@RequestMapping("/merchant/upload")
public class FileUploadController {
    
    @Autowired
    private MinioUtil minioUtil;
    
    // 最大文件大小：5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    /**
     * 上传演唱会封面图（单张）
     * 
     * @param file 图片文件
     * @return 图片URL
     */
    @PostMapping("/cover")
    public Result<Map<String, String>> uploadCoverImage(
            @RequestParam("file") MultipartFile file) {
        
        // 验证只能上传1张
        return uploadSingleImage(file, "cover", 1);
    }
    
    /**
     * 上传演唱会详情图（批量，最多4张）
     * 
     * @param files 图片文件列表
     * @return 图片URL列表
     */
    @PostMapping("/detail")
    public Result<java.util.List<Map<String, String>>> uploadDetailImages(
            @RequestParam("files") MultipartFile[] files) {
        
        return uploadMultipleImages(files, "detail", 4);
    }
    
    /**
     * 上传演唱会详情图（单张，方便逐个上传）
     * 
     * @param file 图片文件
     * @return 图片URL
     */
    @PostMapping("/detail/single")
    public Result<Map<String, String>> uploadSingleDetailImage(
            @RequestParam("file") MultipartFile file) {
        
        return uploadSingleImage(file, "detail", 1);
    }
    
    /**
     * 上传单张图片
     * 
     * @param file 图片文件
     * @param type 图片类型：cover/detail
     * @param maxCount 最大数量
     * @return 图片URL
     */
    private Result<Map<String, String>> uploadSingleImage(MultipartFile file, String type, int maxCount) {
        // 验证文件数量
        if (file == null || file.isEmpty()) {
            return new Result<>(400, "文件不能为空", null);
        }
        
        // 验证并上传
        Map<String, String> result = validateAndUpload(file, type);
        if (result == null) {
            return new Result<>(400, "图片格式不支持，只允许 JPG/JPEG/PNG/WebP 格式", null);
        }
        
        return new Result<>(200, "上传成功", result);
    }
    
    /**
     * 批量上传图片
     * 
     * @param files 图片文件数组
     * @param type 图片类型：cover/detail
     * @param maxCount 最大数量
     * @return 图片URL列表
     */
    private Result<java.util.List<Map<String, String>>> uploadMultipleImages(
            MultipartFile[] files, String type, int maxCount) {
        
        // 1. 验证文件数组
        if (files == null || files.length == 0) {
            return new Result<>(400, "文件不能为空", null);
        }
        
        // 2. 验证数量限制
        if (files.length > maxCount) {
            return new Result<>(400, "最多只能上传" + maxCount + "张图片", null);
        }
        
        // 3. 逐张验证并上传
        java.util.List<Map<String, String>> resultList = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, String> result = validateAndUpload(file, type);
            if (result == null) {
                return new Result<>(400, "图片格式不支持，只允许 JPG/JPEG/PNG/WebP 格式", null);
            }
            resultList.add(result);
        }
        
        return new Result<>(200, "成功上传" + resultList.size() + "张图片", resultList);
    }
    
    /**
     * 验证并上传图片（带压缩）
     * 
     * @param file 图片文件
     * @param type 图片类型
     * @return 上传结果，失败返回null
     */
    private Map<String, String> validateAndUpload(MultipartFile file, String type) {
        try {
            // 1. 验证文件是否为空
            if (file == null || file.isEmpty()) {
                return null;
            }
            
            // 2. 验证文件大小（不超过5MB）
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("图片大小不能超过5MB");
            }
            
            // 3. 验证原始文件扩展名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isValidImageExtension(originalFilename)) {
                return null; // 扩展名不合法
            }
            
            // 4. 验证 MIME Type（防止伪造扩展名）
            String contentType = file.getContentType();
            if (!isValidMimeType(contentType)) {
                return null; // MIME Type不合法
            }
            
            // 5. 验证扩展名和MIME Type是否匹配
            if (!isExtensionMatchMimeType(originalFilename, contentType)) {
                return null; // 扩展名和MIME Type不匹配
            }
            
            // 6. 压缩图片
            long originalSize = file.getSize();
            byte[] compressedBytes = ImageCompressor.compressImage(file);
            
            // 7. 获取压缩后的文件扩展名（统一转为jpg）
            String extension = ImageCompressor.getFileExtension(originalFilename);
            
            // 8. 生成统一命名的文件名
            String fileName = String.format("concert/%s/%s_%s%s", 
                type, 
                java.time.LocalDate.now().toString().replace("-", ""),
                java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                extension
            );
            
            // 9. 上传到 MinIO（使用压缩后的字节数组）
            String url = minioUtil.uploadBytes(compressedBytes, fileName, "image/jpeg");
            
            // 10. 返回结果
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("fileName", fileName);
            result.put("size", String.valueOf(compressedBytes.length));
            result.put("originalName", originalFilename);
            result.put("compressionRatio", String.format("%.2f%%", 
                (1 - (double) compressedBytes.length / originalSize) * 100));
            
            return result;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("上传失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证文件扩展名是否合法
     */
    private boolean isValidImageExtension(String filename) {
        String ext = getFileExtension(filename).toLowerCase();
        return ext.equals(".jpg") || ext.equals(".jpeg") || 
               ext.equals(".png") || ext.equals(".webp");
    }
    
    /**
     * 验证 MIME Type 是否合法
     */
    private boolean isValidMimeType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.equals("image/jpeg") || 
               contentType.equals("image/png") || 
               contentType.equals("image/webp");
    }
    
    /**
     * 验证扩展名和 MIME Type 是否匹配
     */
    private boolean isExtensionMatchMimeType(String filename, String contentType) {
        String ext = getFileExtension(filename).toLowerCase();
        
        if (ext.equals(".jpg") || ext.equals(".jpeg")) {
            return contentType.equals("image/jpeg");
        } else if (ext.equals(".png")) {
            return contentType.equals("image/png");
        } else if (ext.equals(".webp")) {
            return contentType.equals("image/webp");
        }
        
        return false;
    }
    
    /**
     * 获取文件扩展名（包含点号）
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}
