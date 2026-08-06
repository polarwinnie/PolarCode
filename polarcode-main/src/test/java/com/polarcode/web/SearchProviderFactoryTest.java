package com.polarcode.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchProviderFactoryTest {

    // ========== 显式指定 provider ==========

    @Test
    void explicitProviderOverridesAutoDetect() {
        assertEquals("zhipu", SearchProviderFactory.pickProvider("zhipu", null, "key", "http://localhost"));
        assertEquals("searxng", SearchProviderFactory.pickProvider("searxng", "glm", "key", "http://localhost"));
        assertEquals("serpapi", SearchProviderFactory.pickProvider("serpapi", null, null, "http://localhost"));
    }

    // ========== 智谱自动检测 ==========

    @Test
    void autoSelectsZhipuWhenGlmKeyPresent() {
        assertEquals("zhipu", SearchProviderFactory.pickProvider(null, "glm-key", null, null));
        assertEquals("zhipu", SearchProviderFactory.pickProvider(null, "glm-key", "serp-key", "http://localhost"));
    }

    // ========== SerpAPI 自动检测 ==========

    @Test
    void autoSelectsSerpapiWhenOnlySerpKeyPresent() {
        assertEquals("serpapi", SearchProviderFactory.pickProvider(null, null, "any-key", null));
        assertEquals("serpapi", SearchProviderFactory.pickProvider("", "", "any-key", null));
    }

    // ========== SearXNG 自动检测 ==========

    @Test
    void autoSelectsSearxngWhenOnlyUrlPresent() {
        assertEquals("searxng", SearchProviderFactory.pickProvider(null, null, null, "http://localhost:8888"));
        assertEquals("searxng", SearchProviderFactory.pickProvider(null, "", "", "http://localhost:8888"));
    }

    // ========== 默认降级 ==========

    @Test
    void fallsBackToZhipuPlaceholder() {
        assertEquals("zhipu", SearchProviderFactory.pickProvider(null, null, null, null));
    }

    // ========== 大小写归一化 ==========

    @Test
    void normalizesExplicitToLowercase() {
        assertEquals("searxng", SearchProviderFactory.pickProvider("SEARXNG", null, null, null));
        assertEquals("serpapi", SearchProviderFactory.pickProvider("  SerpAPI  ", null, null, null));
        assertEquals("zhipu", SearchProviderFactory.pickProvider("ZHIPU", null, null, null));
    }

    // ========== 百度千帆：显式指定（各类别名）==========

    @Test
    void explicitBaiduQianfanAliases() {
        assertEquals("baidu-qianfan",
                SearchProviderFactory.pickProvider("baidu-qianfan", null, null, null, null));
        assertEquals("baidu-qianfan",
                SearchProviderFactory.pickProvider("baidu", null, null, null, null));
        assertEquals("baidu-qianfan",
                SearchProviderFactory.pickProvider("qianfan", null, null, null, null));
        assertEquals("baidu-qianfan",
                SearchProviderFactory.pickProvider("BAIDU", null, null, null, null));
    }

    // ========== 百度千帆自动检测：有 QIANFAN_API_KEY 就选 ==========

    @Test
    void autoSelectsBaiduQianfanWhenApiKeyPresent() {
        assertEquals("baidu-qianfan",
                SearchProviderFactory.pickProvider(null, null, null, null, "my-qianfan-key"));
    }

    // ========== 百度千帆优先级最高（高于智谱/SerpAPI/SearXNG）==========

    @Test
    void baiduQianfanHasHighestPriority() {
        assertEquals("baidu-qianfan",
                SearchProviderFactory.pickProvider(null, "glm-key", "serp-key",
                        "http://localhost", "qianfan-key"));
    }

    // ========== 无千帆 Key 时降级到其他 ==========

    @Test
    void fallsBackToOthersWhenNoQianfanKey() {
        assertEquals("zhipu",
                SearchProviderFactory.pickProvider(null, "glm-key", null, null, null));
        assertEquals("serpapi",
                SearchProviderFactory.pickProvider(null, null, "serp-key", null, null));
    }
}
