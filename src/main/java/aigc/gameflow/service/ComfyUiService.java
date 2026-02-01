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

    public String postTask(String promptJson) {
        String url = comfyUiUrl + "/prompt";

        // 注意：这里的 HttpHeaders 必须是 org.springframework.http 包下的
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        // 使用 fastjson 解析字符串
        requestBody.put("prompt", JSON.parseObject(promptJson));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("ComfyUI 响应: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("调用 ComfyUI 失败: {}", e.getMessage());
            return null;
        }
    }
    // 在 ComfyUiService 里添加




    public String getImageFilename(String promptId) {
        String url = comfyUiUrl + "/history/" + promptId;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(response.getBody());  // 使用 Fastjson2
            JSONObject taskData = json.getJSONObject(promptId);  // 直接使用 Fastjson2 的方法

            if(taskData == null){
                return null;
            }

            JSONObject outputs = taskData.getJSONObject("outputs");  // 修正：从 taskData 获取
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
            e.printStackTrace();
        }
        return null;
    }
}

