package aigc.gameflow.image;

/** 图片生成供应商的统一扩展接口。 */
public interface ImageGenerationProvider {

    ProviderType providerType();

    /** 当前配置和请求是否允许使用该 Provider。 */
    boolean supports(ImageGenerationRequest request);

    ImageGenerationResult generate(ImageGenerationRequest request);
}
