package com.example.bolsillologico.domain.usecase

import com.example.bolsillologico.domain.model.FinancialState
import com.example.bolsillologico.domain.model.Priority
import com.example.bolsillologico.domain.model.TransactionType
import kotlinx.datetime.*
import java.util.Locale

data class FinancialHealth(
    val availableBalance: Double,
    val reservedForDebts: Double,
    val dailyLimit: Double,
    val dailyLimitUSD: Double,
    val suggestedAction: String,
    val shouldWarnStock: Boolean = false,
    val essentialRatio: Float = 0f,
    val expenseIncomeRatio: Float = 0f,
    val totalMonthlyExpenses: Double = 0.0,
    val totalMonthlyIncome: Double = 0.0,
    val remainingDays: Int = 1,
    val isBudgetCriticalUSD: Boolean = false
)

class CalculateFinancialStatusUseCase {
    operator fun invoke(state: FinancialState): FinancialHealth {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val daysInMonth = now.month.length(now.year % 4 == 0 && (now.year % 100 != 0 || now.year % 400 == 0))
        val remainingDays = (daysInMonth - now.dayOfMonth).coerceAtLeast(1)

        // 1. Calcular deudas próximas de Cashea (no pagadas)
        val upcomingCashea = state.casheaDebts
            .filter { !it.isPaid }
            .sumOf { it.amountVES }

        // 2. Saldo Real Protegido (Balance - lo que DEBES pagar pronto)
        val protectedBalance = (state.balanceVES - upcomingCashea).coerceAtLeast(0.0)

        // 3. Límite Diario en VES y USD
        val dailyLimit = protectedBalance / remainingDays
        val exchangeRate = if (state.usdExchangeRate > 0) state.usdExchangeRate else 1.0
        val dailyLimitUSD = dailyLimit / exchangeRate
        
        val isBudgetCriticalUSD = dailyLimitUSD < 3.0

        // 4. Análisis de Stock
        val missingHighPriority = state.inventory.any { it.priority == Priority.HIGH && it.quantity <= 0 }

        // 5. Métricas de Gastos e Ingresos
        val monthStart = LocalDateTime(now.year, now.month, 1, 0, 0)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        val monthlyTransactions = state.transactions
            .filter { it.date >= monthStart }
        
        val totalSpent = monthlyTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountVES }
        val totalIncome = monthlyTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountVES }
        val essentialSpent = monthlyTransactions.filter { it.type == TransactionType.EXPENSE && it.isEssential }.sumOf { it.amountVES }
        
        val essentialRatio = if (totalSpent > 0) (essentialSpent / totalSpent).toFloat() else 1f
        
        // Ratio Gasto vs Ingreso (cuánto de lo que entra se va)
        val expenseIncomeRatio = if (totalIncome > 0) (totalSpent / totalIncome).toFloat().coerceIn(0f, 1f) else if (totalSpent > 0) 1f else 0f

        // 6. Sugerencia
        val card = state.creditCards.firstOrNull()
        val availableCredit = card?.let { it.limitVES - it.currentDebtVES } ?: 0.0
        
        val suggestedAction = when {
            isBudgetCriticalUSD -> "¡ALERTA! Tu presupuesto diario es menor a $3 USD. Austeridad total."
            missingHighPriority -> "¡Faltan básicos! Prioriza compra de alta prioridad."
            protectedBalance < (upcomingCashea * 0.5) -> "Saldo bajo. ¡Cuidado con las cuotas de Cashea!"
            else -> "Vas bien. Tienes Bs. ${String.format(Locale.getDefault(), "%.2f", dailyLimit)} ($${String.format(Locale.getDefault(), "%.2f", dailyLimitUSD)}) para hoy."
        }

        return FinancialHealth(
            availableBalance = protectedBalance,
            reservedForDebts = upcomingCashea,
            dailyLimit = dailyLimit,
            dailyLimitUSD = dailyLimitUSD,
            suggestedAction = suggestedAction,
            shouldWarnStock = missingHighPriority,
            essentialRatio = essentialRatio,
            expenseIncomeRatio = expenseIncomeRatio,
            totalMonthlyExpenses = totalSpent,
            totalMonthlyIncome = totalIncome,
            remainingDays = remainingDays,
            isBudgetCriticalUSD = isBudgetCriticalUSD
        )
    }

    private fun Month.length(isLeapYear: Boolean): Int {
        return when (this) {
            Month.FEBRUARY -> if (isLeapYear) 29 else 28
            Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
            else -> 31
        }
    }
}
