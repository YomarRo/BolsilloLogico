package com.example.bolsillologico.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.example.bolsillologico.data.local.FinancialStateSerializer
import com.example.bolsillologico.domain.model.FinancialState
import com.example.bolsillologico.domain.repository.FinancialRepository
import kotlinx.coroutines.flow.Flow

private val Context.dataStore: DataStore<FinancialState> by dataStore(
    fileName = "financial_state.json",
    serializer = FinancialStateSerializer
)

class FinancialRepositoryImpl(private val context: Context) : FinancialRepository {
    override val financialState: Flow<FinancialState> = context.dataStore.data

    override suspend fun updateFinancialState(transform: (FinancialState) -> FinancialState) {
        context.dataStore.updateData { currentState ->
            transform(currentState)
        }
    }
}
