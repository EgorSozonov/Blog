package tech.sozonov.blog.core
import java.lang.StringBuilder
import java.util.*
import kotlin.math.*
import tech.sozonov.blog.utils.*
import java.time.LocalDateTime
import java.time.Month
import kotlin.Triple


class NavTree() {
    var name: String
    var children: MutableList<NavTree>


    init {
        name = ""
        children = mutableListOf<NavTree>()
    }

    constructor(_name: String, _children: MutableList<NavTree>): this() {
        name = _name
        children = _children
    }


    /**
     * Make breadcrumbs that trace the way to this file through the navigation tree.
     * Attempts to follow the spine, but this doesn't work for temporal nav trees, so in case of an element not found
     * it switches to the slow version (which searches through all the leaves).
     */
    fun mkBreadcrumbs(subAddress: String): IntArray {
        val spl = subAddress.split("/")
        val result = IntArray(spl.size)
        if (this.name != "" || !this.children.any()) return IntArray(0)

        var curr = this.children
        for (i in 0 until spl.lastIndex) {
            result[i] = curr.indexOfFirst { it.name == spl[i] }
            // if address is not found in spine, maybe this is a temporal tree and it can be found in the leaves
            if (result[i] < 0) return mkBreadcrumbsTemporal(subAddress)
            curr = curr[result[i]].children
        }
        val leafIndex = curr.indexOfFirst { it.name == subAddress }
        if (leafIndex < 0) return IntArray(0)
        result[spl.lastIndex] = leafIndex
        return result
    }


    /**
     * Make breadcrumbs that trace the way to this file through the navigation tree.
     * Searches the leaves only, which is necessary for tempooral nav trees.
     */
    protected fun mkBreadcrumbsTemporal(subAddress: String): IntArray {
        val stack = Stack<Tuple<NavTree, Int>>()

        stack.push(Tuple(this, 0))
        while (stack.any()) {
            val top = stack.peek()

            if (top.second < top.first.children.size) {
                val next = top.first.children[top.second]

                if (next.children.size > 0) {
                    stack.push(Tuple(next, 0))
                } else {
                    if (next.name.lowercase() == subAddress) {
                        val result = IntArray(stack.size)
                        for (i in stack.indices) {
                            result[i] = stack[i].second
                        }
                        return result
                    }
                    ++top.second
                }
            } else {
                stack.pop()
                if (stack.any()) {
                    val prevTop = stack.peek()
                    ++prevTop.second
                }
            }
        }
        return IntArray(0)
    }


    fun toJokeScript(): String {
        val stack = ArrayDeque<Tuple<NavTree, Int>>()
        if (this.children.size == 0) return ""

        val result = StringBuilder(100)
        stack.push(Tuple(this, 0))
        while (stack.any()) {
            val top = stack.peek()

            if (top.second < top.first.children.size) {
                val next = top.first.children[top.second]
                if (next.children.size > 0) {
                    result.append("[\"")
                    result.append(next.name)
                    result.append("\", [")
                    stack.push(Tuple(next, 0))
                } else {
                    result.append("[\"")
                    result.append(next.name)
                    result.append("\", [], ], ")
                }

            } else {
                stack.pop()
                if (stack.any()) result.append("], ], ")
            }
            top.second += 1
        }
        return result.toString()
    }


    companion object {
        fun of(docCache: DocumentCache): Tuple<NavTree, NavTree> {
            val arrNavigation = docCache.toPageArray()
            val topical = topicalOf(arrNavigation)
            val temporal = temporalOf(arrNavigation)
            return Tuple(topical, temporal)
        }

        protected val comparatorFolders = Comparator<Triple<String, LocalDateTime, List<String>>> {
            x, y ->
                val folderLengthCommon = min(x.third.size, y.third.size)
                for (i in 0 until (folderLengthCommon - 1)) {
                    val cmp = x.third[i].compareTo(y.third[i])
                    if (cmp != 0) { return@Comparator cmp }
                }
                if (x.third.size != y.third.size) {
                    return@Comparator y.third.size.compareTo(x.third.size)
                } else {
                    return@Comparator x.third.last().compareTo(y.third.last())
                }
        }

        protected fun topicalOf(pages: List<Tuple<String, LocalDateTime>>): NavTree {
            val pagesByName = pages
                    .map{ x -> Triple(x.first, x.second, x.first.split("/"))}
                    .sortedWith ( comparatorFolders)

//            val fNSplits = pagesByName.map {
//                it.first.split("/")
//            }
            val stack = mutableListOf<NavTree>()
            val root = NavTree("", mutableListOf())
            stack.add(root)

            for (i in pagesByName.indices) {
                val spl = pagesByName[i].third
                var lenSamePrefix: Int = min(stack.size - 1, spl.size) - 1
                while (lenSamePrefix > -1
                    && stack[lenSamePrefix + 1].name != spl[lenSamePrefix]) --lenSamePrefix
                for (j in (lenSamePrefix + 1) until spl.size) {
                    val newElem = if (j == spl.size - 1) {
                            NavTree(pagesByName[i].first, mutableListOf())
                        } else {
                            NavTree(spl[j], mutableListOf())
                        }
                    if (j < stack.lastIndex) {
                        stack[j + 1] = newElem
                    } else {
                        stack.add(newElem)
                    }
                    val prev = stack[j]
                    prev.children.add(newElem)
                }
            }
            return root
        }


        protected fun temporalOf(pages: List<Tuple<String, LocalDateTime>>): NavTree {
            val pagesByDate = pages.sortedWith(compareBy {it.second})

            val stack = mutableListOf<NavTree>()
            val root = NavTree("", mutableListOf())
            stack.add(root)

            for (i in pagesByDate.indices) {
                val page = pagesByDate[i]
                val yearName = page.second.year.toString()
                val monthName = toName(page.second.month)

                var lenSamePrefix = min(stack.size - 1, 2) - 1
                if (lenSamePrefix > -1 && yearName != stack[1].name) lenSamePrefix = -1
                if (lenSamePrefix > 0 && monthName != stack[2].name) lenSamePrefix = 0
                for (j in (lenSamePrefix + 1) until 3) {
                    val name = when (j) {
                        2 -> { page.first }
                        1 -> { monthName }
                        else -> { yearName }
                    }

                    val newElem = NavTree(name, mutableListOf()) // leaves contain the full path
                    if (j < stack.lastIndex) {
                        stack[j + 1] = newElem
                    } else {
                        stack.add(newElem)
                    }
                    val prev = stack[j]
                    prev.children.add(newElem)
                }
            }
            return root
        }


        protected fun toName(month: java.time.Month): String {
            return when (month) {
                Month.JANUARY -> "Jan"
                Month.FEBRUARY -> "Feb"
                Month.MARCH -> "Mar"
                Month.APRIL -> "Apr"
                Month.MAY -> "May"
                Month.JUNE -> "Jun"
                Month.JULY -> "Jul"
                Month.AUGUST -> "Aug"
                Month.SEPTEMBER -> "Sep"
                Month.OCTOBER -> "Oct"
                Month.NOVEMBER -> "Nov"
                Month.DECEMBER -> "Dec"
            }
        }
    }
}