package tw.bk.appai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Groq API 設定屬性類別。
 * 從 application.properties 讀取 app.ai.groq.* 設定項。
 */
@Component
@ConfigurationProperties(prefix = "app.ai.groq")
public class GroqProperties {
    /** Groq API 基礎 URL */
    private String baseUrl = "https://api.groq.com/openai/v1";

    /** Groq API 金鑰（從環境變數 APP_AI_GROQ_API_KEY 取得） */
    private String apiKey;

    /** 使用的模型名稱 */
    private String model = "openai/gpt-oss-120b";

    /** 生成溫度（0.0-2.0），越低越確定性 */
    private Double temperature = 0.2;

    /** 最大回覆 token 數 */
    private Integer maxCompletionTokens = 512;

    /** Top-p 採樣參數 */
    private Double topP = 1.0;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }
}
