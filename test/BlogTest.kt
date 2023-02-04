import tech.sozonov.blog.datasource.file.BlogFile
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BlogTest {
    @Test
    fun `get JS module names 1`() {
        val input = """<html><head><script type="module" src="foo.mjs" /></head><body><div>asdf</div></body><html> """
        val result = BlogFile.getJSModuleNames(input)
        assertEquals(result.size, 1)
        assertEquals(result[0], "foo")
    }

    @Test
    fun `get JS module names 2`() {
        val input = """<html><head>
<script type="module" src="foo.mjs" />
<script type="module" src="./bar/baz.mjs" />
<script type="module" src="./another.MJS" />
</head><body><div>asdf</div></body>
<html>"""
        val result = BlogFile.getJSModuleNames(input)
        assertEquals(result.size, 3)
        assertEquals(result[0], "foo")
        assertEquals(result[1], "./bar/baz")
        assertEquals(result[2], "./another")
    }

}