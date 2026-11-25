package uz.medclinic.platform.web

import uz.medclinic.platform.domain.Page

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T, R> of(page: Page<T>, mapper: (T) -> R) =
            PageResponse(page.items.map(mapper), page.page, page.size, page.totalItems, page.totalPages)
    }
}
