package com.aura.core.model

data class ParseResult(val steps: List<TaskStep>, val errors: List<String>)

/**
 * Parses single-step script lines (used by the flat parser here and by the graph parser).
 *
 *   launch Settings | wait 1500 | back | home
 *   click <selector> (alias: tap) | longclick <selector> | doubletap <selector>
 *   type <selector> = text | type = text
 *   scroll forward | scroll backward | swipe up|down|left|right
 *
 * Selectors: text:Foo, desc:Foo, id:name (a bare word means text:). Lines starting with # are comments.
 * Optional check after "=>": changed, text:Foo, pkg:com.example.app
 */
object TaskScriptParser {

    fun parse(script: String): ParseResult {
        val steps = mutableListOf<TaskStep>()
        val errors = mutableListOf<String>()
        script.lines().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            try {
                steps += parseStepLine(line)
            } catch (e: IllegalArgumentException) {
                errors += "Line ${i + 1}: ${e.message}"
            }
        }
        return ParseResult(steps, errors)
    }

    internal fun parseStepLine(line: String): TaskStep {
        val parts = line.split("=>", limit = 2)
        val body = parts[0].trim()
        val expect = if (parts.size > 1) parseExpectation(parts[1].trim()) else Expectation.None
        val cmd = body.substringBefore(' ').lowercase()
        val arg = body.substringAfter(' ', "").trim()

        val action: Action = when (cmd) {
            "back" -> Action.Back
            "home" -> Action.Home
            "wait" -> Action.Wait(arg.toLongOrNull()?.takeIf { it in 0L..60_000L } ?: fail("wait needs milliseconds (0-60000)"))
            "scroll" -> when (arg.lowercase()) {
                "forward", "down" -> Action.ScrollForward
                "backward", "up" -> Action.ScrollBackward
                else -> fail("scroll needs forward or backward")
            }
            "swipe" -> Action.Swipe(
                SwipeDirection.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) }
                    ?: fail("swipe needs up, down, left or right"),
            )
            "launch" -> if (arg.isEmpty()) fail("launch needs an app name") else Action.LaunchApp(arg)
            "click", "tap" -> Action.Click(parseSelector(arg))
            "longclick" -> Action.LongClick(parseSelector(arg))
            "doubletap" -> Action.DoubleTap(parseSelector(arg))
            "type" -> {
                val idx = arg.indexOf('=')
                if (idx < 0) fail("type needs: type <selector> = text   (or: type = text)")
                val selectorPart = arg.substring(0, idx).trim()
                val text = arg.substring(idx + 1).trim()
                if (text.isEmpty()) fail("type needs some text after =")
                Action.TypeText(if (selectorPart.isEmpty()) null else parseSelector(selectorPart), text)
            }
            else -> fail("unknown command '$cmd'")
        }
        return TaskStep(action, expect)
    }

    private fun parseSelector(arg: String): Selector {
        if (arg.isEmpty()) fail("a selector is required (text:..., desc:..., id:...)")
        return when {
            arg.startsWith("text:") -> Selector(text = arg.removePrefix("text:").trim())
            arg.startsWith("desc:") -> Selector(contentDescription = arg.removePrefix("desc:").trim())
            arg.startsWith("id:") -> Selector(viewId = arg.removePrefix("id:").trim())
            else -> Selector(text = arg)
        }.also { if (it.isEmpty()) fail("selector value is empty") }
    }

    internal fun parseExpectation(arg: String): Expectation = when {
        arg.equals("changed", ignoreCase = true) -> Expectation.ScreenChanged
        arg.startsWith("text:") -> Expectation.TextVisible(arg.removePrefix("text:").trim())
        arg.startsWith("pkg:") -> Expectation.PackageIs(arg.removePrefix("pkg:").trim())
        else -> fail("unknown check '$arg' (use changed, text:..., pkg:...)")
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
}
