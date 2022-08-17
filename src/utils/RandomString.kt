package tech.sozonov.blog.utils
import java.util.concurrent.ThreadLocalRandom


object RandomString {
    //val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun randomString(len: Int): String {
        if (len < 1) return ""
        val array = IntArray(len)
        for (i in 0 until len) {
            array[i] = ThreadLocalRandom.current().nextInt(0, 255)
        }
        return array
            .map{ it.toChar() }
            .joinToString("")

    }
}