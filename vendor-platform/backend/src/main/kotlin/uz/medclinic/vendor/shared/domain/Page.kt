package uz.medclinic.vendor.shared.domain

/** Zero-based page request with a hard upper bound on [size]. */
data class PageQuery(val page: Int = 0, val size: Int = DEFAULT_SIZE) {
    init {
        require(page >= 0) { "page must be >= 0" }
        require(size in 1..MAX_SIZE) { "size must be in 1..$MAX_SIZE" }
    }

    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100

        /** Clamps untrusted request parameters instead of failing. */
        fun of(page: Int?, size: Int?): PageQuery =
            PageQuery((page ?: 0).coerceAtLeast(0), (size ?: DEFAULT_SIZE).coerceIn(1, MAX_SIZE))
    }
}

data class Page<T>(val items: List<T>, val page: Int, val size: Int, val totalItems: Long) {
    val totalPages: Int get() = if (totalItems == 0L) 0 else ((totalItems + size - 1) / size).toInt()

    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), page, size, totalItems)
}
