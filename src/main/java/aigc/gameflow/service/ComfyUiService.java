package aigc.gameflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ComfyUiService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${comfyui.base-url}")
    private String comfyUiUrl;

    // Java 现在只需要调 Python,不需要解析任何 ComfyUI JSON
    public String callPythonFacade(String englishPrompt, long seed) {
        String pythonUrl = comfyUiUrl + "/api/v1/generate";

        Map<String, Object> body = new HashMap<>();
        body.put("prompt", englishPrompt);
        body.put("seed", seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(pythonUrl, entity, String.class);
        JSONObject res = JSON.parseObject(response.getBody());
        return res.getString("prompt_id");
    }

    public String postTask(String promptJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(promptJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(comfyUiUrl + "/prompt", entity, String.class);
        JSONObject result = JSON.parseObject(response.getBody());
        String promptId = result.getString("prompt_id");

        if (promptId == null || promptId.isBlank()) {
            throw new RuntimeException("ComfyUI 返回的 prompt_id 为空");
        }

        return promptId;
    }

    public String buildImageViewUrl(String filename) {
        return comfyUiUrl + "/view?filename=" + filename;
    }

    public String getImageFilename(String promptId) {
        String url = comfyUiUrl + "/history/" + promptId;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(response.getBody());
            JSONObject taskData = json.getJSONObject(promptId);

            if(taskData == null){
                return null;
            }

            JSONObject outputs = taskData.getJSONObject("outputs");
            if(outputs == null){
                return null;
            }

            for(String key : outputs.keySet()){
                JSONObject nodeOutput = outputs.getJSONObject(key);
                if(nodeOutput.containsKey("images")){
                    return nodeOutput.getJSONArray("images")
                            .getJSONObject(0)
                            .getString("filename");
                }
            }
        } catch (Exception e){
            log.error("获取图像文件名失败: {}", e.getMessage());
        }
        return null;
    }
}
