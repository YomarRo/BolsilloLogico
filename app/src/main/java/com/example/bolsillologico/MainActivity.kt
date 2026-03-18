package com.example.bolsillologico

import android.app.DatePickerDialog
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bolsillologico.data.repository.FinancialRepositoryImpl
import com.example.bolsillologico.domain.model.*
import com.example.bolsillologico.presentation.MainViewModel
import com.example.bolsillologico.presentation.ViewModelFactory
import com.example.bolsillologico.ui.theme.BolsilloLogicoTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repository = FinancialRepositoryImpl(applicationContext)
            val viewModel: MainViewModel = viewModel(factory = ViewModelFactory(repository))
            
            BolsilloLogicoTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.financialState.collectAsState()
    val health by viewModel.financialHealth.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val dashboardState = rememberLazyListState()
    val inventoryState = rememberLazyListState()
    val debtsState = rememberLazyListState()

    val isFabVisible by remember {
        derivedStateOf {
            val listState = when(selectedTab) {
                0 -> dashboardState
                1 -> inventoryState
                2 -> debtsState
                else -> null
            }
            // El botón se muestra si:
            // 1. No hay lista (ej. pestaña Configuración)
            // 2. Todavía puedes seguir bajando (hay más elementos abajo)
            // 3. O si la lista es corta (no permite hacer scroll hacia arriba porque ya se ve todo)
            listState == null || listState.canScrollForward || !listState.canScrollBackward
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Resumen") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("Stock") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Deudas") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Config") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab < 3) {
                AnimatedVisibility(
                    visible = isFabVisible,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> Dashboard(state, health, viewModel, dashboardState)
                1 -> InventoryScreen(state, viewModel, inventoryState)
                2 -> DebtsScreen(state, viewModel, debtsState)
                3 -> SettingsScreen(state, viewModel)
            }
        }

        if (showAddDialog) {
            AddGenericDialog(
                tabIndex = selectedTab,
                inventory = state.inventory,
                onDismiss = { showAddDialog = false },
                onAddTransaction = { amt, cat, type, ess, method, stockId -> 
                    viewModel.addTransaction(amt, cat, type, ess, method)
                    stockId?.let { viewModel.adjustStockQuantity(it, 1.0) }
                },
                onAddStock = { name, qty, prio, unit -> viewModel.updateStockItem(name, qty, prio, unit) },
                onAddDebt = { desc, amt, date -> viewModel.addCasheaDebt(desc, amt, date) }
            )
        }
    }
}

