package com.example.quotationmaker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Maroon = Color(0xFF7A111E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Maroon,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFF6DDE0),
                    onPrimaryContainer = Maroon,
                    secondary = Color(0xFFC8505C)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { QuotationStore(context) }
    var list by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Quotation?>(null) }

    val current = editing
    if (current == null) {
        ListScreen(
            list = list,
            onNew = {
                val last = list.firstOrNull()
                editing = (last ?: Quotation()).copy(
                    id = java.util.UUID.randomUUID().toString(),
                    number = "QT-" + (list.size + 1).toString().padStart(3, '0'),
                    date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    toName = "",
                    toAddress = "",
                    items = listOf(QItem())
                )
            },
            onOpen = { editing = it },
            onDelete = { list = store.delete(it.id) }
        )
    } else {
        EditorScreen(
            initial = current,
            onSave = { list = store.save(it) },
            onBack = { editing = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    list: List<Quotation>,
    onNew: () -> Unit,
    onOpen: (Quotation) -> Unit,
    onDelete: (Quotation) -> Unit
) {
    var toDelete by remember { mutableStateOf<Quotation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quotations", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Maroon, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = Maroon,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New quotation") }
            )
        }
    ) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No quotations yet.\nTap “New quotation” to create one.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { q ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(q) },
                        border = BorderStroke(1.dp, Color(0xFFE5C9CD)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(q.toName.ifBlank { "(No client name)" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${q.number}  •  ${q.date}", color = Color.Gray, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(money(q.total), color = Maroon, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = { toDelete = q }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    toDelete?.let { q ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete quotation?") },
            text = { Text("${q.number} for ${q.toName.ifBlank { "this client" }} will be removed.") },
            confirmButton = { TextButton(onClick = { onDelete(q); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

class ItemState(d: String, r: String, q: String, u: String) {
    var desc by mutableStateOf(d)
    var rate by mutableStateOf(r)
    var qty by mutableStateOf(q)
    var unit by mutableStateOf(u)
    fun toItem() = QItem(desc, rate.toDoubleOrNull() ?: 0.0, qty.toDoubleOrNull() ?: 0.0, unit.trim())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initial: Quotation, onSave: (Quotation) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    var number by remember { mutableStateOf(initial.number) }
    var date by remember { mutableStateOf(initial.date) }
    var fromName by remember { mutableStateOf(initial.fromName) }
    var fromReg by remember { mutableStateOf(initial.fromRegNo) }
    var fromAddr by remember { mutableStateOf(initial.fromAddress) }
    var fromPhone by remember { mutableStateOf(initial.fromPhone) }
    var fromEmail by remember { mutableStateOf(initial.fromEmail) }
    var toName by remember { mutableStateOf(initial.toName) }
    var toAddr by remember { mutableStateOf(initial.toAddress) }
    var gst by remember { mutableStateOf(if (initial.gstPercent == 0.0) "" else fmtNum(initial.gstPercent)) }
    var terms by remember { mutableStateOf(initial.paymentTerms) }
    var bankHolder by remember { mutableStateOf(initial.bankHolder) }
    var bankName by remember { mutableStateOf(initial.bankName) }
    var accNo by remember { mutableStateOf(initial.accountNo) }
    var ifsc by remember { mutableStateOf(initial.ifsc) }
    val items = remember {
        mutableStateListOf<ItemState>().apply {
            initial.items.forEach { add(ItemState(it.description, if (it.rate == 0.0) "" else fmtNum(it.rate), fmtNum(it.qty), it.unit)) }
        }
    }

    fun build() = Quotation(
        id = initial.id, number = number, date = date,
        fromName = fromName, fromRegNo = fromReg, fromAddress = fromAddr, fromPhone = fromPhone, fromEmail = fromEmail,
        toName = toName, toAddress = toAddr,
        items = items.map { it.toItem() },
        gstPercent = gst.toDoubleOrNull() ?: 0.0,
        paymentTerms = terms,
        bankHolder = bankHolder, bankName = bankName, accountNo = accNo, ifsc = ifsc
    )

    BackHandler { onSave(build()); onBack() }

    val preview = build()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (number.isBlank()) "Quotation" else number, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onSave(build()); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Maroon, titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", fontWeight = FontWeight.Bold)
                        Text(money(preview.total), fontWeight = FontWeight.Bold, color = Maroon)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onSave(build()); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                        Button(
                            onClick = {
                                val q = build()
                                onSave(q)
                                sharePdf(context, q)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Maroon)
                        ) { Text("Create PDF & Share") }
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("Quotation") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Quotation No", number, { number = it }, Modifier.weight(1f))
                    Field("Date", date, { date = it }, Modifier.weight(1f))
                }
            }
            Section("From (your business / hospital)") {
                Field("Name", fromName, { fromName = it })
                Field("Reg no", fromReg, { fromReg = it })
                Field("Address", fromAddr, { fromAddr = it }, singleLine = false)
                Field("Mobile number", fromPhone, { fromPhone = it }, keyboard = KeyboardType.Phone)
                Field("Email", fromEmail, { fromEmail = it }, keyboard = KeyboardType.Email)
            }
            Section("To (client)") {
                Field("Client name", toName, { toName = it })
                Field("Client address", toAddr, { toAddr = it }, singleLine = false)
            }
            Section("Items") {
                items.forEachIndexed { i, it ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF3F4)),
                        border = BorderStroke(1.dp, Color(0xFFE5C9CD))
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Item ${i + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (items.size > 1) {
                                    IconButton(onClick = { items.removeAt(i) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove item")
                                    }
                                }
                            }
                            Field("Description (one line per test / service)", it.desc, { v -> it.desc = v }, singleLine = false)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("Rate (₹)", it.rate, { v -> it.rate = v }, Modifier.weight(1f), KeyboardType.Decimal)
                                Field("Qty", it.qty, { v -> it.qty = v }, Modifier.weight(1f), KeyboardType.Decimal)
                                Field("Unit", it.unit, { v -> it.unit = v }, Modifier.weight(1f))
                            }
                            Text("Line total: ${money(it.toItem().total)}", color = Maroon, fontSize = 13.sp)
                        }
                    }
                }
                OutlinedButton(onClick = { items.add(ItemState("", "", "1", items.lastOrNull()?.unit ?: "Person")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add item")
                }
                Field("GST % (leave empty for none)", gst, { gst = it }, keyboard = KeyboardType.Decimal)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal"); Text(money(preview.subtotal))
                }
                if (preview.gstPercent > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GST (${fmtNum(preview.gstPercent)}%)"); Text(money(preview.gstAmount))
                    }
                }
            }
            Section("Payment terms") {
                Field("Terms", terms, { terms = it }, singleLine = false)
            }
            Section("Bank details") {
                Field("Account holder", bankHolder, { bankHolder = it })
                Field("Bank & branch", bankName, { bankName = it })
                Field("Account number", accNo, { accNo = it }, keyboard = KeyboardType.Number)
                Field("IFSC", ifsc, { ifsc = it })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5C9CD))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Maroon)
            content()
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth()
    )
}

fun sharePdf(context: Context, q: Quotation) {
    try {
        val file = PdfGenerator.generate(context, q)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Quotation ${q.number}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share quotation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not create PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
