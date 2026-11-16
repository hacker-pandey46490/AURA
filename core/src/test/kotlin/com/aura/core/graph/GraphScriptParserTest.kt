package com.aura.core.graph

import com.aura.core.model.Action
import com.aura.core.model.Expectation
import com.aura.core.model.Selector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphScriptParserTest {

    @Test
    fun parsesLoopsConditionsAndOptionalSteps() {
        val script = """
            repeat 2
              click Foo
              optional scroll backward
            end
            if text:Bar
              back
            else
              home
            end
        """.trimIndent()
        val r = GraphScriptParser.parse(script)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(2, r.graph.roots.size)

        val loop = r.graph.roots[0] as LoopNode
        assertEquals(2, loop.times)
        assertEquals(2, loop.children.size)
        assertTrue((loop.children[1] as ActionNode).optional)

        val cond = r.graph.roots[1] as ConditionNode
        assertEquals(Expectation.TextVisible("Bar"), cond.condition)
        assertEquals(1, cond.thenBranch.children.size)
        assertEquals(1, cond.elseBranch!!.children.size)
    }

    @Test
    fun tapIsAnAliasForClickAndVerifyBecomesANode() {
        val r = GraphScriptParser.parse("tap wifi\nverify text:Wi-Fi")
        assertTrue(r.errors.isEmpty())
        assertEquals(Action.Click(Selector(text = "wifi")), (r.graph.roots[0] as ActionNode).step.action)
        val verify = r.graph.roots[1] as ActionNode
        assertEquals(Expectation.TextVisible("Wi-Fi"), verify.step.expect)
    }

    @Test
    fun nodeIdsAreUnique() {
        val r = GraphScriptParser.parse("back\nrepeat 2\nhome\nend\ngroup G\nback\nend")
        val ids = r.graph.flattened().map { it.node.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun reportsStructureErrors() {
        assertTrue(GraphScriptParser.parse("else\nback").errors.any { it.contains("else") })
        assertTrue(GraphScriptParser.parse("end").errors.any { it.contains("end") })
        assertTrue(GraphScriptParser.parse("repeat 2\nback").errors.any { it.contains("not closed") })
        assertTrue(GraphScriptParser.parse("repeat 0\nback\nend").errors.isNotEmpty())
        assertTrue(GraphScriptParser.parse("repeat 101\nback\nend").errors.isNotEmpty())
        assertTrue(GraphScriptParser.parse("if changed\nback\nend").errors.isNotEmpty())
    }

    @Test
    fun flatLinesStillWork() {
        val r = GraphScriptParser.parse("launch Settings => pkg:com.android.settings\nwait 500")
        assertTrue(r.errors.isEmpty())
        assertEquals(2, r.graph.roots.size)
    }
}
