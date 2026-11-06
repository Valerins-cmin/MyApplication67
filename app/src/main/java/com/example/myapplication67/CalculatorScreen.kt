package com.example.myapplication67

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.myapplication67.ui.theme.LocalAppBackgroundBrush
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal enum class PantryUnit(val label: String) {
    GRAM("г"), KILOGRAM("кг"), MILLILITER("мл"), LITER("л"), PIECE("шт"),
    CUP("стакан"), TABLESPOON("ст. л."), TEASPOON("ч. л."), CAN("банка"),
    BUNCH("пучок"), CLOVE("зубчик"), PINCH("щепотка"), POUND("фунт"), OUNCE("унция"),
}

internal data class PantryItem(val name: String, val amount: Double, val unit: PantryUnit) {
    val label: String get() = "$name ${formatAmount(amount)} ${unit.label}"
}

internal data class RecipeMatch(
    val post: FeedPost,
    val matched: Int,
    val total: Int,
    val missing: List<String>,
) {
    val canCook: Boolean get() = missing.isEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorScreen(
    modifier: Modifier = Modifier,
    dataSource: FeedDataSource,
    profileKey: String,
) {
    val context = LocalContext.current
    val background = LocalAppBackgroundBrush.current
    val preferences = remember(context, profileKey) {
        context.getSharedPreferences("pantry_$profileKey", Context.MODE_PRIVATE)
    }
    var input by rememberSaveable(profileKey) { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var products by remember(preferences) { mutableStateOf(readPantry(preferences.getString("items", null))) }
    var posts by remember(dataSource) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember(dataSource) { mutableStateOf(true) }
    var loadError by remember(dataSource) { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var openedPost by remember { mutableStateOf<FeedPost?>(null) }
    val matches = remember(posts, products) { findRecipeMatches(posts, products) }
    val ready = matches.filter { it.canCook }.take(12)
    val close = matches.filter { !it.canCook && it.matched > 0 }
        .sortedWith(compareBy<RecipeMatch> { it.missing.size }.thenByDescending { it.matched })
        .take(6)

    LaunchedEffect(dataSource, refreshKey) {
        loading = posts.isEmpty()
        while (true) {
            runCatching { dataSource.loadPosts() }
                .onSuccess { posts = it; loadError = false }
                .onFailure { loadError = posts.isEmpty() }
            loading = false
            delay(60_000)
        }
    }

    fun save(next: List<PantryItem>) {
        products = next
        preferences.edit().putString("items", JSONArray().apply {
            next.forEach { item ->
                put(JSONObject().put("name", item.name).put("amount", item.amount).put("unit", item.unit.name))
            }
        }.toString()).apply()
    }

    fun addProduct() {
        val item = try {
            parsePantryInput(input)
        } catch (error: IllegalArgumentException) {
            inputError = error.message
            return
        }
        val existing = products.indexOfFirst {
            it.unit == item.unit && ingredientTokens(it.name) == ingredientTokens(item.name)
        }
        save(if (existing < 0) products + item else products.toMutableList().apply {
            this[existing] = this[existing].copy(amount = this[existing].amount + item.amount)
        })
        input = ""
        inputError = null
    }

    Column(
        modifier = modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Text("Учёт продуктов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; inputError = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Мука-250-г") },
            singleLine = true,
            isError = inputError != null,
            supportingText = { Text(inputError ?: "Продукт-количество-единица: г, кг, мл, л, шт") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addProduct() }),
            trailingIcon = {
                FilledIconButton(onClick = { addProduct() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Добавить продукт")
                }
            },
        )
        if (products.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                products.forEachIndexed { index, item ->
                    InputChip(
                        selected = false,
                        onClick = { save(products.filterIndexed { position, _ -> position != index }) },
                        label = { Text(item.label) },
                        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Удалить ${item.name}") },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Что можно приготовить", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        when {
            products.isEmpty() -> Text("Добавьте продукты, которые есть дома.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            loading && posts.isEmpty() -> CircularProgressIndicator()
            loadError && posts.isEmpty() -> OutlinedButton(onClick = { refreshKey++ }) { Text("Не удалось загрузить рецепты. Повторить") }
            else -> {
                if (ready.isEmpty()) {
                    Text("Пока нет рецепта только из этих продуктов.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ready.forEach { match ->
                        CalculatorRecipeCard(match, onClick = { openedPost = match.post })
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (close.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Если добавить ещё продукты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    close.forEach { match ->
                        CalculatorRecipeCard(match, onClick = { openedPost = match.post })
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (ready.isEmpty() && close.isEmpty()) {
                    Text("Добавьте ещё продукты, чтобы подобрать блюда.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Соль, вода и чёрный перец считаются базовыми. Подбор использует рецепты со списком ингредиентов.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    openedPost?.let { post ->
        FeedRecipeDetailDialog(post = post, dataSource = dataSource, categoryName = null,
            onDismiss = { openedPost = null })
    }
}

@Composable
private fun CalculatorRecipeCard(match: RecipeMatch, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(match.post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(match.post.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Text(
                if (match.canCook) "Все основные ингредиенты есть"
                else "Есть ${match.matched} из ${match.total}. Не хватает: ${match.missing.take(3).joinToString()}",
                style = MaterialTheme.typography.labelMedium,
                color = if (match.canCook) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun parsePantryInput(value: String): PantryItem {
    val lastDash = value.lastIndexOf('-')
    val amountDash = value.lastIndexOf('-', lastDash - 1)
    require(amountDash > 0 && lastDash > amountDash + 1) { "Формат: Мука-250-г" }
    val name = value.substring(0, amountDash).trim()
    val amount = value.substring(amountDash + 1, lastDash).trim().replace(',', '.').toDoubleOrNull()
    val unit = parseUnit(value.substring(lastDash + 1).trim())
    require(name.isNotEmpty() && name.length <= 60 && amount != null && amount.isFinite() && amount > 0 && amount <= 1_000_000) {
        "Укажите продукт и положительное количество"
    }
    require(unit != null) { "Единица: г, кг, мл, л, шт, стакан, ст. л. или ч. л." }
    return PantryItem(name.replaceFirstChar { it.titlecase(Locale.getDefault()) }, amount, unit)
}

private fun parseUnit(value: String): PantryUnit? = when (value.lowercase(Locale.ROOT).replace(".", "").replace(" ", "")) {
    "г", "гр", "грамм", "грамма", "граммов" -> PantryUnit.GRAM
    "кг", "килограмм", "килограмма", "килограммов" -> PantryUnit.KILOGRAM
    "мл", "миллилитр", "миллилитра", "миллилитров" -> PantryUnit.MILLILITER
    "л", "литр", "литра", "литров" -> PantryUnit.LITER
    "шт", "штук", "штука", "штуки" -> PantryUnit.PIECE
    "стакан", "стакана", "стаканов", "чашка", "чашки", "чашек" -> PantryUnit.CUP
    "стл", "столоваяложка", "столовыеложки", "столовыхложек" -> PantryUnit.TABLESPOON
    "чл", "чайнаяложка", "чайныеложки", "чайныхложек" -> PantryUnit.TEASPOON
    "банка", "банки", "банок" -> PantryUnit.CAN
    "пучок", "пучка", "пучков" -> PantryUnit.BUNCH
    "зубчик", "зубчика", "зубчиков" -> PantryUnit.CLOVE
    "щепотка", "щепотки", "щепоток" -> PantryUnit.PINCH
    "фунт", "фунта", "фунтов" -> PantryUnit.POUND
    "унция", "унции", "унций" -> PantryUnit.OUNCE
    else -> null
}

private data class RequiredIngredient(val name: String, val amount: Double?, val unit: PantryUnit)

private val recipeQuantity = Regex("""^(\d+(?:[.,]\d+)?|\d+/\d+|[¼½¾⅓⅔⅛])(?:\s+(\d+/\d+))?\s*(.*)$""")
private val recipeUnits = listOf(
    Regex("""^(столовых ложек|столовые ложки|столовая ложка|ст\.\s*л\.?)\s*""") to PantryUnit.TABLESPOON,
    Regex("""^(чайных ложек|чайные ложки|чайная ложка|ч\.\s*л\.?)\s*""") to PantryUnit.TEASPOON,
    Regex("""^(стаканов|стакана|стакан|чашек|чашки|чашка)\s+""") to PantryUnit.CUP,
    Regex("""^(килограммов|килограмма|килограмм|кг)\s+""") to PantryUnit.KILOGRAM,
    Regex("""^(граммов|грамма|грамм|гр|г)\s+""") to PantryUnit.GRAM,
    Regex("""^(миллилитров|миллилитра|миллилитр|мл)\s+""") to PantryUnit.MILLILITER,
    Regex("""^(литров|литра|литр|л)\s+""") to PantryUnit.LITER,
    Regex("""^(фунтов|фунта|фунт)\s+""") to PantryUnit.POUND,
    Regex("""^(унций|унции|унция)\s+""") to PantryUnit.OUNCE,
    Regex("""^(банок|банки|банка)\s+""") to PantryUnit.CAN,
    Regex("""^(пучков|пучка|пучок)\s+""") to PantryUnit.BUNCH,
    Regex("""^(зубчиков|зубчика|зубчик)\s+""") to PantryUnit.CLOVE,
    Regex("""^(щепоток|щепотки|щепотка)\s+""") to PantryUnit.PINCH,
)

private fun parseFraction(text: String): Double? = when (text) {
    "¼" -> 0.25; "½" -> 0.5; "¾" -> 0.75; "⅓" -> 1.0 / 3; "⅔" -> 2.0 / 3; "⅛" -> 0.125
    else -> if ('/' in text) {
        val parts = text.split('/')
        val denominator = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        if (denominator == 0.0) null else (parts[0].toDoubleOrNull() ?: return null) / denominator
    } else text.replace(',', '.').toDoubleOrNull()
}

private fun recipeIngredients(recipe: String): List<RequiredIngredient> {
    val lines = recipe.lines().map { it.trim() }
    val header = lines.indexOfFirst { it.startsWith("Ингредиенты", ignoreCase = true) }
    if (header < 0) return emptyList()
    return lines.drop(header + 1).takeWhile { it.startsWith('•') || it.startsWith('*') || it.startsWith("- ") }
        .mapNotNull { line ->
            val text = line.drop(1).trim()
            val quantity = recipeQuantity.matchEntire(text)
            val amount = quantity?.let { match ->
                (parseFraction(match.groupValues[1]) ?: return@mapNotNull null) +
                    (match.groupValues[2].takeIf { it.isNotEmpty() }?.let(::parseFraction) ?: 0.0)
            }
            val remaining = (quantity?.groupValues?.get(3) ?: text).trim()
            val measure = recipeUnits.firstNotNullOfOrNull { (pattern, unit) ->
                pattern.find(remaining.lowercase(Locale.ROOT))?.let { match -> unit to remaining.drop(match.value.length).trim() }
            }
            val name = measure?.second ?: remaining.removePrefix("по вкусу ").trim()
            name.takeIf { it.isNotBlank() }?.let { RequiredIngredient(it, amount, measure?.first ?: PantryUnit.PIECE) }
        }
}

private val ignoredWords = setOf(
    "обычн", "свеж", "больш", "маленьк", "мелк", "средн", "нарезан", "измельчен", "очищен",
    "красн", "зелен", "желт", "бел", "черн", "сух", "молот", "терт", "сливочн", "оливков",
    "растительн", "пшеничн", "курин", "вкус", "для", "кусочк", "немног", "горст", "нескольк",
)

internal fun ingredientTokens(name: String): Set<String> = Regex("[а-яёa-z]+", RegexOption.IGNORE_CASE)
    .findAll(name.lowercase(Locale.ROOT).replace('ё', 'е'))
    .map { match ->
        val word = match.value
        when {
            word.startsWith("мук") -> "мук"
            word.startsWith("яйц") || word.startsWith("яиц") || word.startsWith("яич") -> "яйц"
            word.startsWith("сахар") -> "сахар"
            word.startsWith("луков") || word == "лук" || word == "лука" -> "лук"
            word.startsWith("картоф") || word.startsWith("картош") -> "картоф"
            word.startsWith("помидор") || word.startsWith("томат") -> "томат"
            word.startsWith("молок") -> "молок"
            word.startsWith("масл") -> "масл"
            word.startsWith("сол") -> "сол"
            word.startsWith("перц") -> "перц"
            word.startsWith("вод") -> "вод"
            word.startsWith("куриц") || word.startsWith("курин") -> "куриц"
            else -> word.removeSuffix("ами").removeSuffix("ями")
                .removeSuffix("ого").removeSuffix("ыми").removeSuffix("ая")
                .removeSuffix("ое").removeSuffix("ые").removeSuffix("ый")
                .removeSuffix("ий").removeSuffix("ой").removeSuffix("а")
                .removeSuffix("я").removeSuffix("ы").removeSuffix("и")
                .removeSuffix("е").removeSuffix("у").removeSuffix("ю")
                .removeSuffix("о")
        }
    }
    .filter { it.length >= 2 && it !in ignoredWords }
    .toSet()

private fun oilType(name: String): String? =
    listOf("сливоч", "оливк", "растител", "подсолнеч", "кокос", "кунжут")
        .firstOrNull { name.contains(it, ignoreCase = true) }

private fun sameIngredient(first: String, second: String): Boolean {
    val shared = ingredientTokens(first).intersect(ingredientTokens(second))
    if (shared.isEmpty()) return false
    if ("масл" in shared && oilType(first) != oilType(second)) return false
    return true
}

private data class MeasuredAmount(val dimension: String, val value: Double)

private fun measuredAmount(amount: Double, unit: PantryUnit, name: String): MeasuredAmount {
    val tokens = ingredientTokens(name)
    val flour = "мук" in tokens
    val sugar = "сахар" in tokens
    val butter = "масл" in tokens && name.contains("сливоч", ignoreCase = true)
    val salt = "сол" in tokens
    val liquid = tokens.any { it in setOf("молок", "вод", "масл", "сок", "уксус") }
    return when (unit) {
        PantryUnit.GRAM -> MeasuredAmount("г", amount)
        PantryUnit.KILOGRAM -> MeasuredAmount("г", amount * 1000)
        PantryUnit.POUND -> MeasuredAmount("г", amount * 453.6)
        PantryUnit.OUNCE -> MeasuredAmount("г", amount * 28.35)
        PantryUnit.MILLILITER -> MeasuredAmount("мл", amount)
        PantryUnit.LITER -> MeasuredAmount("мл", amount * 1000)
        PantryUnit.CUP -> when {
            flour -> MeasuredAmount("г", amount * 120)
            sugar -> MeasuredAmount("г", amount * 200)
            butter -> MeasuredAmount("г", amount * 225)
            liquid -> MeasuredAmount("мл", amount * 240)
            else -> MeasuredAmount("стакан", amount)
        }
        PantryUnit.TABLESPOON -> when {
            flour -> MeasuredAmount("г", amount * 8)
            sugar -> MeasuredAmount("г", amount * 12.5)
            butter -> MeasuredAmount("г", amount * 14)
            liquid -> MeasuredAmount("мл", amount * 15)
            else -> MeasuredAmount("ст. л.", amount)
        }
        PantryUnit.TEASPOON -> when {
            flour -> MeasuredAmount("г", amount * 3)
            sugar -> MeasuredAmount("г", amount * 4)
            salt -> MeasuredAmount("г", amount * 6)
            liquid -> MeasuredAmount("мл", amount * 5)
            else -> MeasuredAmount("ч. л.", amount)
        }
        else -> MeasuredAmount(unit.label, amount)
    }
}

internal fun findRecipeMatches(posts: List<FeedPost>, products: List<PantryItem>): List<RecipeMatch> {
    if (products.isEmpty()) return emptyList()
    return posts.mapNotNull { post ->
        val requirements = recipeIngredients(post.recipe).filterNot { requirement ->
            val tokens = ingredientTokens(requirement.name)
            val basicPepper = tokens == setOf("перц") &&
                (requirement.name.contains("чёрн", ignoreCase = true) ||
                    requirement.name.contains("черн", ignoreCase = true) ||
                    requirement.name.contains("молот", ignoreCase = true) ||
                    requirement.name.contains("по вкусу", ignoreCase = true))
            tokens.isNotEmpty() && (tokens.all { it in setOf("сол", "вод") } || basicPepper)
        }
        val groups = requirements.filter { ingredientTokens(it.name).isNotEmpty() }
            .groupBy { ingredientTokens(it.name).sorted().joinToString("|") + "#" + oilType(it.name).orEmpty() }.values
        if (groups.isEmpty()) return@mapNotNull null
        val missingGroups = groups.filter { group ->
            val available = products.filter { sameIngredient(it.name, group.first().name) }
                .map { measuredAmount(it.amount, it.unit, it.name) }
            val neededByDimension = group.mapNotNull { requirement ->
                requirement.amount?.let { measuredAmount(it, requirement.unit, requirement.name) }
            }.groupBy { it.dimension }
            available.isEmpty() || neededByDimension.any { (dimension, needed) ->
                available.filter { it.dimension == dimension }.sumOf { it.value } + 0.0001 < needed.sumOf { it.value }
            }
        }
        RecipeMatch(post, groups.size - missingGroups.size, groups.size,
            missingGroups.map { it.first().name })
    }.sortedWith(compareByDescending<RecipeMatch> { it.canCook }.thenBy { it.missing.size }.thenByDescending { it.matched })
}

private fun readPantry(raw: String?): List<PantryItem> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { row ->
            PantryItem(row.getString("name"), row.getDouble("amount"), PantryUnit.valueOf(row.getString("unit")))
        }
    }
}.getOrDefault(emptyList())

private fun formatAmount(amount: Double): String =
    if (amount % 1 == 0.0) amount.toLong().toString() else amount.toString().replace('.', ',')
