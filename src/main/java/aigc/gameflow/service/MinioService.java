package aigc.gameflow.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.UUID;

/** 图片对象存储服务，负责上传和按受控对象路径流式读取。 */
@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String uploadImage(InputStream inputStream, String originalFilename) {
        try {
            ensureBucket();
            String suffix = resolveSuffix(originalFilename);
            String filename = UUID.randomUUID() + suffix;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, -1, 10 * 1024 * 1024)
                            .contentType("image/png")
                            .build()
            );

            String url = endpoint + "/" + bucketName + "/" + filename;
            log.info("Image uploaded to MinIO: {}", url);
            return url;
        } catch (Exception e) {
            log.error("MinIO upload failed", e);
            throw new IllegalStateException("Image upload service failed: " + e.getMessage(), e);
        }
    }

    public void streamImage(String imageUrl, OutputStream outputStream) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("Task has no generated image");
        }
        try {
            // 只允许读取当前 bucket 下的对象，避免把任意 URL 当作代理地址。
            String path = URI.create(imageUrl).getPath();
            String bucketPrefix = "/" + bucketName + "/";
            if (path == null || !path.startsWith(bucketPrefix)) {
                throw new IllegalArgumentException("Invalid stored image URL");
            }
            String objectName = path.substring(bucketPrefix.length());
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(objectName).build()
            )) {
                inputStream.transferTo(outputStream);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO image read failed, imageUrl={}", imageUrl, e);
            throw new IllegalStateException("Image read service failed");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    private String resolveSuffix(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".png";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}
