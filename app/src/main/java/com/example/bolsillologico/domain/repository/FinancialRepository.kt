package com.example.bolsillologico.domain.repository

import com.example.bolsillologico.domain.model.FinancialState
import kotlinx.coroutines.flow.Flow

interface FinancialRepository {
    val financialState: Flow<FinancialState>
    suspend fun updateFinancialState(transform: (FinancialState) -> FinancialState)
}
