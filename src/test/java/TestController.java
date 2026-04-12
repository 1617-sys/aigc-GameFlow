import aigc.gameflow.service.ComfyUiService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private ComfyUiService comfyUiService;

    @PostMapping("/comfyui")
    public String testComfyUi(@RequestBody String promptJson) {
        JSONObject json = JSON.parseObject(promptJson);
        String prompt = json.getString("prompt");
        long seed = json.getLongValue("seed");
        return comfyUiService.callPythonFacade(prompt, seed);
    }
}

