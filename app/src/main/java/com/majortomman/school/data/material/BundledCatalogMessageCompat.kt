package com.majortomman.school.data.material

/**
 * 兼容预制目录校验提示中的紧邻中文插值；后续提示文案集中化时移除。
 */
internal val BundledCatalogBook.subjectTitle导入: String
    get() = "${subjectTitle}导入"
