import tech.sozonov.blog.datasource.file.BlogFile
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BlogTest {
    @Test
    fun `get JS module names 1`() {
        val input = """<html><head><script type="module" src="foo.mjs" /></head><body><div>asdf</div></body><html> """
        val result = BlogFile.parseJSModuleNames(input, "asdf/")
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
        val result = BlogFile.parseJSModuleNames(input, "asdf/")
        assertEquals(result.size, 3)
        assertEquals(result[0], "foo")
        assertEquals(result[1], "./bar/baz")
        assertEquals(result[2], "./another")
    }

    @Test
    fun `get JS module names 3`() {
        val input = """<html><head>
<script type="module" src="plotting.mjs"></script>
<script type="module" src="./gcBenchmark.mjs"/>
</head><body><div>asdf</div></body>
<html>"""
        val result = BlogFile.parseJSModuleNames(input, "asdf/")
        assertEquals(result.size, 2)
        assertEquals(result[0], "plotting")
        assertEquals(result[1], "asdf/gcBenchmark")
    }
}