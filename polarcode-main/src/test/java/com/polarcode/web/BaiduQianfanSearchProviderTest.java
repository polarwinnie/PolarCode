package com.polarcode.web;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaiduQianfanSearchProvider 单元测试。
 *
 * <p>端点: POST https://qianfan.baidubce.com/v2/ai_search/chat/completions
 * <p>通过 OkHttp MockWebServer + Interceptor 重写 URL，
 * 包级私有构造器注入 mock client。
 */
class BaiduQianfanSearchProviderTest {

    private MockWebServer server;
    private OkHttpClient client;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    var original = chain.request();
                    assertEquals("https://qianfan.baidubce.com/v2/ai_search/chat/completions",
                            original.url().toString());
                    HttpUrl mockUrl = server.url(original.url().encodedPath());
                    return chain.proceed(original.newBuilder().url(mockUrl).build());
                })
                .build();
    }

    @AfterEach
    void shutdown() throws IOException {
        server.shutdown();
    }

    // ==================== isReady ====================

    @Test
    void readyWhenApiKeyConfigured() {
        assertTrue(new BaiduQianfanSearchProvider("some-key", "ernie-4.5-turbo-32k", false, client).isReady());
        assertFalse(new BaiduQianfanSearchProvider("", "ernie-4.5-turbo-32k", false, client).isReady());
        assertFalse(new BaiduQianfanSearchProvider(null, "ernie-4.5-turbo-32k", false, client).isReady());
    }

    // ==================== 正常搜索：从 references[] 解析 ====================

    @Test
    void parsesReferencesFromResponse() throws IOException, InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "request_id": "test-123",
                          "choices": [
                            {
                              "finish_reason": "stop",
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "岚图汽车是东风集团旗下高端新能源品牌^[1][2]^"
                              }
                            }
                          ],
                          "references": [
                            {
                              "id": 1,
                              "title": "岚图汽车官网",
                              "url": "https://www.voyah.com.cn",
                              "content": "岚图汽车是东风汽车集团旗下高端智慧新能源品牌",
                              "date": "2026-7-16",
                              "web_anchor": "岚图汽车"
                            },
                            {
                              "id": 2,
                              "title": "百度百科",
                              "url": "https://baike.baidu.com/item/岚图汽车",
                              "content": "岚图汽车科技股份有限公司",
                              "date": null,
                              "web_anchor": "百度百科"
                            }
                          ]
                        }
                        """));

        var provider = new BaiduQianfanSearchProvider("test-key", "ernie-4.5-turbo-32k", false, client);
        List<SearchResult> results = provider.search("岚图汽车", 5);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).position());
        assertEquals("岚图汽车官网", results.get(0).title());
        assertEquals("www.voyah.com.cn", results.get(0).source());
        assertEquals("https://www.voyah.com.cn", results.get(0).url());
        // date 被合并到 snippet 前面
        assertTrue(results.get(0).snippet().startsWith("（2026-7-16）"), results.get(0).snippet());
        assertEquals("百度百科", results.get(1).title());

        // 验证请求体
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("Bearer test-key", req.getHeader("Authorization"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"ernie-4.5-turbo-32k\""), body);
        assertTrue(body.contains("\"role\":\"user\""), body);
        assertTrue(body.contains("岚图汽车"), body);
    }

    // ==================== topK 截断 ====================

    @Test
    void topKRespected() throws IOException {
        StringBuilder refs = new StringBuilder("[");
        for (int i = 1; i <= 10; i++) {
            if (i > 1) refs.append(",");
            refs.append("{\"id\":").append(i)
                    .append(",\"title\":\"标题").append(i).append("\"")
                    .append(",\"url\":\"https://example.com/").append(i).append("\"")
                    .append(",\"content\":\"内容").append(i).append("\"}");
        }
        refs.append("]");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"references\":" + refs + "}"));

        var provider = new BaiduQianfanSearchProvider("t", "ernie-4.5-turbo-32k", false, client);
        List<SearchResult> results = provider.search("test", 3);
        assertEquals(3, results.size());
    }

    // ==================== 错误处理 ====================

    @Test
    void unauthorizedReturnsClearMessage() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid key\"}"));

        var provider = new BaiduQianfanSearchProvider("bad-key", "ernie-4.5-turbo-32k", false, client);
        IOException ex = assertThrows(IOException.class, () -> provider.search("test", 5));
        assertTrue(ex.getMessage().contains("API Key 无效") || ex.getMessage().contains("401"));
    }

    @Test
    void forbiddenReturnsClearMessage() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":\"forbidden\"}"));

        var provider = new BaiduQianfanSearchProvider("bad-key", "ernie-4.5-turbo-32k", false, client);
        IOException ex = assertThrows(IOException.class, () -> provider.search("test", 5));
        assertTrue(ex.getMessage().contains("API Key 无效") || ex.getMessage().contains("403"));
    }

    @Test
    void serverErrorIncludesBodyPreview() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"内部错误\"}"));

        var provider = new BaiduQianfanSearchProvider("some-key", "ernie-4.5-turbo-32k", false, client);
        IOException ex = assertThrows(IOException.class, () -> provider.search("test", 5));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void searchWithoutKeyThrows() {
        var provider = new BaiduQianfanSearchProvider("", "ernie-4.5-turbo-32k", false, client);
        IOException ex = assertThrows(IOException.class, () -> provider.search("test", 5));
        assertTrue(ex.getMessage().contains("未配置"));
    }

    // ==================== 空结果 ====================

    @Test
    void emptyReferencesReturnsEmptyList() throws IOException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"request_id":"x","references":[]}
                        """));

        var provider = new BaiduQianfanSearchProvider("token", "ernie-4.5-turbo-32k", false, client);
        List<SearchResult> results = provider.search("无结果查询", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void missingReferencesFieldReturnsEmptyList() throws IOException {
        // 没有 references 字段时（部分错误响应可能没带），不应抛错
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"request_id":"x","choices":[]}
                        """));

        var provider = new BaiduQianfanSearchProvider("token", "ernie-4.5-turbo-32k", false, client);
        List<SearchResult> results = provider.search("test", 5);
        assertTrue(results.isEmpty());
    }

    // ==================== 空标题/空内容跳过 ====================

    @Test
    void emptyFieldsAreSkipped() throws IOException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "references": [
                            {"id":1,"title":"","url":"","content":""},
                            {"id":2,"title":"有效","url":"https://example.com","content":"正文"}
                          ]
                        }
                        """));

        var provider = new BaiduQianfanSearchProvider("key", "ernie-4.5-turbo-32k", false, client);
        List<SearchResult> results = provider.search("test", 10);
        assertEquals(1, results.size());
        assertEquals("有效", results.get(0).title());
        assertEquals(1, results.get(0).position());
    }

    // ==================== name() ====================

    @Test
    void providerName() {
        assertEquals("baidu-qianfan",
                new BaiduQianfanSearchProvider("key", "ernie-4.5-turbo-32k", false, client).name());
    }

    // ==================== 模型覆盖 ====================

    @Test
    void modelOverrideInRequest() throws IOException, InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"references\":[]}"));

        var provider = new BaiduQianfanSearchProvider("key", "custom-model-v1", false, client);
        provider.search("test", 5);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"custom-model-v1\""), body);
    }

    @Test
    void blankModelFallsBackToDefault() {
        var provider = new BaiduQianfanSearchProvider("key", "  ", false, client);
        assertNotNull(provider);
        assertTrue(provider.isReady());
    }

    // ==================== 请求体扩展字段（stream / instruction / corner_markers / deep_search）====================

    @Test
    void requestBodyContainsAllExtensionFields() throws IOException, InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"references\":[]}"));

        var provider = new BaiduQianfanSearchProvider("key", "ernie-4.5-turbo-32k", false, client);
        provider.search("近日油价调整消息", 5);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"stream\":false"), body);
        assertTrue(body.contains("\"instruction\":\"##\""), body);
        assertTrue(body.contains("\"enable_corner_markers\":true"), body);
        assertTrue(body.contains("\"enable_deep_search\":false"), body);
        assertTrue(body.contains("近日油价调整消息"), body);
    }

    @Test
    void deepSearchEnabledWhenRequested() throws IOException, InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"references\":[]}"));

        var provider = new BaiduQianfanSearchProvider("key", "ernie-4.5-turbo-32k", true, client);
        provider.search("近日油价调整消息", 5);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"enable_deep_search\":true"), body);
    }
}
