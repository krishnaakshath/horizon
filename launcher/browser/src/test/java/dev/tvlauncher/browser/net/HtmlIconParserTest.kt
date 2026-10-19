package dev.tvlauncher.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlIconParserTest {
    private val base = "https://www.example.com/news/today.html"

    @Test fun resolves_relativeAbsoluteAndProtocolRelative() {
        val html = """
            <head>
            <link rel="icon" href="/img/a.png" sizes="32x32">
            <link rel="icon" href="b.png" sizes="31x31">
            <link rel='icon' href='//cdn.example.net/c.png' sizes='30x30'>
            <link rel=icon href=https://static.example.org/d.png sizes=29x29>
            </head>"""
        assertEquals(
            listOf(
                "https://www.example.com/img/a.png",
                "https://www.example.com/news/b.png",
                "https://cdn.example.net/c.png",
                "https://static.example.org/d.png"
            ),
            HtmlIconParser.parse(html, base)
        )
    }

    @Test fun prefers_largerSizes() {
        val html = """<link rel="icon" sizes="16x16" href="/16.png"><link rel="icon" href="/nosize.png">
            <link rel="icon" sizes="192x192" href="/192.png"><link rel="icon" sizes="48x48 96x96" href="/multi.png">"""
        assertEquals(listOf("https://www.example.com/192.png", "https://www.example.com/multi.png",
            "https://www.example.com/16.png", "https://www.example.com/nosize.png"), HtmlIconParser.parse(html, base))
    }

    @Test fun appleTouchIcon_beatsFavicon() {
        val html = """<link rel="shortcut icon" href="/favicon.ico" sizes="256x256">
            <LINK HREF="/apple.png" REL="apple-touch-icon">
            <link rel="apple-touch-icon-precomposed" href="/apple-pre.png">"""
        val r = HtmlIconParser.parse(html, base)
        assertEquals("https://www.example.com/apple.png", r[0])
        assertEquals("https://www.example.com/apple-pre.png", r[1])
        assertEquals("https://www.example.com/favicon.ico", r[2])
    }

    @Test fun ignores_dataUris_svg_andNonIconLinks() {
        val html = """<link rel="icon" href="data:image/png;base64,AAAA">
            <link rel="stylesheet" href="/s.css"><link rel="icon" type="image/svg+xml" href="/i.svg">
            <link rel="mask-icon" href="/m.svg"><link rel="icon" href="javascript:alert(1)">
            <link rel="icon" href="/ok.png">"""
        assertEquals(listOf("https://www.example.com/ok.png"), HtmlIconParser.parse(html, base))
    }

    @Test fun httpIcons_upgradedToHttps_andBaseHrefHonored() {
        val html = """<base href="https://assets.example.com/root/"><link rel="icon" href="i.png">
            <link rel="icon" href="http://x.example.com/h.png">"""
        assertEquals(listOf("https://assets.example.com/root/i.png", "https://x.example.com/h.png"),
            HtmlIconParser.parse(html, base))
    }

    @Test fun junkHtml_neverThrows() {
        val junk = listOf(
            null, "", "<link", "<link rel=\"icon\" href=\"", "<<<>>>\u0000￿", "<link rel=icon href=\"http://[::1\">",
            "<link rel=\"icon\" href=\"%%%\">", "<link rel=\"icon\" href=\"https://a b c/\">",
            "<link rel=\"icon\" sizes=\"99999999999999999999x1\" href=\"/x.png\">", "<".repeat(200_000)
        )
        junk.forEach { HtmlIconParser.parse(it, base) }
        assertTrue(HtmlIconParser.parse("<link rel=icon href=/x.png>", "not a url").isEmpty())
    }

    @Test fun onlyFirst64Kb_isParsed() {
        val html = " ".repeat(70 * 1024) + "<link rel=\"icon\" href=\"/late.png\">"
        assertTrue(HtmlIconParser.parse(html, base).isEmpty())
    }
}
