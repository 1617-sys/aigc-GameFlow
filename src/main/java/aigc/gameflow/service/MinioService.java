package aigc.gameflow.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * @param inputStream 图片的输入流
     * @param originalFilename 原始文件名 (例如 ComfyUI_001.png)
     * @return 图片的 HTTP 访问链接
    * */
    public String uploadImage(InputStream inputStream, String originalFilename){
        try{
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String filename = UUID.randomUUID().toString() + suffix;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, -1, 10485760) // 10MB 分片
                            .contentType("image/png") // 显式指定类型，否则浏览器打开会变成下载
                            .build()
            );



            String url = endpoint + "/" + bucketName + "/" + filename;
            log.info("图片上传 MinIO 成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("MinIO 上传失败", e);
            throw new RuntimeException("图片上传服务异常");
        }
    }
}