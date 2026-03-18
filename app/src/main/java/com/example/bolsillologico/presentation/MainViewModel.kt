package com.example.bolsillologico.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bolsillologico.domain.model.*
import com.example.bolsillologico.domain.repository.FinancialRepository
import com.example.bolsillologico.domain.usecase.CalculateFinancialStatusUseCase
import com.example.bolsillologico.domain.usecase.FinancialHealth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    private val repository: FinancialRepository,
    private val calculateFinancialStatusUseCase: CalculateFinancialStatusUseCase = CalculateFinancialStatusUseCase()
) : ViewModel() {

    val financialState: StateFlow<FinancialState> = repository.financialState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialState())

    val financialHealth: StateFlow<FinancialHealth> = financialState
        .map { calculateFinancialStatusUseCase(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), calculateFinancialStatusUseCase(FinancialState()))

    init {
        ensurePredefinedStock()
    }

    private fun ensurePredefinedStock() {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                if (currentState.inventory.isEmpty()) {
                    val basicItems = listOf(
                        "Arroz", "Harina P.A.N", "Aceite", "Pasta", "Azúcar", "Sal", "Café", "Leche", "Huevos", "Granos"
                    ).map { name ->
                        ItemStock(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            quantity = 0.0,
                            unit = if (name == "Aceite") "Lts" else if (name == "Huevos") "Carton" else "Kgs",
                            priority = Priority.HIGH
                        )
                    }
                    currentState.copy(inventory = basicItems)
                } else {
                    currentState
                }
            }
        }
    }

    fun updateBalance(newBalance: Double) {
        viewModelScope.launch {
            repository.updateFinancialState { it.copy(balanceVES = newBalance) }
        }
    }

    fun updateExchangeRate(rate: Double) {
        viewModelScope.launch {
            repository.updateFinancialState { it.copy(usdExchangeRate = rate) }
        }
    }

    fun addTransaction(amount: Double, category: String, type: TransactionType, isEssential: Boolean, paymentMethod: PaymentMethod = PaymentMethod.CASH) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                // Regla del 70% de la TC
                if (type == TransactionType.EXPENSE && paymentMethod == PaymentMethod.CREDIT_CARD) {
                    val card = currentState.creditCards.firstOrNull()
                    if (card != null) {
                        val newDebt = card.currentDebtVES + amount
                        if (newDebt > card.limitVES * 0.7) {
                            return@updateFinancialState currentState
                        }
                    }
                }

                val newTransaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    amountVES = amount,
                    category = category,
                    date = System.currentTimeMillis(),
                    type = type,
                    isEssential = isEssential,
                    paymentMethod = paymentMethod
                )
                
                var newBalance = currentState.balanceVES
                var newCards = currentState.creditCards
                
                if (type == TransactionType.INCOME) {
                    newBalance += amount
                } else {
                    if (paymentMethod == PaymentMethod.CREDIT_CARD) {
                        newCards = currentState.creditCards.map { card ->
                            card.copy(currentDebtVES = card.currentDebtVES + amount)
                        }
                    } else {
                        newBalance -= amount
                    }
                }

                currentState.copy(
                    balanceVES = newBalance,
                    creditCards = newCards,
                    transactions = currentState.transactions + newTransaction
                )
            }
        }
    }

    fun payCreditCard(amount: Double) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                val card = currentState.creditCards.firstOrNull() ?: return@updateFinancialState currentState
                if (currentState.balanceVES < amount) return@updateFinancialState currentState

                val newDebt = (card.currentDebtVES - amount).coerceAtLeast(0.0)
                val newBalance = currentState.balanceVES - amount
                
                val paymentTransaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    amountVES = amount,
                    category = "Pago Tarjeta ${card.name}",
                    date = System.currentTimeMillis(),
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.CASH,
                    isEssential = true
                )

                currentState.copy(
                    balanceVES = newBalance,
                    creditCards = currentState.creditCards.map { it.copy(currentDebtVES = newDebt) },
                    transactions = currentState.transactions + paymentTransaction
                )
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                var newBalance = currentState.balanceVES
                var newCards = currentState.creditCards
                
                if (transaction.type == TransactionType.INCOME) {
                    newBalance -= transaction.amountVES
                } else {
                    if (transaction.paymentMethod == PaymentMethod.CREDIT_CARD) {
                        newCards = currentState.creditCards.map { card ->
                            card.copy(currentDebtVES = (card.currentDebtVES - transaction.amountVES).coerceAtLeast(0.0))
                        }
                    } else {
                        newBalance += transaction.amountVES
                    }
                }
                currentState.copy(
                    balanceVES = newBalance,
                    creditCards = newCards,
                    transactions = currentState.transactions.filter { it.id != transaction.id }
                )
            }
        }
    }

    fun addCasheaDebt(description: String, amount: Double, dueDate: Long) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                val newDebt = CasheaInstallment(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    amountVES = amount,
                    dueDate = dueDate
                )
                currentState.copy(casheaDebts = currentState.casheaDebts + newDebt)
            }
        }
    }

    fun deleteCasheaDebt(debtId: String) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                currentState.copy(casheaDebts = currentState.casheaDebts.filter { it.id != debtId })
            }
        }
    }

    fun toggleDebtPaid(debtId: String) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                val debt = currentState.casheaDebts.find { it.id == debtId } ?: return@updateFinancialState currentState
                val willBePaid = !debt.isPaid
                
                var newBalance = currentState.balanceVES
                val newTransactions = currentState.transactions.toMutableList()
                
                if (willBePaid) {
                    newBalance -= debt.amountVES
                    newTransactions.add(
                        Transaction(
                            id = UUID.randomUUID().toString(),
                            amountVES = debt.amountVES,
                            category = "Pago Deuda: ${debt.description}",
                            date = System.currentTimeMillis(),
                            type = TransactionType.EXPENSE,
                            paymentMethod = PaymentMethod.CASH,
                            isEssential = true
                        )
                    )
                } else {
                    newBalance += debt.amountVES
                    // Opcionalmente eliminar la transacción de pago si se desmarca
                    val lastPaymentTx = newTransactions.findLast { it.category == "Pago Deuda: ${debt.description}" }
                    if (lastPaymentTx != null) newTransactions.remove(lastPaymentTx)
                }

                currentState.copy(
                    balanceVES = newBalance,
                    transactions = newTransactions,
                    casheaDebts = currentState.casheaDebts.map {
                        if (it.id == debtId) it.copy(isPaid = willBePaid) else it
                    }
                )
            }
        }
    }

    fun updateCreditCard(name: String, limit: Double, debt: Double, closingDate: Int, dueDate: Int) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                val card = CreditCard(
                    id = "main_card",
                    name = name,
                    limitVES = limit,
                    currentDebtVES = debt,
                    closingDate = closingDate,
                    dueDate = dueDate
                )
                currentState.copy(creditCards = listOf(card))
            }
        }
    }

    fun updateStockItem(name: String, quantity: Double, priority: Priority, unit: String) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                val existingItemIndex = currentState.inventory.indexOfFirst { it.name.equals(name, true) }
                val newList = currentState.inventory.toMutableList()
                
                if (existingItemIndex != -1) {
                    newList[existingItemIndex] = newList[existingItemIndex].copy(quantity = quantity, priority = priority, unit = unit)
                } else {
                    newList.add(ItemStock(id = UUID.randomUUID().toString(), name = name, quantity = quantity, unit = unit, priority = priority))
                }
                
                currentState.copy(inventory = newList)
            }
        }
    }

    fun adjustStockQuantity(itemId: String, delta: Double) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                val newList = currentState.inventory.map { item ->
                    if (item.id == itemId) {
                        item.copy(quantity = (item.quantity + delta).coerceAtLeast(0.0))
                    } else {
                        item
                    }
                }
                currentState.copy(inventory = newList)
            }
        }
    }

    fun deleteStockItem(itemId: String) {
        viewModelScope.launch {
            repository.updateFinancialState { currentState ->
                currentState.copy(inventory = currentState.inventory.filter { it.id != itemId })
            }
        }
    }
}
