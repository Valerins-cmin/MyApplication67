package com.example.myapplication67

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorScreenTest {
    private val biscuit = FeedPost(
        id = "biscuit",
        authorId = "demo",
        authorLogin = "cook",
        title = "Классический бисквит",
        description = "Яйца, мука и сахар",
        recipe = """Ингредиенты:
            |• 5 яиц
            |• 150 г муки
            |• 150 г сахара
            |• 1 щепотка соли
            |
            |Взбейте яйца с сахаром и добавьте муку.
        """.trimMargin(),
        imagePath = "",
        createdAt = "2026-11-06T00:00:00Z",
    )

    @Test fun parsesInputAndRejectsInvalidQuantity() {
        assertEquals(PantryItem("Мука", 250.0, PantryUnit.GRAM), parsePantryInput("мука-250-г"))
        assertEquals(PantryUnit.PIECE, parsePantryInput("Яйца-5-штук").unit)
        assertTrue(runCatching { parsePantryInput("Мука-0-г") }.isFailure)
        assertTrue(runCatching { parsePantryInput("Мука-250") }.isFailure)
    }

    @Test fun matchesAvailableIngredientsAndFlagsShortage() {
        val products = listOf(
            parsePantryInput("Яйца-5-шт"),
            parsePantryInput("Мука-200-г"),
            parsePantryInput("Сахар-250-г"),
        )
        val ready = findRecipeMatches(listOf(biscuit), products).single()
        assertTrue(ready.canCook)
        assertEquals(3, ready.matched)

        val short = findRecipeMatches(listOf(biscuit), products.dropLast(1) + parsePantryInput("Сахар-100-г")).single()
        assertFalse(short.canCook)
        assertEquals(listOf("сахара"), short.missing)
    }

    @Test fun convertsFlourFromCupsToGrams() {
        val pancake = biscuit.copy(recipe = "Ингредиенты:\n• ½ стакана обычной муки\n\nСмешайте ингредиенты.")
        assertTrue(findRecipeMatches(listOf(pancake), listOf(parsePantryInput("Мука-60-г"))).single().canCook)
        assertFalse(findRecipeMatches(listOf(pancake), listOf(parsePantryInput("Мука-50-г"))).single().canCook)
    }

    @Test fun addsRepeatedAmountsAndKeepsBellPepperRequired() {
        val recipe = biscuit.copy(recipe = "Ингредиенты:\n- 100 г сахара\n- 100 г сахара\n- 1 красный перец\n\nСмешайте.")
        val short = findRecipeMatches(listOf(recipe), listOf(parsePantryInput("Сахар-150-г"))).single()
        assertFalse(short.canCook)
        assertEquals(0, short.matched)
        val enoughSugar = findRecipeMatches(listOf(recipe), listOf(parsePantryInput("Сахар-200-г"))).single()
        assertEquals(1, enoughSugar.matched)
        assertEquals(listOf("красный перец"), enoughSugar.missing)
    }
}
