package com.aura.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScriptParserTest {

    @Test
    fun parsesBasicScriptWithExpectation() {
        val r = TaskScriptParser.parse("launch Settings\nwait 500\nscroll forward => changed")
        assertTrue(r.errors.isEmpty())
        assertEquals(3, r.steps.size)
        assertEquals(Action.LaunchApp("Settings"), r.steps[0].action)
        assertEquals(Action.Wait(500), r.steps[1].action)
        assertEquals(Expectation.ScreenChanged, r.steps[2].expect)
    }

    @Test
    fun parsesSelectorsAndTyping() {
        val r = TaskScriptParser.parse("click desc:Search settings\ntype id:search_edit = hello world\ntype = hi\nclick Wi-Fi")
        assertTrue(r.errors.isEmpty())
        assertEquals(Action.Click(Selector(contentDescription = "Search settings")), r.steps[0].action)
        assertEquals(Action.TypeText(Selector(viewId = "search_edit"), "hello world"), r.steps[1].action)
        assertEquals(Action.TypeText(null, "hi"), r.steps[2].action)
        assertEquals(Action.Click(Selector(text = "Wi-Fi")), r.steps[3].action)
    }

    @Test
    fun skipsCommentsAndBlankLines() {
        val r = TaskScriptParser.parse("# note\n\nback\n  \nhome")
        assertEquals(2, r.steps.size)
        assertTrue(r.errors.isEmpty())
    }

    @Test
    fun reportsErrorsWithLineNumbers() {
        val r = TaskScriptParser.parse("fly away\nwait abc")
        assertEquals(0, r.steps.size)
        assertEquals(2, r.errors.size)
        assertTrue(r.errors[0].startsWith("Line 1:"))
        assertTrue(r.errors[1].startsWith("Line 2:"))
    }

    @Test
    fun sensitivityGuardFlagsRiskyTargetsOnly() {
        val risky = TaskStep(Action.Click(Selector(text = "Send")))
        val safe = TaskStep(Action.Click(Selector(text = "Wi-Fi")))
        assertTrue(SensitivityGuard.reason(risky) != null)
        assertTrue(SensitivityGuard.reason(safe) == null)
        assertEquals(listOf(0), SensitivityGuard.sensitiveSteps(listOf(risky, safe)).map { it.first })
    }
}
