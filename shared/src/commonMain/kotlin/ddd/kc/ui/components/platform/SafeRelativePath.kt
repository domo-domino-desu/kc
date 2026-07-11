package ddd.kc.ui.components.platform

internal fun isSafePathSegment(value: String): Boolean =
    value.isNotBlank() &&
        value != "." &&
        value != ".." &&
        '/' !in value &&
        '\\' !in value &&
        '\u0000' !in value

internal fun hasSafeRelativePath(directories: List<String>, fileName: String): Boolean =
    directories.all(::isSafePathSegment) && isSafePathSegment(fileName)
