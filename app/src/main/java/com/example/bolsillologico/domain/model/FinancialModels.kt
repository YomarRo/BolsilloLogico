package com.example.bolsillologico.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FinancialState(
    val balanceVES: Double = 0.0,
    val dailyLimit: Double = 0.0,
    val usdExchangeRate: Double = 1.0,
    val casheaDebts: List<CasheaInstallment> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val inventory: List<ItemStock> = emptyList(),
    val transactions: List<Transaction> = emptyList()
)

@Serializable
data class CasheaInstallment(
    val id: String,
    val description: String,
    val amountVES: Double,
    val dueDate: Long,
    val isPaid: Boolean = false
)

@Serializable
data class CreditCard(
    val id: String,
    val name: String,
    val limitVES: Double,
    val currentDebtVES: Double,
    val closingDate: Int, // Día del mes (1-31)
    val dueDate: Int      // Día del mes (1-31)
)

@Serializable
data class ItemStock(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val priority: Priority = Priority.MEDIUM
)

enum class Priority { HIGH, MEDIUM, LOW }

@Serializable
data class Transaction(
    val id: String,
    val amountVES: Double,
    val category: String,
    val date: Long,
    val type: TransactionType,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isEssential: Boolean = true
)

enum class TransactionType { INCOME, EXPENSE }
enum class PaymentMethod { CASH, CREDIT_CARD }
