package com.aura.core.graph

import com.aura.core.model.Action
import com.aura.core.model.Expectation
import com.aura.core.model.TaskScriptParser
import com.aura.core.model.TaskStep
import com.aura.core.model.describe

data class GraphParseResult(val graph: TaskGraph, val errors: List<String>)

/**
 * Task script with blocks. Everything from the flat script still works, plus:
 *
 *   repeat 3 ... end              loop (1-100 times)
 *   if text:Foo ... else ... end  condition (text:... or pkg:...); else is optional
 *   group Name ... end            named sub-sequence
 *   optional <step>               failure of this step is skipped instead of stopping the task
 *   verify text:Foo               standalone check (also: verify pkg:..., verify changed)
 */
object GraphScriptParser {
    private const val MAX_REPEAT = 100

    private abstract class Block(val line: Int) {
        val children = mutableListOf<GraphNode>()
        open fun target(): MutableList<GraphNode> = children
    }

    private class RootBlock : Block(0)
    private class RepeatBlock(line: Int, val times: Int) : Block(line)
    private class GroupBlock(line: Int, val title: String) : Block(line)
    private class IfBlock(line: Int, val condition: Expectation) : Block(line) {
        val elseChildren = mutableListOf<GraphNode>()
        var inElse = false
        override fun target(): MutableList<GraphNode> = if (inElse) elseChildren else children
    }

    fun parse(script: String, title: String = "Task"): GraphParseResult {
        var counter = 0
        fun nextId() = "n${++counter}"

        val errors = mutableListOf<String>()
        val root = RootBlock()
        val stack = ArrayDeque<Block>()
        stack.addLast(root)

        fun add(node: GraphNode) {
            stack.last().target().add(node)
        }

        script.lines().forEachIndexed { index, raw ->
            val lineNo = index + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            try {
                val keyword = line.substringBefore(' ').lowercase()
                val arg = line.substringAfter(' ', "").trim()
                when (keyword) {
                    "repeat" -> {
                        val n = arg.toIntOrNull()
                        if (n == null || n !in 1..MAX_REPEAT) fail("repeat needs a number from 1 to $MAX_REPEAT")
                        stack.addLast(RepeatBlock(lineNo, n))
                    }
                    "group" -> stack.addLast(GroupBlock(lineNo, arg.ifEmpty { "Group" }))
                    "if" -> stack.addLast(IfBlock(lineNo, parseCondition(arg)))
                    "else" -> {
                        val top = stack.last()
                        if (top !is IfBlock || top.inElse) fail("'else' without a matching 'if'")
                        top.inElse = true
                    }
                    "end" -> {
                        if (stack.size <= 1) fail("'end' without an open block")
                        val block = stack.removeLast()
                        if (block.children.isEmpty()) fail("this block is empty")
                        val node: GraphNode = when (block) {
                            is RepeatBlock -> LoopNode(nextId(), block.times, block.children.toList())
                            is GroupBlock -> GroupNode(nextId(), block.title, block.children.toList())
                            is IfBlock -> ConditionNode(
                                nextId(),
                                block.condition,
                                GroupNode(nextId(), "then", block.children.toList()),
                                if (block.inElse && block.elseChildren.isNotEmpty()) {
                                    GroupNode(nextId(), "else", block.elseChildren.toList())
                                } else {
                                    null
                                },
                            )
                            else -> fail("internal parser error")
                        }
                        add(node)
                    }
                    "verify" -> {
                        val expect = TaskScriptParser.parseExpectation(arg)
                        add(
                            ActionNode(
                                nextId(),
                                TaskStep(Action.Wait(0), expect, maxRetries = 3),
                                label = "verify ${expect.describe()}",
                            ),
                        )
                    }
                    "optional" -> add(ActionNode(nextId(), TaskScriptParser.parseStepLine(arg), optional = true))
                    else -> add(ActionNode(nextId(), TaskScriptParser.parseStepLine(line)))
                }
            } catch (e: IllegalArgumentException) {
                errors += "Line $lineNo: ${e.message}"
            }
        }

        stack.drop(1).forEach { errors += "Line ${it.line}: block is not closed with 'end'" }
        return GraphParseResult(TaskGraph(title, root.children.toList()), errors)
    }

    private fun parseCondition(arg: String): Expectation {
        val e = TaskScriptParser.parseExpectation(arg)
        if (e !is Expectation.TextVisible && e !is Expectation.PackageIs) fail("if needs text:... or pkg:...")
        return e
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
}
