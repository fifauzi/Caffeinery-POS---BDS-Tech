package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an operational cash-out expense paid from the cash drawer during an active shift
 * (e.g. buying plastic bags, mineral water, ice, delivery fees).
 */
@Entity(tableName = "cash_expenses")
data class CashExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long = 0,
    val shiftCode: String,
    val staffName: String,
    val description: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis()
)
