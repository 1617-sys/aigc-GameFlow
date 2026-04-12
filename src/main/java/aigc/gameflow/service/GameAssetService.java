package aigc.gameflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class GameAssetService {

    @Autowired
    private ComfyUiService comfyUiService;
    @Autowired
    private AiPromptService aiPromptService;
    public String generateByText(String prompt) {
        log.info("正在请求 AI 优化提示词...");
        String engPrompt = aiPromptService.optimize(prompt);
        log.info("AI 优化结果: {}", engPrompt);
        engPrompt = engPrompt.replace("\r","").replace("\n","");

        String json = readJson("workflows/t2i.json");

        long randomSeed = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        String finalJson = json
                .replace("${prompt}", engPrompt)
                .replace("\"${seed}\"", String.valueOf(randomSeed)) // 兼容 seed 是字符串的情况
                .replace("${seed}", String.valueOf(randomSeed));

        return comfyUiService.postTask(finalJson);
    }

    private String readJson(String path){
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if(!resource.exists()){
                throw new RuntimeException("模板文件不存在: " + path);
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            }catch(IOException e){
            throw new RuntimeException("读取模板文件失败: " + path);
        }
    }
}
