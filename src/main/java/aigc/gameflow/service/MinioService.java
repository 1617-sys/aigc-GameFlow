package aigc.gameflow.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

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
            throw new RuntimeException("Image upload service failed");
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
