package aigc.gameflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/** 封装 ComfyUI 的任务提交、状态查询和图片访问接口。 */
@Service
@Slf4j
public class ComfyUiService {

    private final RestClient restClient;
    private final String comfyUiUrl;

    public ComfyUiService(
            @Qualifier("comfyUiRestClient") RestClient restClient,
            @Value("${comfyui.base-url}") String comfyUiUrl
    ) {
        this.restClient = restClient;
        this.comfyUiUrl = comfyUiUrl;
    }

    // 可选的 Python 门面入口；当前主链路由 GameAssetService 直接调用 postTask。
    public String callPythonFacade(String englishPrompt, long seed) {
        String pythonUrl = comfyUiUrl + "/api/v1/generate";

        Map<String, Object> body = new HashMap<>();
        body.put("prompt", englishPrompt);
        body.put("seed", seed);

        String responseBody = restClient.post()
                .uri(pythonUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JSONObject res = JSON.parseObject(responseBody);
        return res.getString("prompt_id");
    }

    public String postTask(String promptJson) {
        String responseBody = restClient.post()
                .uri(comfyUiUrl + "/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(promptJson)
                .retrieve()
                .body(String.class);
        JSONObject result = JSON.parseObject(responseBody);
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
            // ComfyUI 未完成时 history 中可能还没有当前任务或 outputs，此时返回 null 继续轮询。
            String responseBody = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            JSONObject json = JSON.parseObject(responseBody);
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
