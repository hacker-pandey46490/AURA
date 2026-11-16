package com.aura.core.model

/** Returns null when the expectation holds, otherwise a human-readable reason it does not. */
fun checkExpectation(expect: Expectation, before: ScreenSnapshot?, after: ScreenSnapshot?): String? = when (expect) {
    Expectation.None -> null
    Expectation.ScreenChanged -> when {
        after == null -> "Screen not readable"
        before == null || after.packageName != before.packageName || after.root != before.root -> null
        else -> "Screen did not change"
    }
    is Expectation.TextVisible ->
        if (after?.containsText(expect.text) == true) null else "Text \"${expect.text}\" not found"
    is Expectation.PackageIs ->
        if (after?.packageName == expect.packageName) null
        else "Expected app ${expect.packageName} but saw ${after?.packageName ?: "nothing"}"
}
