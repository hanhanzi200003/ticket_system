package com.example.ticket_system.config.utils;

import io.minio.MinioClient;
import io.minio.errors.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 工具类
 * 提供文件上传、下载、删除等常用操作
 */
@Component
public class MinioUtil {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * 确保存储桶存在，如果不存在则创建
     */
    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!found) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(bucketName).build()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("检查或创建存储桶失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传文件
     *
     * @param file 要上传的文件
     * @param objectName 文件在 MinIO 中的名称（建议使用 UUID 或时间戳避免重名）
     * @return 文件的访问 URL
     */
    public String uploadFile(MultipartFile file, String objectName) {
        try {
            ensureBucketExists();

            // 上传文件到 MinIO
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 返回文件访问路径
            return getFileUrl(objectName);
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 上传字节数组（用于压缩后的图片）
     *
     * @param bytes 字节数组
     * @param objectName 文件在 MinIO 中的名称
     * @param contentType 文件类型（如 image/jpeg）
     * @return 文件的访问 URL
     */
    public String uploadBytes(byte[] bytes, String objectName, String contentType) {
        try {
            ensureBucketExists();
            
            // 将字节数组转换为输入流
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(bytes);
            
            // 上传到 MinIO
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, bytes.length, -1)
                            .contentType(contentType)
                            .build()
            );
            
            // 返回文件访问路径
            return getFileUrl(objectName);
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件的访问 URL
     *
     * @param objectName 文件名称
     * @return 文件的完整访问 URL
     */
    public String getFileUrl(String objectName) {
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }

    /**
     * 获取文件的预签名 URL（用于临时访问）
     *
     * @param objectName 文件名称
     * @param expiry 过期时间（单位：秒）
     * @return 预签名 URL
     */
    public String getPresignedUrl(String objectName, int expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiry, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取预签名 URL 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 要删除的文件名称
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件
     *
     * @param objectName 文件名称
     * @return 文件输入流
     */
    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }


}