@Composable
fun Dashboard(
    state: FinancialState, 
    health: com.example.bolsillologico.domain.usecase.FinancialHealth,
    viewModel: MainViewModel,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            FinancialSummaryCard(health, state.balanceVES)
        }
        
        item {
            MonthlyMetricsCard(health)
        }

        // Aviso de deudas venciendo hoy
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val todayEnd = todayStart + (24 * 60 * 60 * 1000)

        val debtsDueToday = state.casheaDebts.filter { !it.isPaid && it.dueDate in todayStart until todayEnd }
        if (debtsDueToday.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "¡ALERTA! Tienes ${debtsDueToday.size} deuda(s) por pagar hoy. Priorízalas.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBF360C)
                        )
                    }
                }
            }
        }

        state.creditCards.firstOrNull()?.let { card ->
            val daysUntilClosing = getDaysUntilDay(card.closingDate)
            
            if (daysUntilClosing == -1 || (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == card.closingDate + 1)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("¡Ciclo reiniciado! Ideal para compras grandes hoy.", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (daysUntilClosing in 1..3) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Tarjeta ${card.name} cierra en $daysUntilClosing días.", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            if (card.currentDebtVES >= card.limitVES * 0.7) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("¡Límite de TDC al 70%! Bloqueada.", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (health.isBudgetCriticalUSD) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("PRESUPUESTO CRÍTICO < $3 USD/día", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (health.shouldWarnStock) {
            item { StockWarningCard() }
        }
        
        item { ActionSuggestionCard(health.suggestedAction) }
        
        item { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORIAL DE OPERACIONES", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            }
        }
        
        items(state.transactions.reversed()) { transaction ->
            TransactionItem(transaction, onDelete = { viewModel.deleteTransaction(transaction) })
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

fun getDaysUntilDay(targetDay: Int): Int {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    return if (targetDay >= today) {
        targetDay - today
    } else {
        (maxDays - today) + targetDay
    }
}

@Composable
fun MonthlyMetricsCard(health: com.example.bolsillologico.domain.usecase.FinancialHealth) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularIndicator(
                    ratio = health.essentialRatio,
                    label = "VITAL",
                    color = MaterialTheme.colorScheme.primary,
                    size = 72.dp
                )
                
                CircularIndicator(
                    ratio = health.expenseIncomeRatio,
                    label = "GASTO",
                    color = Color(0xFFE53935),
                    size = 72.dp
                )
                
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RENDIMIENTO", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    Text(
                        text = "Entra: Bs. ${String.format(Locale.getDefault(), "%.2f", health.totalMonthlyIncome)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        maxLines = 1
                    )
                    Text(
                        text = "Sale: Bs. ${String.format(Locale.getDefault(), "%.2f", health.totalMonthlyExpenses)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                        maxLines = 1
                    )
                }
            }
            
            Surface(
                color = if(health.expenseIncomeRatio > 0.9f) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if(health.expenseIncomeRatio > 0.9f) "⚠ ¡Cuidado! Casi no hay margen de ahorro." 
                           else if (health.essentialRatio > 0.7f) "✔ Buen control de gastos vitales."
                           else "ℹ Reduce gastos opcionales.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if(health.expenseIncomeRatio > 0.9f) MaterialTheme.colorScheme.error else Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun CircularIndicator(ratio: Float, label: String, color: Color, size: androidx.compose.ui.unit.Dp = 72.dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        val backgroundColor = color.copy(alpha = 0.2f)
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = backgroundColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(ratio * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InventoryScreen(state: FinancialState, viewModel: MainViewModel, listState: LazyListState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { 
            Text("Inventario de Stock", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Controla tus suministros básicos", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(state.inventory) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if(item.priority == Priority.HIGH) "Prioridad: ALTA" else "Prioridad: BAJA", 
                            fontSize = 11.sp, 
                            color = if(item.priority == Priority.HIGH) Color.Red else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.adjustStockQuantity(item.id, -1.0) }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Restar", tint = MaterialTheme.colorScheme.error)
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${item.quantity.toInt()}", 
                                fontWeight = FontWeight.Black, 
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(item.unit, fontSize = 10.sp, color = Color.Gray)
                        }

                        IconButton(onClick = { viewModel.adjustStockQuantity(item.id, 1.0) }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Sumar", tint = Color(0xFF4CAF50))
                        }
                    }
                    
                    IconButton(onClick = { viewModel.deleteStockItem(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun DebtsScreen(state: FinancialState, viewModel: MainViewModel, listState: LazyListState) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Deudas y Cashea", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(state.casheaDebts) { debt ->
            Card(
                modifier = Modifier.fillMaxWidth(), 
                colors = CardDefaults.cardColors(containerColor = if(debt.isPaid) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFFF3E0))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = debt.isPaid, onCheckedChange = { viewModel.toggleDebtPaid(debt.id) })
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            debt.description, 
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (debt.isPaid) TextDecoration.LineThrough else null,
                            color = if (debt.isPaid) Color.Gray else Color.Unspecified
                        )
                        Text("Vence: ${dateFormat.format(Date(debt.dueDate))}", fontSize = 12.sp)
                    }
                    Text(
                        "Bs. ${debt.amountVES}", 
                        fontWeight = FontWeight.Black,
                        textDecoration = if (debt.isPaid) TextDecoration.LineThrough else null,
                        color = if (debt.isPaid) Color.Gray else Color.Unspecified
                    )
                    IconButton(onClick = { viewModel.deleteCasheaDebt(debt.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun SettingsScreen(state: FinancialState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var balance by remember { mutableStateOf(state.balanceVES.toString()) }
    var usdRate by remember { mutableStateOf(state.usdExchangeRate.toString()) }
    
    val card = state.creditCards.firstOrNull()
    var tcName by remember { mutableStateOf(card?.name ?: "") }
    var tcLimit by remember { mutableStateOf(card?.limitVES?.toString() ?: "") }
    var tcDebt by remember { mutableStateOf(card?.currentDebtVES?.toString() ?: "") }
    var tcClosing by remember { mutableStateOf(card?.closingDate?.toString() ?: "15") }
    var tcDue by remember { mutableStateOf(card?.dueDate?.toString() ?: "30") }
    
    var payAmount by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("AJUSTES GLOBALES", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    Text("Personalización", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Moneda y Saldo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = usdRate, onValueChange = { usdRate = it }, label = { Text("Tasa USD (Bs.)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = balance, onValueChange = { balance = it }, label = { Text("Saldo en Cuenta (Bs.)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { 
                        viewModel.updateExchangeRate(usdRate.toDoubleOrNull() ?: 1.0)
                        viewModel.updateBalance(balance.toDoubleOrNull() ?: 0.0)
                        focusManager.clearFocus()
                        Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.align(Alignment.End)) { Text("Aplicar") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tarjeta de Crédito", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = tcName, onValueChange = { tcName = it }, label = { Text("Banco") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tcLimit, onValueChange = { tcLimit = it }, label = { Text("Límite") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tcDebt, onValueChange = { tcDebt = it }, label = { Text("Deuda") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = tcClosing, onValueChange = { tcClosing = it }, label = { Text("Corte") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = tcDue, onValueChange = { tcDue = it }, label = { Text("Pago") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Pagar Deuda", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = payAmount, onValueChange = { payAmount = it }, label = { Text("Bs.") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Button(onClick = { 
                            viewModel.payCreditCard(payAmount.toDoubleOrNull() ?: 0.0)
                            payAmount = ""
                        }, modifier = Modifier.padding(start = 8.dp)) { Text("Pagar") }
                    }

                    Button(onClick = { 
                        viewModel.updateCreditCard(tcName, tcLimit.toDoubleOrNull() ?: 0.0, tcDebt.toDoubleOrNull() ?: 0.0, tcClosing.toIntOrNull() ?: 15, tcDue.toIntOrNull() ?: 30)
                        focusManager.clearFocus()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar TDC") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reportes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Genera un documento con tus movimientos del mes actual.", fontSize = 13.sp)
                    Button(onClick = { generatePDF(context, state) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar PDF Mensual")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

fun generatePDF(context: android.content.Context, state: FinancialState) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas: Canvas = page.canvas
    val paint = Paint()
    
    var y = 40f
    paint.textSize = 14f
    paint.isFakeBoldText = true
    canvas.drawText("REPORTE MENSUAL - BOLSILLO LÓGICO", 20f, y, paint)
    
    y += 30f
    paint.textSize = 10f
    paint.isFakeBoldText = false
    canvas.drawText("Saldo Actual: Bs. ${state.balanceVES}", 20f, y, paint)
    
    y += 20f
    canvas.drawText("Tasa USD: ${state.usdExchangeRate}", 20f, y, paint)
    
    y += 30f
    paint.isFakeBoldText = true
    canvas.drawText("ÚLTIMOS MOVIMIENTOS:", 20f, y, paint)
    
    y += 20f
    paint.isFakeBoldText = false
    state.transactions.takeLast(15).reversed().forEach { tx ->
        val type = if(tx.type == TransactionType.INCOME) "+" else "-"
        canvas.drawText("${tx.category}: $type Bs. ${tx.amountVES}", 20f, y, paint)
        y += 15f
        if (y > 580f) return@forEach
    }
    
    pdfDocument.finishPage(page)
    
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    val file = File(directory, "Reporte_Financiero_${System.currentTimeMillis()}.pdf")
    
    try {
        pdfDocument.writeTo(FileOutputStream(file))
        Toast.makeText(context, "PDF Guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al crear PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        pdfDocument.close()
    }
}

val expenseCategories = listOf(
    "🛒 Mercado" to true,
    "⚡ Servicios" to true,
    "💊 Salud" to true,
    "🚌 Transporte" to true,
    "🏠 Hogar" to true,
    "🎓 Educación" to true,
    "🍔 Comida Afuera" to false,
    "🎬 Entretenimiento" to false,
    "🛍️ Compras" to false,
    "🎁 Regalos" to false,
    "🎲 Otros" to false
)

val incomeCategories = listOf(
    "💰 Sueldo",
    "🚀 Freelance",
    "📦 Ventas",
    "💸 Bonos",
    "🏠 Rentas",
    "🧧 Otros"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGenericDialog(
    tabIndex: Int,
    inventory: List<ItemStock>,
    onDismiss: () -> Unit,
    onAddTransaction: (Double, String, TransactionType, Boolean, PaymentMethod, String?) -> Unit,
    onAddStock: (String, Double, Priority, String) -> Unit,
    onAddDebt: (String, Double, Long) -> Unit
) {
    var field1 by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var field3 by remember { mutableStateOf("uds") }
    var bool1 by remember { mutableStateOf(true) } // Is Expense
    var bool2 by remember { mutableStateOf(true) } // Is Essential
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var linkedStockItemId by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val calendar = Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when(tabIndex) { 0 -> "Nueva Operación"; 1 -> "Stock"; else -> "Deuda" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = field1, onValueChange = { field1 = it }, label = { Text(when(tabIndex) { 0 -> "Monto"; 1 -> "Artículo"; else -> "Descripción" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = if(tabIndex != 1) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default)
                
                if (tabIndex == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = bool1, onClick = { bool1 = true; selectedCategory = "" })
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Text("Gasto")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = !bool1, onClick = { bool1 = false; selectedCategory = "" })
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Text("Ingreso")
                    }
                    
                    Text("Categoría:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cats = if (bool1) expenseCategories.map { it.first } else incomeCategories
                        items(cats) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { 
                                    selectedCategory = cat
                                    if (bool1) {
                                        val isEssential = expenseCategories.find { it.first == cat }?.second ?: false
                                        bool2 = isEssential
                                    }
                                },
                                label = { Text(cat) }
                            )
                        }
                    }

                    if (bool1 && selectedCategory.contains("Mercado")) {
                        Text("¿Es un producto de Stock?", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(inventory) { item ->
                                FilterChip(
                                    selected = linkedStockItemId == item.id,
                                    onClick = { linkedStockItemId = if(linkedStockItemId == item.id) null else item.id },
                                    label = { Text(item.name) }
                                )
                            }
                        }
                    }

                    if (bool1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = paymentMethod == PaymentMethod.CASH, onClick = { paymentMethod = PaymentMethod.CASH }); Text("Efectivo")
                            Spacer(modifier = Modifier.width(12.dp))
                            RadioButton(selected = paymentMethod == PaymentMethod.CREDIT_CARD, onClick = { paymentMethod = PaymentMethod.CREDIT_CARD }); Text("TDC")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bool2, onCheckedChange = { bool2 = it })
                            Text("¿Es un gasto Vital / Esencial?")
                        }
                    }
                } else if (tabIndex == 1) {
                    var qtyField by remember { mutableStateOf("") }
                    OutlinedTextField(value = qtyField, onValueChange = { qtyField = it }, label = { Text("Cantidad") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    selectedCategory = qtyField // Reusando para cantidad en stock
                    OutlinedTextField(value = field3, onValueChange = { field3 = it }, label = { Text("Unidad") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bool2, onCheckedChange = { bool2 = it })
                        Text("Alta Prioridad")
                    }
                } else {
                    var amtField by remember { mutableStateOf("") }
                    OutlinedTextField(value = amtField, onValueChange = { amtField = it }, label = { Text("Monto") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    selectedCategory = amtField // Reusando para monto en deudas
                    
                    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDate))
                    Button(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); selectedDate = calendar.timeInMillis }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Fecha Vencimiento: $dateStr") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when(tabIndex) {
                    0 -> onAddTransaction(field1.toDoubleOrNull() ?: 0.0, selectedCategory, if(bool1) TransactionType.EXPENSE else TransactionType.INCOME, bool2, paymentMethod, linkedStockItemId)
                    1 -> onAddStock(field1, selectedCategory.toDoubleOrNull() ?: 0.0, if(bool2) Priority.HIGH else Priority.LOW, field3)
                    2 -> onAddDebt(field1, selectedCategory.toDoubleOrNull() ?: 0.0, selectedDate)
                }
                onDismiss()
            }) { Text("Confirmar") }
        }
    )
}

@Composable
fun FinancialSummaryCard(health: com.example.bolsillologico.domain.usecase.FinancialHealth, totalBalance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (health.isBudgetCriticalUSD) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DISPONIBLE POR DÍA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                Text("Faltan ${health.remainingDays} días", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Bs. ${String.format(Locale.getDefault(), "%.2f", health.dailyLimit)}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("($${String.format(Locale.getDefault(), "%.2f", health.dailyLimitUSD)})", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("SALDO TOTAL", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    Text("Bs. ${String.format(Locale.getDefault(), "%.2f", totalBalance)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("RESERVADO DEUDAS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    Text("Bs. ${String.format(Locale.getDefault(), "%.2f", health.reservedForDebts)}", fontWeight = FontWeight.Bold, color = Color.Yellow)
                }
            }
        }
    }
}

@Composable
fun StockWarningCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Stock crítico de artículos vitales.", fontSize = 13.sp, color = Color.Red)
        }
    }
}

@Composable
fun ActionSuggestionCard(suggestion: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(suggestion, fontSize = 14.sp)
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, onDelete: () -> Unit) {
    val isTdcExpense = transaction.type == TransactionType.EXPENSE && transaction.paymentMethod == PaymentMethod.CREDIT_CARD
    val color = when {
        transaction.type == TransactionType.INCOME -> Color(0xFF4CAF50)
        isTdcExpense -> Color(0xFFFFB300) // Amarillo/Ambar para visibilidad en fondo claro
        else -> MaterialTheme.colorScheme.error
    }
    
    val prefix = if (transaction.type == TransactionType.INCOME) "+" else "-"
    val method = if (transaction.paymentMethod == PaymentMethod.CREDIT_CARD) " (TDC)" else ""
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(transaction.date))
    
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.category + method, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.type == TransactionType.EXPENSE) {
                        Text(if (transaction.isEssential) "Vital" else "Opcional", fontSize = 11.sp, color = Color.Gray)
                        Text(" • ", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(dateStr, fontSize = 11.sp, color = Color.Gray)
                }
            }
            Text("$prefix Bs. ${transaction.amountVES}", color = color, fontWeight = FontWeight.ExtraBold)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(18.dp)) }
        }
    }
}
