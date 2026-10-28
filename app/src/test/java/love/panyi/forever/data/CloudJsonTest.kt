package love.panyi.forever.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudJsonTest {

    @Test fun chatEndpoint_appendsPath() =
        assertEquals("https://api.deepseek.com/v1/chat/completions", chatEndpoint(" https://api.deepseek.com/v1/ "))

    @Test fun chatEndpoint_keepsFullPath() =
        assertEquals("https://x.com/v1/chat/completions", chatEndpoint("https://x.com/v1/chat/completions"))

    @Test fun chatEndpoint_rejectsHttp() {
        assertThrows(AiException::class.java) { chatEndpoint("http://10.0.2.2:11434/v1") }
    }

    @Test fun chatRequestJson_shape() {
        val o = JSONObject(chatRequestJson("m1", "sys", "usr"))
        assertEquals("m1", o.getString("model"))
        assertEquals(0.2, o.getDouble("temperature"), 1e-9)
        val msgs = o.getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        assertEquals("usr", msgs.getJSONObject(1).getString("content"))
    }

    @Test fun chatResponseContent_readsFirstChoice() =
        assertEquals("{}", chatResponseContent("""{"choices":[{"message":{"role":"assistant","content":"{}"}}]}"""))

    @Test fun chatResponseContent_missingIsParseError() {
        assertThrows(PlanParseException::class.java) { chatResponseContent("""{"choices":[]}""") }
    }

    @Test fun httpErrorMessage_cases() {
        assertEquals("API Key 无效", httpErrorMessage(401, ""))
        assertEquals("请求失败（429）：Rate limit", httpErrorMessage(429, """{"error":{"message":"Rate limit"}}"""))
        assertEquals("请求失败（500）", httpErrorMessage(500, "<html>"))
    }

    @Test fun cloudConfig_requiresHttpsAndAllFields() {
        assertTrue(CloudConfig("https://a.com", "k", "m").isComplete)
        assertFalse(CloudConfig("http://a.com", "k", "m").isComplete)
        assertFalse(CloudConfig("https://a.com", " ", "m").isComplete)
        assertFalse(CloudConfig("https://a.com", "sk-\u200Babc", "m").isComplete)
        assertFalse(CloudConfig("https://a.com", "sk-a\nbc", "m").isComplete)
        assertFalse(CloudConfig("https://a.com", "sk-\uFF41bc", "m").isComplete)
        assertTrue(CloudConfig("https://a.com", " sk-abc_123 ", "m").isComplete)
    }
}
