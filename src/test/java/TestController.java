import aigc.gameflow.service.ComfyUiService;
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
        return comfyUiService.postTask(promptJson);
    }
}

