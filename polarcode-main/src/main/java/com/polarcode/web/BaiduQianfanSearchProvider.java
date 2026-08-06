package com.polarcode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 百度千帆「智能搜索」provider（v2/ai_search/chat/completions）。
 *
 * <p>走 OpenAI-compatible chat completions + 非流式模式（{@code stream: false}）：
 * 请求体里塞 user 消息，模型在 response content 里生成带 ^[N]^ 引用标记的 Markdown，
 * 真实引用源放在顶层 {@code references[]} 数组里（id / title / url / content / date / web_anchor / website）。
 *
 * <p>本 provider 优先从 {@code references[]} 解析成结构化 {@link SearchResult} 列表，
 * 供 web_search 工具的折叠 / 摘要 / URL 渲染复用。
 *
 * <p>配置（.env）：
 * <pre>
 * QIANFAN_API_KEY=your_appbuilder_api_key
 * QIANFAN_AI_SEARCH_MODEL=ernie-4.5-turbo-32k
 * </pre>
 */
public class BaiduQianfanSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(BaiduQianfanSearchProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private static final String ENDPOINT =
            "https://qianfan.baidubce.com/v2/ai_search/chat/completions";
    private static final String DEFAULT_MODEL = "ernie-4.5-turbo-32k";

    private final String apiKey;
    private final String model;
    private final boolean deepSearch;
    private final OkHttpClient httpClient;

    public BaiduQianfanSearchProvider(String apiKey) {
        this(apiKey, DEFAULT_MODEL, false, new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build());
    }

    public BaiduQianfanSearchProvider(String apiKey, String model) {
        this(apiKey, model, false, new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build());
    }

    public BaiduQianfanSearchProvider(String apiKey, String model, boolean deepSearch) {
        this(apiKey, model, deepSearch, new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build());
    }

    /**
     * 包级私有构造器——用于单元测试注入 Mock OkHttpClient。
     */
    BaiduQianfanSearchProvider(String apiKey, String model, boolean deepSearch, OkHttpClient httpClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        this.deepSearch = deepSearch;
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "baidu-qianfan";
    }

    @Override
    public boolean isReady() {
        return !apiKey.isBlank();
    }

    @Override
    public String unavailableHint() {
        return "百度千帆搜索未配置。请在 .env 中设置 QIANFAN_API_KEY。\n"
                + "千帆平台：https://cloud.baidu.com/product/qianfan";
    }

    @Override
    public List<SearchResult> search(String query, int topK) throws IOException {
        if (!isReady()) {
            throw new IOException(unavailableHint());
        }
        int count = topK > 0 ? Math.min(topK, 50) : 5;

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", model);
        // 关闭 SSE：智能搜索接口走非流式 chat completions，一次性返回完整 JSON。
        payload.put("stream", false);
        // 千帆「智能搜索」扩展参数：开启角标（^[N]^ 引用标记）。
        // 深度搜索（enable_deep_search）会显著增加响应时间，默认关闭；
        // 需要更高覆盖/权威度时可通过 QIANFAN_AI_SEARCH_DEEP=true 开启。
        payload.put("instruction", "##");
        payload.put("enable_corner_markers", true);
        payload.put("enable_deep_search", deepSearch);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", query);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(payload.toString(), JSON_MEDIA))
                .build();

        log.info("Baidu Qianfan ai_search: query={}, model={}, topK={}", query, model, count);

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                if (response.code() == 401 || response.code() == 403) {
                    throw new IOException("百度千帆 API Key 无效或已过期 (HTTP " + response.code() + ")");
                }
                throw new IOException("百度千帆智能搜索请求失败 (HTTP " + response.code() + "): "
                        + truncate(body, 200));
            }
            return parse(body, count);
        }
    }

    /**
     * 解析响应：优先取顶层 {@code references[]}，每个元素映射成一个 {@link SearchResult}。
     * 缺失 references 时回退到 message.content + ^[N]^ 标记，但结构化字段有限。
     */
    private List<SearchResult> parse(String json, int maxResults) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode references = root.path("references");
        List<SearchResult> results = new ArrayList<>();

        if (references.isArray()) {
            int position = 0;
            for (JsonNode ref : references) {
                if (position >= maxResults) {
                    break;
                }
                String title = firstNonEmpty(ref, "title", "web_anchor");
                String url = ref.path("url").asText("");
                String content = ref.path("content").asText("");
                String date = ref.path("date").asText("");
                if (title.isBlank() && content.isBlank()) {
                    continue;
                }
                if (!date.isBlank()) {
                    // 摘要里附日期，便于阅读
                    content = content.isBlank() ? "（" + date + "）" : "（" + date + "）" + content;
                }
                position++;
                results.add(SearchResult.of(position, title, url, content));
            }
        }

        if (results.isEmpty()) {
            log.info("Baidu Qianfan ai_search returned 0 references for query");
        }

        return results;
    }

    private static String firstNonEmpty(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("");
            if (!value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
