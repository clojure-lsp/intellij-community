// "Replace with 'new<T>()'" "true"

@Deprecated(message = "", replaceWith = ReplaceWith("new<T>()"))
fun <T> old(t: T) {
}

fun <T> new() {
}

fun test() {
    <caret>old(1)
}