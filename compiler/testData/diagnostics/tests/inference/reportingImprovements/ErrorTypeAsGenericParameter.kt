// !WITH_NEW_INFERENCE

package a

fun <T, R, S> foo(block: (T)-> R, <!UNUSED_PARAMETER!>second<!>: (T)-> S) = block

fun main() {
    val fff = { <!UNUSED_ANONYMOUS_PARAMETER!>x<!>: Int -> <!UNRESOLVED_REFERENCE!>aaa<!> }
    <!NI;NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>foo<!>(<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>fff<!>, { x -> x + 1 })
}

