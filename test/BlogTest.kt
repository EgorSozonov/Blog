import tech.sozonov.blog.core.Rewriter
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BlogTest {
    @Test
    fun `get JS module names 1`() {
        val input = """<html><head><script type="module" src="foo.js" /></head><body><div>asdf</div></body><html> """
        val result = Rewriter.parseJSModuleNames(input, "asdf/")
        assertEquals(result.size, 1)
        assertEquals(result[0], "foo")
    }

    @Test
    fun `get JS module names 2`() {
        val input = """<html><head>
<script type="module" src="foo.js" />
<script type="module" src="./bar/baz.js" />
<script type="module" src="./another.JS" />
</head><body><div>asdf</div></body>
<html>"""
        val result = Rewriter.parseJSModuleNames(input, "asdf/")
        assertEquals(result.size, 3)
        assertEquals(result[0], "foo")
        assertEquals(result[1], "asdf/bar/baz")
        assertEquals(result[2], "asdf/another")
    }

    @Test
    fun `get JS module names 3`() {
        val input = """<html><head>
<script type="module" src="plotting.js"></script>
<script type="module" src="./gcBenchmark.js"/>
</head><body><div>asdf</div></body>
<html>"""
        val result = Rewriter.parseJSModuleNames(input, "asdf/")
        assertEquals(result.size, 2)
        assertEquals(result[0], "plotting")
        assertEquals(result[1], "asdf/gcBenchmark")
    }
}