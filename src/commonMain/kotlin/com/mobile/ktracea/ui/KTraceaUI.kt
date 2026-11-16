package com.mobile.ktracea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.ktracea.KTracea
import com.mobile.ktracea.export.CurlExporter
import com.mobile.ktracea.export.HarExporter
import com.mobile.ktracea.mock.MockEngine
import com.mobile.ktracea.model.HttpTransaction
import com.mobile.ktracea.model.MockRule
import kotlinx.coroutines.launch

enum class Screen {
    TRANSACTIONS,
    DETAIL,
    MOCKS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KTraceaScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(Screen.TRANSACTIONS) }
    var selectedTransaction by remember { mutableStateOf<HttpTransaction?>(null) }
    val transactions by KTracea.repository.transactions.collectAsState()
    val mockRules by MockEngine.rules.collectAsState()
    val scope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                Screen.TRANSACTIONS -> "KTracea Inspector (${transactions.size})"
                                Screen.DETAIL -> selectedTransaction?.path ?: "Details"
                                Screen.MOCKS -> "Mock Rules (${mockRules.size})"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (currentScreen != Screen.TRANSACTIONS) {
                            TextButton(onClick = { currentScreen = Screen.TRANSACTIONS }) {
                                Text("Back", color = MaterialTheme.colorScheme.secondary)
                            }
                        } else if (onClose != null) {
                            TextButton(onClick = onClose) {
                                Text("Close", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == Screen.TRANSACTIONS) {
                            TextButton(onClick = { currentScreen = Screen.MOCKS }) {
                                Text("Mocks", color = Color(0xFFFFB74D))
                            }
                            TextButton(onClick = { KTracea.clearAll() }) {
                                Text("Clear", color = Color(0xFFEF5350))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            modifier = modifier
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (currentScreen) {
                    Screen.TRANSACTIONS -> TransactionListScreen(
                        transactions = transactions,
                        onSelect = { tx ->
                            selectedTransaction = tx
                            currentScreen = Screen.DETAIL
                        }
                    )
                    Screen.DETAIL -> selectedTransaction?.let { tx ->
                        TransactionDetailScreen(
                            transaction = tx,
                            onCreateMock = { rule ->
                                scope.launch {
                                    MockEngine.addRule(rule)
                                    currentScreen = Screen.MOCKS
                                }
                            }
                        )
                    }
                    Screen.MOCKS -> MockRulesScreen(
                        rules = mockRules,
                        onToggle = { id -> scope.launch { MockEngine.toggleRule(id) } },
                        onDelete = { id -> scope.launch { MockEngine.removeRule(id) } },
                        onAdd = { rule -> scope.launch { MockEngine.addRule(rule) } }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionListScreen(
    transactions: List<HttpTransaction>,
    onSelect: (HttpTransaction) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }

    val filtered = remember(transactions, searchQuery, filterStatus) {
        transactions.filter { tx ->
            val matchQuery = searchQuery.isEmpty() ||
                    tx.url.contains(searchQuery, ignoreCase = true) ||
                    tx.method.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (filterStatus) {
                "2xx" -> tx.responseCode in 200..299
                "4xx" -> tx.responseCode in 400..499
                "5xx" -> tx.responseCode in 500..599
                "MOCK" -> tx.isMocked
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by URL or method...", fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "2xx", "4xx", "5xx", "MOCK").forEach { filter ->
                FilterChip(
                    selected = filterStatus == filter,
                    onClick = { filterStatus = filter },
                    label = { Text(filter, fontSize = 12.sp) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (transactions.isEmpty()) "No network traffic captured yet" else "No matching transactions",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    TransactionItem(tx = tx, onClick = { onSelect(tx) })
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    tx: HttpTransaction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Method Badge
                Text(
                    text = tx.method,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when (tx.method.uppercase()) {
                        "GET" -> Color(0xFF64B5F6)
                        "POST" -> Color(0xFF81C784)
                        "PUT" -> Color(0xFFFFB74D)
                        "DELETE" -> Color(0xFFE57373)
                        else -> Color.White
                    }
                )

                // Status Code
                val statusCodeText = tx.responseCode?.toString() ?: (if (tx.error != null) "ERR" else "...")
                val statusColor = when (tx.responseCode) {
                    in 200..299 -> Color(0xFF81C784)
                    in 400..499 -> Color(0xFFFFB74D)
                    in 500..599 -> Color(0xFFE57373)
                    else -> if (tx.error != null) Color(0xFFE57373) else Color.Gray
                }
                Text(
                    text = statusCodeText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = statusColor
                )

                if (tx.isMocked) {
                    Surface(
                        color = Color(0xFF512DA8),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "MOCK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                tx.durationMs?.let {
                    Text(text = "${it}ms", fontSize = 11.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tx.url,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
        }
    }
}

@Composable
fun TransactionDetailScreen(
    transaction: HttpTransaction,
    onCreateMock: (MockRule) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Request", "Response")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                0 -> OverviewTab(transaction, onCreateMock)
                1 -> HeadersBodyTab(transaction.requestHeaders, transaction.requestBody)
                2 -> HeadersBodyTab(transaction.responseHeaders, transaction.responseBody)
            }
        }
    }
}

@Composable
fun OverviewTab(
    tx: HttpTransaction,
    onCreateMock: (MockRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow("URL", tx.url)
        DetailRow("Method", tx.method)
        DetailRow("Status Code", tx.responseCode?.toString() ?: "N/A")
        DetailRow("Duration", "${tx.durationMs ?: 0} ms")
        DetailRow("Timestamp", tx.timestamp.toString())
        tx.error?.let { DetailRow("Error", it, valueColor = Color(0xFFE57373)) }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val rule = MockRule(
                    id = "${tx.timestamp}_mock",
                    urlPattern = tx.path,
                    method = tx.method,
                    responseCode = tx.responseCode ?: 200,
                    responseBody = tx.responseBody ?: "{}"
                )
                onCreateMock(rule)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Mock Rule From This Request")
        }

        OutlinedButton(
            onClick = {
                // cURL copy helper
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export as cURL")
        }
    }
}

@Composable
fun HeadersBodyTab(
    headers: List<com.mobile.ktracea.model.HttpHeader>,
    body: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Headers (${headers.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (headers.isEmpty()) {
            Text("No headers", color = Color.Gray, fontSize = 13.sp)
        } else {
            headers.forEach { header ->
                Row {
                    Text("${header.name}: ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF90CAF9))
                    Text(header.value, fontSize = 12.sp, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Body", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (body.isNullOrEmpty()) {
            Text("(Empty body)", color = Color.Gray, fontSize = 13.sp)
        } else {
            Surface(
                color = Color(0xFF262626),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = body,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MockRulesScreen(
    rules: List<MockRule>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (MockRule) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add Mock Rule", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No mock rules configured", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${rule.method} ${rule.urlPattern}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Status: ${rule.responseCode} | Delay: ${rule.delayMs}ms", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = rule.isEnabled, onCheckedChange = { onToggle(rule.id) })
                            IconButton(onClick = { onDelete(rule.id) }) {
                                Text("🗑", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var pattern by remember { mutableStateOf("") }
        var method by remember { mutableStateOf("GET") }
        var status by remember { mutableStateOf("200") }
        var body by remember { mutableStateOf("{\"status\":\"mocked\"}") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Mock Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("URL Pattern (path/regex)") })
                    OutlinedTextField(value = method, onValueChange = { method = it }, label = { Text("Method (GET, POST, *)") })
                    OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Status Code") })
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Response JSON Body") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pattern.isNotBlank()) {
                        val newRule = MockRule(
                            id = "${(0..999999).random()}",
                            urlPattern = pattern,
                            method = method,
                            responseCode = status.toIntOrNull() ?: 200,
                            responseBody = body
                        )
                        onAdd(newRule)
                        showAddDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
