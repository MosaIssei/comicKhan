package ir.comicreader.fa.data

/** Numeric-aware ("natural") comparison so "page2" sorts before "page10". */
object Ordering {
    val Natural: Comparator<String> = Comparator { a, b -> compare(a, b) }

    fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ni = i
                while (ni < a.length && a[ni].isDigit()) ni++
                var nj = j
                while (nj < b.length && b[nj].isDigit()) nj++
                val na = a.substring(i, ni).trimStart('0')
                val nb = b.substring(j, nj).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ni
                j = nj
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
