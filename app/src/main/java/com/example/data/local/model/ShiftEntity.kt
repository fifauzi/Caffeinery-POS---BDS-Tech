package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a cashier work shift session and petty cash reconciliation.
 * Tracks initial petty cash (kas kecil), cash/non-cash sales, physical cash count,
 * and reconciliation difference.
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftCode: String,
    val staffId: String,
    val staffName: String,
    val staffRole: String, // Owner, Manager, Kasir, Barista
    val shiftName: String, // Shift 1, Shift 2, Event
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val startingCash: Double = 0.0, // Modal awal kas kecil (Petty Cash)
    val totalCashSales: Double = 0.0,
    val totalNonCashSales: Double = 0.0,
    val totalCashExpenses: Double = 0.0,
    val totalTransactions: Int = 0,
    val expectedCash: Double = startingCash,
    val actualCash: Double? = null,
    val cashDifference: Double? = null,
    val notes: String = "",
    val status: String = "OPEN" // "OPEN" or "CLOSED"
)
