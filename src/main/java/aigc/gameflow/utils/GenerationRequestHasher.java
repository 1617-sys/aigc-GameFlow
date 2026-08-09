package aigc.gameflow.utils;

import aigc.gameflow.dto.GenerationSubmitRequest;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;

public final class GenerationRequestHasher {

    private GenerationRequestHasher() {
    }

    public static String hash(GenerationSubmitRequest request) {
        return DigestUtil.sha256Hex(JSON.toJSONString(request));
    }
}
