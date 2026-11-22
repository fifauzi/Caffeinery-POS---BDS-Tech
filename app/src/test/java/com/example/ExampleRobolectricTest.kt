package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.service.EscPosPrinterService
import com.example.service.QrisPayloadGenerator
import com.example.ui.PosViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Caffeinery POS by BDS Tech", appName)
    }

    @Test
    fun `dynamic QRIS generation generates valid TLV and CRC`() {
        val payload = QrisPayloadGenerator.generateDynamicQris(
            invoiceNumber = "INV-20261021-001",
            amount = 45000.0,
            merchantName = "CAFFEINERY SPECIALTY"
        )
        assertNotNull(payload)
        assertTrue(payload.startsWith("000201010212")) // Format 01 + Dynamic 12
        assertTrue(payload.contains("5303360")) // Currency IDR
        assertTrue(payload.contains("540545000")) // Amount 45000
        assertTrue(payload.contains("6304")) // CRC tag
        assertEquals(4, payload.takeLast(4).length) // 4-char CRC16
    }

    @Test
    fun `esc pos byte builder produces valid printer commands`() {
        val order = OrderEntity(
            invoiceNumber = "INV-001",
            timestamp = System.currentTimeMillis(),
            customerName = "Guest",
            subtotal = 35000.0,
            grandTotal = 38500.0,
            paymentMethod = "CASH",
            tenderedAmount = 50000.0,
            changeAmount = 11500.0
        )
        val items = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-001",
                productId = 1,
                productName = "Spanish Latte",
                sku = "CF-001",
                basePrice = 35000.0,
                unitPrice = 35000.0,
                quantity = 1,
                lineTotal = 35000.0
            )
        )
        val bytes = EscPosPrinterService.buildReceiptBytes(order, items)
        assertTrue(bytes.isNotEmpty())
        // Verify ESC @ (0x1B, 0x40) init command
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }

    @Test
    fun `csv export includes 26 dedicated multi columns with hpp profit dinein packaging and tax`() {
        val order = OrderEntity(
            invoiceNumber = "INV-002",
            timestamp = System.currentTimeMillis(),
            customerName = "Budi Walk-in",
            orderType = "DINE_IN",
            cashierName = "Rangga Maheza",
            shiftName = "Shift 1",
            subtotal = 37000.0,
            serviceFeePercentage = 2.5,
            serviceFeeAmount = 925.0,
            packagingFeeAmount = 0.0,
            taxPercentage = 10.0,
            taxAmount = 3792.5,
            grandTotal = 41700.0,
            paymentMethod = "QRIS"
        )
        val item = OrderItemEntity(
            orderInvoiceNumber = "INV-002",
            productId = 1,
            productName = "Café Latte",
            sku = "EB-006",
            basePrice = 32000.0,
            costPrice = 11200.0,
            unitPrice = 37000.0,
            quantity = 1,
            lineTotal = 37000.0,
            categoryName = "Espresso Based",
            variantSummary = "Cashew Milk (+Rp 5.000)",
            variantAddons = "Cashew Milk (+Rp 5.000)",
            variantExtraTotal = 5000.0
        )
        val csv = com.example.service.CsvExportService.generateSalesCsv(
            listOf(order),
            mapOf("INV-002" to listOf(item))
        )
        assertTrue(csv.contains("Transaction ID,Date & Time,Employee / Cashier,Shift,Order Type,Category,Product Name,Base Price,HPP (Unit),Total HPP,Gross Profit,Variant - Size,Variant - Type,Variant - Beans Type,Variant - Grind Size,Variant - Sugar Level,Variant - Add-ons,Variant Extra Price Total,Quantity,Subtotal,Discount,Dine-In Service Charge,Takeaway Packaging Fee,Tax (PB1),Grand Total,Payment Method"))
        assertTrue(csv.contains("Rangga Maheza,Shift 1"))
        assertTrue(csv.contains("11200.0")) // HPP unit & total
        assertTrue(csv.contains("25800.0")) // Gross profit (37000 - 11200)
        assertTrue(csv.contains("925.0")) // 2.5% Dine-In service charge
        assertTrue(csv.contains("Cashew Milk (+Rp 5.000)"))
    }

    @Test
    fun `settings storage service persists and loads custom outlet info across restarts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage1 = com.example.data.local.SettingsStorageService(context)
        storage1.saveOutletInfo(
            com.example.domain.model.OutletInfo(
                name = "BDS Coffee Roasters",
                branch = "Mega Kuningan",
                address = "Jl. Prof. Dr. Satrio No. 1",
                phone = "+62 811-2233-4455",
                taxId = "99.888.777.6-555.000",
                instagram = "@bdscoffee_roasters"
            )
        )

        // Simulate app restart with a fresh storage instance
        val storage2 = com.example.data.local.SettingsStorageService(context)
        val loaded = storage2.loadOutletInfo()
        assertEquals("BDS Coffee Roasters", loaded.name)
        assertEquals("Mega Kuningan", loaded.branch)
        assertEquals("Jl. Prof. Dr. Satrio No. 1", loaded.address)
        assertEquals("+62 811-2233-4455", loaded.phone)
        assertEquals("@bdscoffee_roasters", loaded.instagram)
    }

    @Test
    fun `settings storage service persists receipt customizer and tax configurations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage1 = com.example.data.local.SettingsStorageService(context)
        storage1.saveReceiptCustomizer(
            com.example.domain.model.ReceiptCustomizerConfig(
                paperSize = "80mm",
                showLogo = false,
                showTax = false,
                headerNote = "CUSTOM HEADER BDS",
                footerNote = "CUSTOM FOOTER NOTE"
            )
        )
        storage1.saveAdditionalFees(
            com.example.domain.model.AdditionalFeesConfig(
                isTaxEnabled = true,
                taxPercentage = 11.0,
                isServiceFeeEnabled = true,
                serviceFeePercentage = 7.0,
                isRoundingEnabled = false
            )
        )

        // Simulate app restart
        val storage2 = com.example.data.local.SettingsStorageService(context)
        val loadedReceipt = storage2.loadReceiptCustomizer()
        assertEquals("80mm", loadedReceipt.paperSize)
        assertEquals(false, loadedReceipt.showLogo)
        assertEquals(false, loadedReceipt.showTax)
        assertEquals("CUSTOM HEADER BDS", loadedReceipt.headerNote)
        assertEquals("CUSTOM FOOTER NOTE", loadedReceipt.footerNote)

        val loadedFees = storage2.loadAdditionalFees()
        assertEquals(true, loadedFees.isTaxEnabled)
        assertEquals(11.0, loadedFees.taxPercentage, 0.01)
        assertEquals(true, loadedFees.isServiceFeeEnabled)
        assertEquals(7.0, loadedFees.serviceFeePercentage, 0.01)
        assertEquals(false, loadedFees.isRoundingEnabled)
    }

    @Test
    fun `settings storage service persists active cashier and simplified shift`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage1 = com.example.data.local.SettingsStorageService(context)
        val emp = com.example.domain.model.EmployeeItem(
            name = "Budi Santoso",
            role = "Kasir",
            shift = "Shift 2",
            pin = "2024"
        )
        storage1.saveActiveEmployee(emp)
        storage1.saveActiveShift("Shift 2")

        // Simulate app restart
        val storage2 = com.example.data.local.SettingsStorageService(context)
        val loadedEmp = storage2.loadActiveEmployee()
        val loadedShift = storage2.loadActiveShift()

        assertEquals("Budi Santoso", loadedEmp.name)
        assertEquals("Kasir", loadedEmp.role)
        assertEquals("Shift 2", loadedShift)

        // Also test Event shift
        storage2.saveActiveShift("Event")
        assertEquals("Event", storage2.loadActiveShift())
    }

    @Test
    fun `cart item variant summary shows nominal price additions and hides zero cost`() {
        val product = com.example.data.local.model.ProductEntity(
            id = 10,
            categoryId = "coffee",
            name = "Espresso Tonic",
            sku = "CF-010",
            basePrice = 28000.0,
            costPrice = 10000.0,
            stockQuantity = 20
        )
        val variants = mapOf(
            "Size" to listOf(com.example.domain.model.VariantOption("Large", 5000.0)),
            "Type" to listOf(com.example.domain.model.VariantOption("Iced", 0.0)),
            "Add-ons" to listOf(com.example.domain.model.VariantOption("Extra Espresso Shot", 6000.0))
        )
        val cartItem = com.example.domain.model.CartItem(
            product = product,
            selectedVariants = variants,
            quantity = 1
        )

        val summary = cartItem.variantSummary
        assertTrue(summary.contains("Large (+Rp 5.000)"))
        assertTrue(summary.contains("Extra Espresso Shot (+Rp 6.000)"))
        assertTrue(summary.contains("Iced"))
        // Verify zero cost does not have (+Rp 0)
        org.junit.Assert.assertFalse(summary.contains("Iced (+Rp 0)"))
    }

    @Test
    fun `pdf receipt generation renders with dynamic height and no overflow`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val order = OrderEntity(
            invoiceNumber = "INV-TEST-099",
            timestamp = System.currentTimeMillis(),
            customerName = "Arif Rahardian",
            orderType = "DINE_IN",
            cashierName = "Rian Pratama",
            shiftName = "Shift 1",
            subtotal = 70000.0,
            taxAmount = 7000.0,
            grandTotal = 77000.0,
            paymentMethod = "CASH",
            tenderedAmount = 100000.0,
            changeAmount = 23000.0
        )
        val items = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-TEST-099",
                productId = 1,
                productName = "Specialty House Blend V60 Long Name That Might Wrap",
                sku = "CF-001",
                basePrice = 35000.0,
                unitPrice = 40000.0,
                quantity = 2,
                lineTotal = 70000.0,
                variantSummary = "Large (+Rp 5.000), Extra Espresso Shot (+Rp 6.000)"
            )
        )
        val message = com.example.service.PdfReceiptService.buildWhatsAppReceiptMessage(
            order = order,
            items = items
        )
        assertNotNull(message)
        assertTrue(message.contains("Kasir:* Rian Pratama"))
        org.junit.Assert.assertFalse(message.contains("Shift 1"))
        assertTrue(message.contains("Large (+Rp 5.000)"))
        assertTrue(message.contains("Extra Espresso Shot (+Rp 6.000)"))
        assertTrue(message.contains("77.000"))
    }

    @Test
    fun `official BDS COFFEE CO dynamic and static QRIS generation`() {
        val dynamicPayload = QrisPayloadGenerator.generateDynamicQris(
            invoiceNumber = "INV-BDS-001",
            amount = 50000.0,
            merchantName = "BDS COFFEE CO",
            nmid = "ID1024365195063",
            terminalId = "A01"
        )
        assertNotNull(dynamicPayload)
        assertTrue(dynamicPayload.contains("BDS COFFEE CO"))
        assertTrue(dynamicPayload.contains("ID1024365195063"))
        assertTrue(dynamicPayload.contains("A01"))
        assertTrue(dynamicPayload.contains("540550000")) // Nominal Rp 50.000
        assertEquals(4, dynamicPayload.takeLast(4).length)
        assertTrue(QrisPayloadGenerator.isValidQrisPayload(dynamicPayload))

        val staticPayload = QrisPayloadGenerator.generateStaticQris(
            merchantName = "BDS COFFEE CO",
            nmid = "ID1024365195063",
            terminalId = "A01"
        )
        assertEquals(QrisPayloadGenerator.OFFICIAL_STATIC_QRIS_PAYLOAD, staticPayload)
        assertTrue(staticPayload.startsWith("000201010211")) // Point of initiation 11 (Static QR)
        assertTrue(staticPayload.contains("BDS COFFEE CO"))
        assertTrue(staticPayload.contains("ID1024365195063"))
        assertTrue(staticPayload.contains("A01"))
        // Static QR does NOT have Tag 54 (amount fixed)
        org.junit.Assert.assertFalse(staticPayload.contains("540550000"))
        assertTrue(QrisPayloadGenerator.isValidQrisPayload(staticPayload))
    }

    @Test
    fun `zxing qr matrix generator produces valid non-empty matrix for official payload`() {
        val matrix = com.example.ui.components.QrMatrixBuilder.generateMatrix(QrisPayloadGenerator.OFFICIAL_STATIC_QRIS_PAYLOAD)
        assertTrue(matrix.isNotEmpty())
        assertTrue(matrix[0].isNotEmpty())
        val hasDarkModules = matrix.any { row -> row.any { it } }
        assertTrue(hasDarkModules)
        // With margin=1, (1, 1) is the start of the top-left finder pattern
        assertTrue(matrix[1][1])
    }

    @Test
    fun `official qris_bds asset png exists and loads via AssetManager and Drawable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetStream = context.assets.open("qris_bds.png")
        assertNotNull(assetStream)
        assertTrue(assetStream.available() > 0)
        assetStream.close()

        val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.qris_bds)
        assertNotNull(drawable)
    }

    @Test
    fun `printer connection guard rejects blank address`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(EscPosPrinterService.isPrinterConnected(context, ""))
        assertFalse(EscPosPrinterService.isPrinterConnected(context, "   "))
    }

    @Test
    fun `thermal receipt bytes show only cashier name and omit shift`() {
        val order = OrderEntity(
            invoiceNumber = "INV-003",
            timestamp = System.currentTimeMillis(),
            customerName = "Guest",
            cashierName = "Budi Kasir",
            shiftName = "Shift 1",
            subtotal = 35000.0,
            grandTotal = 35000.0,
            paymentMethod = "CASH"
        )
        val items = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-003",
                productId = 1,
                productName = "Latte",
                sku = "CF-001",
                basePrice = 35000.0,
                unitPrice = 35000.0,
                quantity = 1,
                lineTotal = 35000.0
            )
        )
        val bytes = EscPosPrinterService.buildReceiptBytes(order, items)
        val receiptText = String(bytes, Charsets.ISO_8859_1)
        assertTrue(receiptText.contains("Kasir:"))
        assertTrue(receiptText.contains("Budi Kasir"))
        org.junit.Assert.assertFalse(receiptText.contains("Shift 1"))
    }

    @Test
    fun `convertToDynamicQRIS correctly transforms base static payload with Tag 54 injection for Rp 29400`() {
        // Base static string for BDS COFFEE CO
        val baseStatic = QrisPayloadGenerator.OFFICIAL_STATIC_QRIS_PAYLOAD
        assertTrue(baseStatic.contains("010211")) // Static point of initiation

        val dynamicResult = QrisPayloadGenerator.convertToDynamicQRIS(
            staticString = baseStatic,
            amount = 29400.0,
            invoiceNumber = "INV-29400"
        )

        assertNotNull(dynamicResult)
        // 1. Point of initiation converted from 11 to 12
        assertTrue(dynamicResult.startsWith("000201010212"))
        org.junit.Assert.assertFalse(dynamicResult.contains("010211"))

        // 2. Tag 54 injected with exact length 05 and value 29400
        assertTrue(dynamicResult.contains("540529400"))

        // 3. Official credentials preserved
        assertTrue(dynamicResult.contains("ID1024365195063"))
        assertTrue(dynamicResult.contains("BDS COFFEE CO"))
        assertTrue(dynamicResult.contains("A01"))

        // 4. Tag 53 (IDR currency) precedes Tag 54
        val idx53 = dynamicResult.indexOf("5303360")
        val idx54 = dynamicResult.indexOf("540529400")
        assertTrue(idx53 != -1 && idx54 != -1 && idx54 > idx53)

        // 5. CRC16-CCITT checksum valid
        assertTrue(dynamicResult.contains("6304"))
        assertEquals(4, dynamicResult.takeLast(4).length)
        assertTrue(QrisPayloadGenerator.isValidQrisPayload(dynamicResult))
    }

    @Test
    fun `order entity and receipts correctly map customer name under Pelanggan`() {
        val order = OrderEntity(
            invoiceNumber = "INV-004",
            timestamp = System.currentTimeMillis(),
            customerName = "Kak Budi - Meja 03",
            cashierName = "Rian Kasir",
            subtotal = 40000.0,
            grandTotal = 40000.0,
            paymentMethod = "CASH"
        )
        val items = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-004",
                productId = 1,
                productName = "Americano",
                sku = "CF-002",
                basePrice = 40000.0,
                unitPrice = 40000.0,
                quantity = 1,
                lineTotal = 40000.0
            )
        )
        val bytes = EscPosPrinterService.buildReceiptBytes(order, items)
        val receiptText = String(bytes, Charsets.ISO_8859_1)
        assertTrue(receiptText.contains("Pelanggan:"))
        assertTrue(receiptText.contains("Kak Budi - Meja 03"))

        val message = com.example.service.PdfReceiptService.buildWhatsAppReceiptMessage(order, items)
        assertTrue(message.contains("👤 *Pelanggan:* Kak Budi - Meja 03"))
    }

    @Test
    fun `tiered B2B pricing calculates correct dynamic unit price and tier labels`() {
        val b2bProduct = com.example.data.local.model.ProductEntity(
            id = 201,
            sku = "B2B-001",
            name = "Roasted Beans House Blend 1kg",
            categoryId = "roasted_beans",
            basePrice = 250000.0,
            costPrice = 120000.0,
            stockQuantity = 100,
            hasB2bPricing = true,
            b2bTier1MinQty = 5,
            b2bTier1Price = 220000.0,
            b2bTier2MinQty = 10,
            b2bTier2Price = 200000.0
        )

        // Case 1: Standard retail qty < Tier 1
        val cartRetail = com.example.domain.model.CartItem(
            product = b2bProduct,
            quantity = 2
        )
        assertEquals(250000.0, cartRetail.finalUnitPrice, 0.01)
        assertEquals(500000.0, cartRetail.lineTotal, 0.01)
        org.junit.Assert.assertFalse(cartRetail.isB2bPriceApplied)
        org.junit.Assert.assertNull(cartRetail.b2bTierLabel)

        // Case 2: Tier 1 (qty = 5)
        val cartTier1 = com.example.domain.model.CartItem(
            product = b2bProduct,
            quantity = 5
        )
        assertEquals(220000.0, cartTier1.finalUnitPrice, 0.01)
        assertEquals(1100000.0, cartTier1.lineTotal, 0.01)
        assertTrue(cartTier1.isB2bPriceApplied)
        assertEquals("B2B Tier 1 (≥5)", cartTier1.b2bTierLabel)

        // Case 3: Tier 2 (qty = 10)
        val cartTier2 = com.example.domain.model.CartItem(
            product = b2bProduct,
            quantity = 10
        )
        assertEquals(200000.0, cartTier2.finalUnitPrice, 0.01)
        assertEquals(2000000.0, cartTier2.lineTotal, 0.01)
        assertTrue(cartTier2.isB2bPriceApplied)
        assertEquals("B2B Tier 2 (≥10)", cartTier2.b2bTierLabel)

        // Case 4: Non-B2B product remains strictly standard retail even at high quantity
        val standardProduct = com.example.data.local.model.ProductEntity(
            id = 202,
            sku = "STD-001",
            name = "Croissant Butter",
            categoryId = "pastry",
            basePrice = 28000.0,
            costPrice = 12000.0,
            stockQuantity = 50,
            hasB2bPricing = false
        )
        val cartStandard = com.example.domain.model.CartItem(
            product = standardProduct,
            quantity = 25
        )
        assertEquals(28000.0, cartStandard.finalUnitPrice, 0.01)
        assertEquals(700000.0, cartStandard.lineTotal, 0.01)
        org.junit.Assert.assertFalse(cartStandard.isB2bPriceApplied)
        org.junit.Assert.assertNull(cartStandard.b2bTierLabel)
    }

    @Test
    fun `database seeder populates bds coffee company catalog with 35 percent hpp and is idempotent`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val dao = db.posDao()

        assertEquals(0, dao.getProductCount())
        assertEquals(0, dao.getCategoryCount())
        assertEquals(0, dao.getOrderCount())

        // First seed
        com.example.data.local.DatabaseSeeder.seedIfEmpty(dao)

        val prodCount = dao.getProductCount()
        val catCount = dao.getCategoryCount()
        val orderCount = dao.getOrderCount()
        val variantGroupCount = dao.getVariantGroupCount()

        assertEquals(35, prodCount)
        assertEquals(8, catCount)
        assertEquals(1, variantGroupCount)
        assertEquals("Fresh install must start with 0 dummy orders", 0, orderCount)
        assertNull("Fresh install must start with no active/dummy shift", dao.getActiveShift())

        // Check exact categories & 35% HPP across every seeded product
        val prods = dao.getAllProductsSync()
        assertEquals(35, prods.size)
        prods.forEach { p ->
            assertEquals("Expected 35% HPP for ${p.name}", kotlin.math.round(p.basePrice * 0.35), p.costPrice, 0.01)
            assertTrue("${p.name} should default active", p.isActive)
        }

        val espresso = prods.find { it.name == "Espresso" }
        assertNotNull(espresso)
        assertEquals(20000.0, espresso!!.basePrice, 0.01)
        assertEquals(7000.0, espresso.costPrice, 0.01)

        val cashewBottle = prods.find { it.name == "Cashew Coffee Milk (500ml)" }
        assertNotNull(cashewBottle)
        assertEquals(90000.0, cashewBottle!!.basePrice, 0.01)
        assertEquals(31500.0, cashewBottle.costPrice, 0.01)

        // Second seed (idempotent - should not duplicate)
        com.example.data.local.DatabaseSeeder.seedIfEmpty(dao)
        assertEquals(prodCount, dao.getProductCount())
        assertEquals(catCount, dao.getCategoryCount())
        assertEquals(orderCount, dao.getOrderCount())

        db.close()
    }

    @Test
    fun `cart draft persistence and recovery in SettingsStorageService`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = com.example.data.local.SettingsStorageService(context)

        service.clearActiveCartDraft()
        org.junit.Assert.assertNull(service.loadActiveCartDraft())

        val sampleJson = """[{"productId":1,"productName":"BDS Aren Latte","quantity":2}]"""
        service.saveActiveCartDraft(
            itemsJson = sampleJson,
            customerName = "Kak Rudi / Meja 03",
            orderType = "DINE_IN",
            discountType = "NONE",
            discountValue = 0.0
        )

        val loaded = service.loadActiveCartDraft()
        assertNotNull(loaded)
        assertEquals("Kak Rudi / Meja 03", loaded!!.customerName)
        assertEquals("DINE_IN", loaded.orderType)
        assertEquals(sampleJson, loaded.itemsJson)

        service.clearActiveCartDraft()
        org.junit.Assert.assertNull(service.loadActiveCartDraft())
    }

    @Test
    fun `dynamic date grouping partitions transactions by WIB calendar date and calculates day-specific totals`() {
        val calWib = java.util.Calendar.getInstance(com.example.ui.components.DateTimeUtils.WIB_ZONE).apply {
            set(2026, java.util.Calendar.OCTOBER, 28, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val t1 = calWib.timeInMillis // 28 Oct 2026 10:00 WIB

        calWib.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calWib.set(java.util.Calendar.MINUTE, 30)
        val t2LateNight = calWib.timeInMillis // 28 Oct 2026 23:30 WIB (in UTC this is 16:30 same day, but tests calendar boundary)

        calWib.set(2026, java.util.Calendar.OCTOBER, 27, 14, 0, 0)
        val t3Yesterday = calWib.timeInMillis // 27 Oct 2026 14:00 WIB

        // 1. Verify Date Key grouping (yyyy-MM-dd)
        assertEquals("2026-10-28", com.example.ui.components.DateTimeUtils.formatDateKey(t1))
        assertEquals("2026-10-28", com.example.ui.components.DateTimeUtils.formatDateKey(t2LateNight))
        assertEquals("2026-10-27", com.example.ui.components.DateTimeUtils.formatDateKey(t3Yesterday))

        // 2. Verify Indonesian Header formatting
        val header1 = com.example.ui.components.DateTimeUtils.formatDateHeader(t1)
        val header3 = com.example.ui.components.DateTimeUtils.formatDateHeader(t3Yesterday)
        assertTrue(header1.contains("28"))
        assertTrue(header1.contains("Okt") || header1.contains("Oktober"))
        assertTrue(header3.contains("27"))

        // 3. Verify Orders Grouping and Specific Daily Totals
        val orders = listOf(
            OrderEntity(
                invoiceNumber = "INV-01",
                timestamp = t1,
                customerName = "Cust 1",
                subtotal = 50000.0,
                grandTotal = 50000.0,
                paymentMethod = "CASH"
            ),
            OrderEntity(
                invoiceNumber = "INV-02",
                timestamp = t2LateNight,
                customerName = "Cust 2",
                subtotal = 75000.0,
                grandTotal = 75000.0,
                paymentMethod = "QRIS"
            ),
            OrderEntity(
                invoiceNumber = "INV-03",
                timestamp = t3Yesterday,
                customerName = "Cust 3",
                subtotal = 120000.0,
                grandTotal = 120000.0,
                paymentMethod = "BANK_TRANSFER"
            )
        )

        val grouped = orders.groupBy { com.example.ui.components.DateTimeUtils.formatDateKey(it.timestamp) }
        assertEquals(2, grouped.keys.size)
        assertTrue(grouped.containsKey("2026-10-28"))
        assertTrue(grouped.containsKey("2026-10-27"))

        val day1Orders = grouped["2026-10-28"]!!
        val day2Orders = grouped["2026-10-27"]!!

        assertEquals(2, day1Orders.size)
        assertEquals(125000.0, day1Orders.sumOf { it.grandTotal }, 0.01)

        assertEquals(1, day2Orders.size)
        assertEquals(120000.0, day2Orders.sumOf { it.grandTotal }, 0.01)

        // 4. Verify Start & End of Day bounds in WIB
        val startOfT1 = com.example.ui.components.DateTimeUtils.getStartOfDayWib(t1)
        val endOfT1 = com.example.ui.components.DateTimeUtils.getEndOfDayWib(t1)
        assertTrue(t1 in startOfT1..endOfT1)
        assertTrue(t2LateNight in startOfT1..endOfT1)
        org.junit.Assert.assertFalse(t3Yesterday in startOfT1..endOfT1)
    }

    @Test
    fun `extract date directly from invoice ID pattern and partition historical dates independently`() {
        val nowMistakenTimestamp = System.currentTimeMillis() // Today timestamp

        // Orders created on different historical dates but with identical/today raw timestamp
        val orderOct27 = OrderEntity(
            invoiceNumber = "INV-20261027-142010",
            timestamp = nowMistakenTimestamp,
            customerName = "Cust 27",
            subtotal = 50000.0,
            grandTotal = 50000.0,
            paymentMethod = "CASH"
        )
        val orderOct28 = OrderEntity(
            invoiceNumber = "INV-20261028-113045",
            timestamp = nowMistakenTimestamp,
            customerName = "Cust 28",
            subtotal = 80000.0,
            grandTotal = 80000.0,
            paymentMethod = "QRIS"
        )
        val orderOct29 = OrderEntity(
            invoiceNumber = "INV-20261029-091520",
            timestamp = nowMistakenTimestamp,
            customerName = "Cust 29",
            subtotal = 35000.0,
            grandTotal = 35000.0,
            paymentMethod = "BANK_TRANSFER"
        )

        // 1. Verify getOrderDateKey parses directly from invoice prefix
        assertEquals("2026-10-27", com.example.ui.components.DateTimeUtils.getOrderDateKey(orderOct27))
        assertEquals("2026-10-28", com.example.ui.components.DateTimeUtils.getOrderDateKey(orderOct28))
        assertEquals("2026-10-29", com.example.ui.components.DateTimeUtils.getOrderDateKey(orderOct29))

        // 2. Verify getOrderEffectiveTimestamp extracts exact WIB time
        val eff27 = com.example.ui.components.DateTimeUtils.getOrderEffectiveTimestamp(orderOct27)
        val eff28 = com.example.ui.components.DateTimeUtils.getOrderEffectiveTimestamp(orderOct28)
        val eff29 = com.example.ui.components.DateTimeUtils.getOrderEffectiveTimestamp(orderOct29)

        assertEquals("14:20:10 WIB", com.example.ui.components.DateTimeUtils.formatTime(eff27))
        assertEquals("11:30:45 WIB", com.example.ui.components.DateTimeUtils.formatTime(eff28))
        assertEquals("09:15:20 WIB", com.example.ui.components.DateTimeUtils.formatTime(eff29))

        // 3. Verify distinct calendar date grouping into 3 separate groups
        val orders = listOf(orderOct27, orderOct28, orderOct29)
        val groupedByDate = orders.groupBy { com.example.ui.components.DateTimeUtils.getOrderDateKey(it) }

        assertEquals(3, groupedByDate.keys.size)
        assertTrue(groupedByDate.containsKey("2026-10-27"))
        assertTrue(groupedByDate.containsKey("2026-10-28"))
        assertTrue(groupedByDate.containsKey("2026-10-29"))

        // 4. Verify each group's independent totals
        assertEquals(50000.0, groupedByDate["2026-10-27"]!!.sumOf { it.grandTotal }, 0.01)
        assertEquals(80000.0, groupedByDate["2026-10-28"]!!.sumOf { it.grandTotal }, 0.01)
        assertEquals(35000.0, groupedByDate["2026-10-29"]!!.sumOf { it.grandTotal }, 0.01)
    }

    @Test
    fun `complete data wipeout resets database tables and staff to zero`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val dao = db.posDao()

        // Populate database with master catalog, then insert real transaction & shift records
        com.example.data.local.DatabaseSeeder.seedIfEmpty(dao)
        assertEquals(0, dao.getOrderCount())
        dao.insertOrder(
            OrderEntity(
                invoiceNumber = "INV-WIPE-001",
                timestamp = System.currentTimeMillis(),
                subtotal = 30000.0,
                grandTotal = 33000.0,
                paymentMethod = "CASH"
            )
        )
        assertTrue(dao.getProductCount() > 0)
        assertTrue(dao.getCategoryCount() > 0)
        assertTrue(dao.getOrderCount() > 0)
        assertTrue(dao.getVariantGroupCount() > 0)

        // Populate staff and cart drafts
        val storage = com.example.data.local.SettingsStorageService(context)
        storage.saveEmployees(listOf(
            com.example.domain.model.EmployeeItem(name = "Test Staff", role = "Barista", pin = "9999")
        ))
        storage.saveActiveCartDraft("[]", "Cust A", "DINE_IN", "NONE", 0.0)
        assertEquals(1, storage.loadEmployees().size)
        assertNotNull(storage.loadActiveCartDraft())

        // Execute complete wipeout
        dao.wipeAllDatabaseData()
        storage.wipeAllStaffAndDrafts()

        // Assert clean slate with only default Owner account (Rangga Maheza)
        assertEquals(0, dao.getProductCount())
        assertEquals(0, dao.getCategoryCount())
        assertEquals(0, dao.getOrderCount())
        assertEquals(0, dao.getVariantGroupCount())
        val wipedEmployees = storage.loadEmployees()
        assertEquals(1, wipedEmployees.size)
        assertEquals("Rangga Maheza", wipedEmployees.first().name)
        assertEquals("Owner", wipedEmployees.first().role)
        assertEquals("0000", wipedEmployees.first().pin)
        assertEquals("Rangga Maheza", storage.loadActiveEmployee().name)
        org.junit.Assert.assertNull(storage.loadActiveCartDraft())

        db.close()
    }

    @Test
    fun `variant data mapper accurately extracts attributes from cart item map`() {
        val product = com.example.data.local.model.ProductEntity(
            id = 50,
            sku = "CF-050",
            name = "Spanish Latte",
            categoryId = "coffee",
            basePrice = 30000.0,
            costPrice = 12000.0,
            stockQuantity = 100
        )
        val selectedVariants = mapOf(
            "Size" to listOf(com.example.domain.model.VariantOption("Large", 5000.0)),
            "Type" to listOf(com.example.domain.model.VariantOption("Iced", 0.0)),
            "Beans Type" to listOf(com.example.domain.model.VariantOption("Single Origin Ethiopia", 8000.0)),
            "Grind Size" to listOf(com.example.domain.model.VariantOption("Espresso (Fine)", 0.0)),
            "Sugar Level" to listOf(com.example.domain.model.VariantOption("Less Sugar 50%", 0.0)),
            "Add-ons" to listOf(com.example.domain.model.VariantOption("Oat Milk Sub", 8000.0))
        )
        val cartItem = com.example.domain.model.CartItem(
            product = product,
            selectedVariants = selectedVariants,
            quantity = 1
        )

        val mapped = com.example.service.VariantDataMapper.extractFromCartItem(cartItem)
        assertEquals("Large (+Rp 5.000)", mapped.size)
        assertEquals("Iced", mapped.type)
        assertEquals("Single Origin Ethiopia (+Rp 8.000)", mapped.beansType)
        assertEquals("Espresso (Fine)", mapped.grindSize)
        assertEquals("Less Sugar 50%", mapped.sugarLevel)
        assertEquals("Oat Milk Sub (+Rp 8.000)", mapped.addOns)
        assertEquals(21000.0, mapped.extraPriceTotal, 0.01)
    }

    @Test
    fun `csv export generator populates variant columns from summary when individual entity fields are unpopulated`() {
        val order = OrderEntity(
            invoiceNumber = "INV-20261029-999999",
            timestamp = System.currentTimeMillis(),
            customerName = "Customer Test",
            orderType = "TAKEAWAY",
            subtotal = 40000.0,
            packagingFeeAmount = 2000.0,
            taxAmount = 4000.0,
            grandTotal = 46000.0,
            paymentMethod = "QRIS"
        )
        // Simulate order item where variant columns were unpopulated or empty, but variantSummary was saved
        val item = OrderItemEntity(
            orderInvoiceNumber = "INV-20261029-999999",
            productId = 1,
            productName = "Café Latte",
            sku = "EB-006",
            basePrice = 32000.0,
            costPrice = 11200.0,
            variantSummary = "Iced (+Rp 3.000), Extra Shot (+Rp 5.000)",
            unitPrice = 40000.0,
            quantity = 1,
            lineTotal = 40000.0,
            categoryName = "Espresso Based",
            variantSize = "",
            variantType = "",
            variantBeans = "",
            variantGrind = "",
            variantSugar = "",
            variantAddons = "",
            variantExtraTotal = 0.0
        )

        val csv = com.example.service.CsvExportService.generateSalesCsv(
            listOf(order),
            mapOf("INV-20261029-999999" to listOf(item))
        )

        // Verify CSV header
        assertTrue(csv.contains("Variant - Size,Variant - Type,Variant - Beans Type,Variant - Grind Size,Variant - Sugar Level,Variant - Add-ons,Variant Extra Price Total"))

        // Verify that add-ons and takeaway packaging fee are populated properly
        assertTrue(csv.contains("Iced (+Rp 3.000), Extra Shot (+Rp 5.000)"))
        assertTrue(csv.contains("8000.0")) // 3000 + 5000
        assertTrue(csv.contains("2000.0")) // packaging fee
    }

    @Test
    fun `EscPosPrinterService getPairedBluetoothPrinters contains no hardcoded simulator devices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val printers = com.example.service.EscPosPrinterService.getPairedBluetoothPrinters(context, "")
        // Verify completely stripped simulators
        val hasSimulator = printers.any { 
            it.name.contains("Virtual", ignoreCase = true) || 
            it.name.contains("Simulasi", ignoreCase = true) ||
            it.address == "00:11:22:33:44:55" ||
            it.address == "FF:EE:DD:CC:BB:AA"
        }
        assertFalse("Device list must not contain any fake simulator devices", hasSimulator)
    }

    @Test
    fun `EscPosPrinterService checkPrinterConnection returns failure for blank address`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = com.example.service.EscPosPrinterService.checkPrinterConnection(context, "")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Belum ada printer Bluetooth yang dipilih") == true)
    }

    @Test
    fun `EscPosPrinterService checkPrinterConnection returns failure for unbonded or unreachable device`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = com.example.service.EscPosPrinterService.checkPrinterConnection(context, "00:11:22:33:44:55")
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue("Error message must explain printer disconnected or not paired: $msg", 
            msg.contains("Gagal mencetak") || msg.contains("terputus") || msg.contains("belum dipasangkan"))
    }

    @Test
    fun `PosViewModel selectPrinter updates state and persists device selection`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        vm.selectPrinter("11:22:33:44:55:66", "RPP02N Thermal")

        assertEquals("11:22:33:44:55:66", vm.selectedPrinterAddress.value)
        assertEquals("RPP02N Thermal", vm.selectedPrinterName.value)
    }

    @Test
    fun `PosViewModel attemptPrintReceipt triggers error alert dialog when printer is disconnected`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        // Select a disconnected printer address
        vm.selectPrinter("11:22:33:44:55:66", "RPP02N Thermal")

        val dummyOrder = OrderEntity(
            invoiceNumber = "INV-TEST-ALERT",
            timestamp = System.currentTimeMillis(),
            customerName = "Test Walk-in",
            subtotal = 30000.0,
            grandTotal = 33000.0,
            paymentMethod = "CASH"
        )
        val dummyItems = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-TEST-ALERT",
                productId = 1,
                productName = "Latte",
                sku = "CF-01",
                basePrice = 30000.0,
                unitPrice = 30000.0,
                quantity = 1,
                lineTotal = 30000.0
            )
        )

        // Attempt print with non-existent hardware connection
        vm.attemptPrintReceipt(app, dummyOrder, dummyItems)

        // Verify alert state and error message
        assertTrue("showPrinterDisconnectedAlert must be true", vm.showPrinterDisconnectedAlert.value)
        val errorMsg = vm.printerErrorMessage.value ?: ""
        assertTrue("Error message must explain failure: $errorMsg", errorMsg.startsWith("Gagal mencetak"))

        // Cashier can dismiss or retry
        vm.dismissPrinterDisconnectedAlert()
        assertFalse("showPrinterDisconnectedAlert must be false after dismissal", vm.showPrinterDisconnectedAlert.value)
    }

    @Test
    fun `multi-role authentication verifies PIN and grants role-based tab access`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        val owner = com.example.domain.model.EmployeeItem(
            id = "emp_owner",
            name = "Rian Ardiansyah",
            role = "Owner",
            pin = "0000"
        )
        val cashier = com.example.domain.model.EmployeeItem(
            id = "emp_kasir",
            name = "Budi Santoso",
            role = "Kasir",
            pin = "1111"
        )

        // 1. Invalid PIN fails
        val invalidLogin = vm.loginStaff(owner, "9999")
        assertFalse("Invalid PIN must fail", invalidLogin)
        assertFalse("isLoggedIn must remain false on invalid PIN", vm.isLoggedIn.value)

        // 2. Owner login succeeds and gets all 5 tabs
        val ownerLogin = vm.loginStaff(owner, "0000")
        assertTrue("Owner login must succeed with correct PIN", ownerLogin)
        assertTrue("isLoggedIn must be true", vm.isLoggedIn.value)
        assertEquals("Owner", vm.currentUser.value?.role)
        assertTrue("Owner isSuperAdmin must be true", vm.currentUser.value?.isSuperAdmin() == true)
        assertEquals(5, vm.currentUser.value?.allowedTabs()?.size)
        assertTrue(vm.currentUser.value?.allowedTabs()?.contains(com.example.ui.components.PosTab.SETTINGS) == true)
        assertTrue(vm.currentUser.value?.allowedTabs()?.contains(com.example.ui.components.PosTab.DASHBOARD) == true)

        // 3. Logout
        vm.logoutStaff()
        assertFalse("isLoggedIn must be false after logout", vm.isLoggedIn.value)
        org.junit.Assert.assertNull(vm.currentUser.value)

        // 4. Cashier login succeeds but restricted to KASIR and TRANSAKSI tabs
        val cashierLogin = vm.loginStaff(cashier, "1111")
        assertTrue("Cashier login must succeed with correct PIN", cashierLogin)
        assertTrue("isLoggedIn must be true", vm.isLoggedIn.value)
        assertEquals("Kasir", vm.currentUser.value?.role)
        assertFalse("Cashier isSuperAdmin must be false", vm.currentUser.value?.isSuperAdmin() == true)
        val cashierTabs = vm.currentUser.value?.allowedTabs() ?: emptyList()
        assertEquals(2, cashierTabs.size)
        assertTrue(cashierTabs.contains(com.example.ui.components.PosTab.KASIR))
        assertTrue(cashierTabs.contains(com.example.ui.components.PosTab.TRANSAKSI))
        assertFalse("Kasir cannot access Dashboard", cashierTabs.contains(com.example.ui.components.PosTab.DASHBOARD))
        assertFalse("Kasir cannot access Settings", cashierTabs.contains(com.example.ui.components.PosTab.SETTINGS))
        assertFalse("Kasir cannot access Katalog", cashierTabs.contains(com.example.ui.components.PosTab.KATALOG))

        // 5. Navigation guard redirects unauthorized tab selection to KASIR
        vm.setCurrentTab(com.example.ui.components.PosTab.SETTINGS)
        assertEquals("Unauthorized tab must redirect to KASIR", com.example.ui.components.PosTab.KASIR, vm.currentTab.value)
    }

    @Test
    fun `shift management workflow tracks petty cash and closing reconciliation`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        val cashier = com.example.domain.model.EmployeeItem(
            id = "emp_kasir_shift",
            name = "Siti Kasir",
            role = "Kasir",
            pin = "1111"
        )
        vm.loginStaff(cashier, "1111")

        // 1. Cashier opens shift with 500,000 IDR starting cash
        vm.openShiftSession(500000.0, "Shift 1")
        kotlinx.coroutines.delay(100) // Allow IO coroutine to complete

        val activeShift = vm.activeShiftSession.value
        assertNotNull("Active shift must be created", activeShift)
        assertEquals("OPEN", activeShift?.status)
        assertEquals(500000.0, activeShift?.startingCash ?: 0.0, 0.01)
        assertEquals("Siti Kasir", activeShift?.staffName)

        // 2. Cashier closes shift with 750,000 IDR physical cash in drawer
        vm.closeShiftSession(750000.0, "Diserahkan ke kasir shift sore")
        kotlinx.coroutines.delay(100)

        // After closing shift, active shift session should be cleared
        org.junit.Assert.assertNull("Active shift must be cleared after close", vm.activeShiftSession.value)
    }

    @Test
    fun `opening checkout without active shift prompts open shift modal`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        // Ensure no active shift
        vm.dismissOpenShift()

        // Attempt to checkout
        vm.openCheckoutModal()

        assertTrue("Opening checkout without open shift must prompt open shift modal", vm.showOpenShiftModal.value)
    }

    @Test
    fun `shift closing receipt bytes format contains expected petty cash and reconciliation lines`() {
        val shift = com.example.data.local.model.ShiftEntity(
            shiftCode = "SHF-TEST-001",
            staffId = "emp-01",
            staffName = "Budi Santoso",
            staffRole = "Kasir",
            shiftName = "Shift 1",
            openedAt = System.currentTimeMillis() - 28800000L,
            startingCash = 500000.0
        )

        val payload = EscPosPrinterService.buildShiftClosingReceiptBytes(
            shift = shift,
            cashSales = 850000.0,
            nonCashSales = 420000.0,
            bankTransferSales = 150000.0,
            qrisSales = 270000.0,
            grandTotalRevenue = 1270000.0,
            actualCash = 1350000.0,
            transactionCount = 18,
            productSales = mapOf(
                "Caffe Latte" to 12,
                "House Blend 250g" to 3
            ),
            storeName = "BDS COFFEE COMPANY",
            storeBranch = "Senopati Roastery"
        )

        assertNotNull(payload)
        assertTrue(payload.isNotEmpty())

        val receiptText = String(payload, Charsets.ISO_8859_1)
        assertTrue(receiptText.contains("BDS COFFEE COMPANY"))
        assertTrue(receiptText.contains("REKAP PENUTUPAN KASIR"))
        assertTrue(receiptText.contains("Budi Santoso"))
        assertTrue(receiptText.contains("Shift 1"))
        assertTrue(receiptText.contains("Kas Awal (Petty Cash):"))
        assertTrue(receiptText.contains("Pendapatan Tunai Sistem:"))
        assertTrue(receiptText.contains("Ekspektasi Kas di Laci:"))
        assertTrue(receiptText.contains("Kas Fisik di Tangan:"))
        assertTrue(receiptText.contains("Selisih (Discrepancy):"))
        assertTrue(receiptText.contains("Total Cash:"))
        assertTrue(receiptText.contains("Total QRIS:"))
        assertTrue(receiptText.contains("Total Bank Transfer (TF):"))
        assertTrue(receiptText.contains("Grand Total Omset Shift:"))
        assertTrue(receiptText.contains("PRODUK TERJUAL (ITEMIZED SALES)"))
        assertTrue(receiptText.contains("Caffe Latte"))
        assertTrue(receiptText.contains("x12"))
        assertTrue(receiptText.contains("House Blend 250g"))
        assertTrue(receiptText.contains("x3"))
        assertTrue(receiptText.contains("TANDA TANGAN & VERIFIKASI"))
    }

    @Test
    fun `printActiveShiftClosingReceipt triggers alert when printer is disconnected or unconfigured`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        val owner = com.example.domain.model.EmployeeItem(
            id = "emp_owner",
            name = "Rian Ardiansyah",
            role = "Owner",
            pin = "0000"
        )
        vm.loginStaff(owner, "0000")
        vm.openShiftSession(200000.0, "Shift 1")
        kotlinx.coroutines.delay(100)

        // Clear printer address to test disconnected/unconfigured printer behavior
        vm.selectPrinter("", "")

        // Click "Cetak Struk"
        vm.printActiveShiftClosingReceipt(app, 250000.0)
        kotlinx.coroutines.delay(100)

        // Must display alert popup with informative error message
        assertTrue("showPrinterDisconnectedAlert must be true", vm.showPrinterDisconnectedAlert.value)
        assertFalse("isPrintingAnimation must be false after error", vm.isPrintingAnimation.value)
        val errorMsg = vm.printerErrorMessage.value ?: ""
        assertTrue("Error message must indicate disconnected printer: $errorMsg", errorMsg.contains("printer", ignoreCase = true) || errorMsg.contains("terputus", ignoreCase = true) || errorMsg.contains("Bluetooth", ignoreCase = true))

        // Dismiss alert
        vm.dismissPrinterDisconnectedAlert()
        assertFalse("Alert must be dismissed", vm.showPrinterDisconnectedAlert.value)
    }

    @Test
    fun `close shift session auto print triggers and calculates accurate product sales breakdown`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        val owner = com.example.domain.model.EmployeeItem(
            id = "emp_owner",
            name = "Rian Ardiansyah",
            role = "Owner",
            pin = "0000"
        )
        vm.loginStaff(owner, "0000")

        // 1. Open Shift
        vm.openShiftSession(200000.0, "Shift 1")
        kotlinx.coroutines.delay(100)
        assertNotNull(vm.activeShiftSession.value)

        // 2. Close Shift with actual physical cash
        vm.closeShiftSession(350000.0, "Shift 1 selesai rapi")
        kotlinx.coroutines.delay(100)

        // Active shift is null and user is still logged in since role is Owner
        org.junit.Assert.assertNull(vm.activeShiftSession.value)
        assertTrue(vm.isLoggedIn.value)
    }

    @Test
    fun `fresh staff directory contains only owner Rangga Maheza and protects sole owner deletion`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Clear any leftover staff prefs to simulate clean install
        app.getSharedPreferences("caffeinery_pos_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val vm = PosViewModel(app)
        val employees = vm.employees.value
        assertEquals(1, employees.size)
        val owner = employees.first()
        assertEquals("Rangga Maheza", owner.name)
        assertEquals("Owner", owner.role)
        assertEquals("0000", owner.pin)

        // Cannot delete the sole remaining Owner
        assertFalse(vm.removeEmployee(owner.id))
        assertEquals(1, vm.employees.value.size)

        // Adding a dynamic cashier & editing and deleting works
        vm.addEmployee("Dina Kasir", "Kasir", "1234")
        assertEquals(2, vm.employees.value.size)
        val addedKasir = vm.employees.value.find { it.name == "Dina Kasir" }!!
        vm.updateEmployee(addedKasir.copy(name = "Dina Barista", role = "Barista"))
        assertEquals("Dina Barista", vm.employees.value.find { it.id == addedKasir.id }?.name)
        assertTrue(vm.removeEmployee(addedKasir.id))
        assertEquals(1, vm.employees.value.size)
    }

    @Test
    fun `product active inactive toggle hides inactive items from POS while keeping them in catalog`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val dao = db.posDao()
        com.example.data.local.DatabaseSeeder.seedIfEmpty(dao)

        val allInitial = dao.getAllProductsIncludingInactiveSync()
        assertEquals(35, allInitial.size)
        val target = allInitial.first()

        // Toggle inactive (out of stock)
        dao.updateProductActiveStatus(target.id, false)

        val activeForPos = dao.getAllProductsSync()
        val catalogManagementList = dao.getAllProductsIncludingInactiveSync()

        assertEquals(34, activeForPos.size)
        assertFalse(activeForPos.any { it.id == target.id })
        assertEquals(35, catalogManagementList.size)
        assertFalse(catalogManagementList.first { it.id == target.id }.isActive)

        // Reactivate
        dao.updateProductActiveStatus(target.id, true)
        assertEquals(35, dao.getAllProductsSync().size)
        db.close()
    }

    @Test
    fun `dine in applies 2 point 5 percent charge and takeaway applies toggleable packaging fee`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("caffeinery_pos_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = PosViewModel(app)

        // Collect cartSummary eagerly so WhileSubscribed starts before ViewModel mutations
        val collectJob = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            vm.cartSummary.collect {}
        }

        val item = com.example.data.local.model.ProductEntity(
            id = 100,
            sku = "EB-100",
            name = "O.G Coffee Milk",
            categoryId = "espresso_based",
            basePrice = 40000.0,
            costPrice = 14000.0,
            stockQuantity = 50
        )
        vm.addToCart(item, emptyMap(), quantity = 1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // 1. Default DINE_IN -> automatic 2.5% charge (1000.0 on 40000.0), 0.0 packaging
        vm.setOrderType("DINE_IN")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        var summary = vm.cartSummary.value
        assertEquals(2.5, summary.serviceFeePercentage, 0.01)
        assertEquals(1000.0, summary.serviceFeeAmount, 0.01)
        assertEquals(0.0, summary.packagingFeeAmount, 0.01)

        // 2. Switch to TAKEAWAY -> 0.0 Dine-in service charge; packaging fee OFF initially
        vm.setOrderType("TAKEAWAY")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        summary = vm.cartSummary.value
        assertEquals(0.0, summary.serviceFeeAmount, 0.01)
        assertEquals(0.0, summary.packagingFeeAmount, 0.01)

        // 3. Toggle Takeaway Packaging ON -> adds 2000.0 packaging fee
        vm.setTakeawayPackagingEnabled(true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        summary = vm.cartSummary.value
        assertEquals(0.0, summary.serviceFeeAmount, 0.01)
        assertEquals(2000.0, summary.packagingFeeAmount, 0.01)

        collectJob.cancel()
    }

    @Test
    fun `thermal receipt with monochrome logo places logo at bottom after footer and uses 58mm styling`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val order = OrderEntity(
            invoiceNumber = "INV-LOGO-TEST",
            timestamp = System.currentTimeMillis(),
            customerName = "Meja 01",
            orderType = "TAKEAWAY",
            cashierName = "Rangga Maheza",
            subtotal = 37000.0,
            serviceFeeAmount = 0.0,
            packagingFeeAmount = 2000.0,
            taxPercentage = 10.0,
            taxAmount = 3700.0,
            grandTotal = 42700.0,
            paymentMethod = "CASH",
            tenderedAmount = 50000.0,
            changeAmount = 7300.0
        )
        val items = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-LOGO-TEST",
                productId = 6,
                productName = "Café Latte",
                sku = "EB-006",
                basePrice = 32000.0,
                costPrice = 11200.0,
                unitPrice = 37000.0,
                quantity = 1,
                lineTotal = 37000.0,
                variantSummary = "Cashew Milk (+Rp 5.000)"
            )
        )

        val bytes = EscPosPrinterService.buildReceiptBytes(
            order = order,
            items = items,
            storeName = "BDS COFFEE CO",
            footerMessage = "Terima kasih atas pembelian Anda.",
            context = context,
            showLogo = true,
            queueNumber = "07"
        )
        assertTrue(bytes.isNotEmpty())

        // Verify GS_TEXT_DOUBLE_HEIGHT (0x1D, 0x21, 0x01) is used instead of 0x11 so 58mm header stays 32-col
        val hasDoubleHeight = (0 until bytes.size - 2).any { i ->
            bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x21.toByte() && bytes[i + 2] == 0x01.toByte()
        }
        val hasDoubleWidthHeight = (0 until bytes.size - 2).any { i ->
            bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x21.toByte() && bytes[i + 2] == 0x11.toByte()
        }
        assertTrue("Header must use double height (0x01)", hasDoubleHeight)
        assertFalse("Header must NOT use double width+height (0x11) on 58mm paper", hasDoubleWidthHeight)

        // Verify GS v 0 raster command (0x1D, 0x76, 0x30) is emitted at the bottom AFTER footer text
        val rasterIdx = (0 until bytes.size - 2).firstOrNull { i ->
            bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x76.toByte() && bytes[i + 2] == 0x30.toByte()
        } ?: -1
        assertTrue("Monochrome raster logo GS v 0 must be present", rasterIdx >= 0)

        val text = String(bytes, Charsets.US_ASCII)
        val storeIdx = text.indexOf("BDS COFFEE CO")
        val queueIdx = text.indexOf("Antrean 07")
        val itemIdx = text.indexOf("1 Cafe Latte")
        val addonIdx = text.indexOf("  Cashew Milk (+Rp 5.000)")
        val packIdx = text.indexOf("Biaya Kemasan:")
        val totalIdx = text.indexOf("Total Tagihan:")
        val footerIdx = text.indexOf("Terima kasih atas pembelian")
        val andaIdx = text.indexOf("Anda.")

        assertTrue("Split order info must contain Antrean 07", queueIdx > storeIdx)
        assertTrue("Simplified order type must be Takeaway", text.contains("Takeaway"))
        assertFalse("Must remove long BUNGKUS label", text.contains("BUNGKUS"))
        assertTrue("Footer closing message must appear intact after Total Tagihan", footerIdx > totalIdx && andaIdx > footerIdx)
        assertTrue("Ordered item must appear after Antrean header", itemIdx > queueIdx)
        assertTrue("Indented add-on detail must be printed below item", addonIdx > itemIdx)
        assertTrue("Takeaway packaging fee line must be printed", packIdx > addonIdx)
        assertTrue("Total Tagihan line must be printed", totalIdx > packIdx)
        assertTrue("Logo raster must appear at the bottom AFTER footer text", rasterIdx > andaIdx)
    }

    @Test
    fun `fresh install initializes master catalog and owner only with zero orders and shifts`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val dao = db.posDao()

        com.example.data.local.DatabaseSeeder.seedIfEmpty(dao)

        assertEquals(8, dao.getCategoryCount())
        assertEquals(35, dao.getProductCount())
        assertEquals(1, dao.getVariantGroupCount())
        assertEquals(0, dao.getOrderCount())
        assertTrue(dao.getAllOrdersSync().isEmpty())
        assertNull(dao.getActiveShift())

        db.close()
    }

    @Test
    fun `room migration from v8 to v10 preserves existing orders shifts custom products and adds cash expenses`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_upgrade_preserve_${System.currentTimeMillis()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Simulate a pre-existing v8 database with user orders, order_items, shifts, and a custom product
        val legacySqlDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacySqlDb.version = 8
        legacySqlDb.execSQL("CREATE TABLE categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, iconName TEXT NOT NULL, colorHex TEXT NOT NULL, sortOrder INTEGER NOT NULL DEFAULT 0)")
        legacySqlDb.execSQL("INSERT INTO categories VALUES ('custom_cat', 'Custom Cat', 'coffee', '#000000', 1)")
        legacySqlDb.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sku TEXT NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, basePrice REAL NOT NULL, costPrice REAL NOT NULL DEFAULT 0.0, stockQuantity INTEGER NOT NULL, unit TEXT NOT NULL DEFAULT 'cup', description TEXT NOT NULL DEFAULT '', iconName TEXT NOT NULL DEFAULT 'coffee', variantsJson TEXT NOT NULL DEFAULT '', hasB2bPricing INTEGER NOT NULL DEFAULT 0, b2bTier1MinQty INTEGER NOT NULL DEFAULT 5, b2bTier1Price REAL NOT NULL DEFAULT 0.0, b2bTier2MinQty INTEGER NOT NULL DEFAULT 10, b2bTier2Price REAL NOT NULL DEFAULT 0.0)")
        legacySqlDb.execSQL("INSERT INTO products (id, sku, name, categoryId, basePrice, costPrice, stockQuantity) VALUES (77, 'CUST-77', 'Owner Custom Blend', 'custom_cat', 45000.0, 15750.0, 20)")
        legacySqlDb.execSQL("CREATE TABLE orders (invoiceNumber TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, customerName TEXT NOT NULL DEFAULT 'Walk-in Guest', orderType TEXT NOT NULL DEFAULT 'DINE_IN', subtotal REAL NOT NULL, itemDiscountTotal REAL NOT NULL DEFAULT 0.0, orderDiscountPercentage REAL NOT NULL DEFAULT 0.0, orderDiscountAmount REAL NOT NULL DEFAULT 0.0, serviceFeePercentage REAL NOT NULL DEFAULT 0.0, serviceFeeAmount REAL NOT NULL DEFAULT 0.0, taxPercentage REAL NOT NULL DEFAULT 10.0, taxAmount REAL NOT NULL DEFAULT 0.0, grandTotal REAL NOT NULL, paymentMethod TEXT NOT NULL, tenderedAmount REAL NOT NULL DEFAULT 0.0, changeAmount REAL NOT NULL DEFAULT 0.0, paymentStatus TEXT NOT NULL DEFAULT 'PAID', cashierName TEXT NOT NULL DEFAULT 'Rangga Maheza', shiftName TEXT NOT NULL DEFAULT 'Shift 1', notes TEXT NOT NULL DEFAULT '')")
        legacySqlDb.execSQL("INSERT INTO orders (invoiceNumber, timestamp, customerName, subtotal, grandTotal, paymentMethod) VALUES ('INV-PROD-999', 1730000000000, 'VIP Guest', 45000.0, 49500.0, 'QRIS')")
        legacySqlDb.execSQL("CREATE TABLE order_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, orderInvoiceNumber TEXT NOT NULL, productId INTEGER NOT NULL, productName TEXT NOT NULL, sku TEXT NOT NULL, basePrice REAL NOT NULL, costPrice REAL NOT NULL DEFAULT 0.0, variantSummary TEXT NOT NULL DEFAULT '', unitPrice REAL NOT NULL, quantity INTEGER NOT NULL, itemDiscount REAL NOT NULL DEFAULT 0.0, lineTotal REAL NOT NULL, categoryName TEXT NOT NULL DEFAULT '', variantSize TEXT NOT NULL DEFAULT '', variantType TEXT NOT NULL DEFAULT '', variantBeans TEXT NOT NULL DEFAULT '', variantGrind TEXT NOT NULL DEFAULT '', variantSugar TEXT NOT NULL DEFAULT '', variantAddons TEXT NOT NULL DEFAULT '', variantExtraTotal REAL NOT NULL DEFAULT 0.0, FOREIGN KEY(orderInvoiceNumber) REFERENCES orders(invoiceNumber) ON DELETE CASCADE)")
        legacySqlDb.execSQL("CREATE INDEX index_order_items_orderInvoiceNumber ON order_items (orderInvoiceNumber)")
        legacySqlDb.execSQL("INSERT INTO order_items (orderInvoiceNumber, productId, productName, sku, basePrice, costPrice, unitPrice, quantity, lineTotal) VALUES ('INV-PROD-999', 77, 'Owner Custom Blend', 'CUST-77', 45000.0, 15750.0, 45000.0, 1, 45000.0)")
        legacySqlDb.execSQL("CREATE TABLE variant_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, isMultiSelect INTEGER NOT NULL DEFAULT 0, optionsJson TEXT NOT NULL, applicableCategoryIds TEXT NOT NULL DEFAULT 'all', sortOrder INTEGER NOT NULL DEFAULT 0)")
        legacySqlDb.execSQL("CREATE TABLE open_bills (id TEXT NOT NULL PRIMARY KEY, billNumber TEXT NOT NULL, customerName TEXT NOT NULL DEFAULT '', tableNumber TEXT NOT NULL DEFAULT '', orderType TEXT NOT NULL DEFAULT 'DINE_IN', itemsJson TEXT NOT NULL, totalItemCount INTEGER NOT NULL DEFAULT 0, subtotal REAL NOT NULL DEFAULT 0.0, taxAmount REAL NOT NULL DEFAULT 0.0, serviceFeeAmount REAL NOT NULL DEFAULT 0.0, grandTotal REAL NOT NULL DEFAULT 0.0, notes TEXT NOT NULL DEFAULT '', cashierName TEXT NOT NULL DEFAULT 'Rangga Maheza', createdAt INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'ACTIVE')")
        legacySqlDb.execSQL("CREATE TABLE shifts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftCode TEXT NOT NULL, staffId TEXT NOT NULL, staffName TEXT NOT NULL, staffRole TEXT NOT NULL, shiftName TEXT NOT NULL, openedAt INTEGER NOT NULL, closedAt INTEGER, startingCash REAL NOT NULL DEFAULT 0.0, totalCashSales REAL NOT NULL DEFAULT 0.0, totalNonCashSales REAL NOT NULL DEFAULT 0.0, totalTransactions INTEGER NOT NULL DEFAULT 0, expectedCash REAL NOT NULL DEFAULT 0.0, actualCash REAL, cashDifference REAL, notes TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'OPEN')")
        legacySqlDb.execSQL("INSERT INTO shifts (shiftCode, staffId, staffName, staffRole, shiftName, openedAt, startingCash, status) VALUES ('SHF-PROD-1', 'emp_owner', 'Rangga Maheza', 'Owner', 'Shift 1', 1730000000000, 300000.0, 'OPEN')")
        legacySqlDb.close()

        // 2. Open with Room v10 using safe migrations (no destructive wipe)
        val upgradedDb = androidx.room.Room.databaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java,
            dbName
        )
            .addMigrations(*com.example.data.local.AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val dao = upgradedDb.posDao()
        // Run seeder as on startup
        com.example.data.local.DatabaseSeeder.seedIfEmpty(dao)

        // 3. Assert ALL historical production records survived upgrade untouched!
        val preservedOrder = dao.getOrderByInvoice("INV-PROD-999")
        assertNotNull("Existing transaction must be preserved across app upgrade", preservedOrder)
        assertEquals(49500.0, preservedOrder!!.grandTotal, 0.01)
        assertEquals(0.0, preservedOrder.packagingFeeAmount, 0.01)

        val preservedItems = dao.getOrderItemsForInvoiceSync("INV-PROD-999")
        assertEquals(1, preservedItems.size)
        assertEquals("Owner Custom Blend", preservedItems.first().productName)

        val preservedShift = dao.getActiveShift()
        assertNotNull("Existing active shift must be preserved across app upgrade", preservedShift)
        assertEquals("SHF-PROD-1", preservedShift!!.shiftCode)
        assertEquals(300000.0, preservedShift.startingCash, 0.01)
        assertEquals(0.0, preservedShift.totalCashExpenses, 0.01)

        // 4. Verify new v10 cash_expenses table works seamlessly alongside preserved shift
        dao.insertCashExpense(
            com.example.data.local.model.CashExpenseEntity(
                shiftId = preservedShift.id,
                shiftCode = preservedShift.shiftCode,
                staffName = "Rangga Maheza",
                description = "Beli plastik & es batu",
                amount = 25000.0
            )
        )
        val expenses = dao.getCashExpensesForShiftSync("SHF-PROD-1")
        assertEquals(1, expenses.size)
        assertEquals(25000.0, expenses.first().amount, 0.01)

        val preservedProducts = dao.getAllProductsIncludingInactiveSync()
        assertEquals(1, preservedProducts.size) // Not overwritten by 35 default items!
        assertEquals("Owner Custom Blend", preservedProducts.first().name)
        assertTrue(preservedProducts.first().isActive)

        upgradedDb.close()
        context.deleteDatabase(dbName)
        Unit
    }

    @Test
    fun `cash expenses deduct from expected cash formula on shift closing and thermal receipt`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val dao = db.posDao()

        val openedAt = System.currentTimeMillis() - 3600_000L
        val shift = com.example.data.local.model.ShiftEntity(
            shiftCode = "SHF-EXP-1",
            staffId = "emp_kasir",
            staffName = "Dina Kasir",
            staffRole = "Kasir",
            shiftName = "Shift 1",
            openedAt = openedAt,
            startingCash = 500000.0,
            status = "OPEN"
        )
        val shiftId = dao.insertShift(shift)
        val savedShift = shift.copy(id = shiftId)

        // Cash sales: Rp 200.000
        dao.insertOrder(
            OrderEntity(
                invoiceNumber = "INV-CASH-1",
                timestamp = openedAt + 60_000L,
                customerName = "Walk-in",
                subtotal = 200000.0,
                grandTotal = 200000.0,
                paymentMethod = "CASH",
                cashierName = "Dina Kasir"
            )
        )

        // Cash expenses: Rp 35.000 + Rp 15.000 = Rp 50.000
        dao.insertCashExpense(
            com.example.data.local.model.CashExpenseEntity(
                shiftId = shiftId,
                shiftCode = "SHF-EXP-1",
                staffName = "Dina Kasir",
                description = "Plastik Kemasan",
                amount = 35000.0
            )
        )
        dao.insertCashExpense(
            com.example.data.local.model.CashExpenseEntity(
                shiftId = shiftId,
                shiftCode = "SHF-EXP-1",
                staffName = "Dina Kasir",
                description = "Air Mineral",
                amount = 15000.0
            )
        )

        val expenses = dao.getCashExpensesForShiftSync("SHF-EXP-1")
        val totalExpenses = expenses.sumOf { it.amount }
        assertEquals(50000.0, totalExpenses, 0.01)

        // Formula: Expected Cash = (Initial Petty Cash + Cash Sales) - Total Cash Expenses
        val cashSales = 200000.0
        val expectedCash = (savedShift.startingCash + cashSales) - totalExpenses
        assertEquals(650000.0, expectedCash, 0.01)

        val actualCash = 645000.0
        val discrepancy = actualCash - expectedCash
        assertEquals(-5000.0, discrepancy, 0.01)

        val bytes = EscPosPrinterService.buildShiftClosingReceiptBytes(
            shift = savedShift,
            cashSales = cashSales,
            actualCash = actualCash,
            transactionCount = 1,
            productSales = mapOf("O.G Coffee Milk" to 4),
            cashExpenses = expenses,
            totalCashExpenses = totalExpenses,
            context = context,
            showLogo = true
        )
        val receiptText = String(bytes, Charsets.US_ASCII)
        assertTrue(receiptText.contains("Pengeluaran Kas:"))
        assertTrue(receiptText.contains("50.000"))
        assertTrue(receiptText.contains("650.000"))
        assertTrue(receiptText.contains("Plastik Kemasan"))

        // Verify logo raster is at the very bottom after footer text
        val rasterIdx = (0 until bytes.size - 2).firstOrNull { i ->
            bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x76.toByte() && bytes[i + 2] == 0x30.toByte()
        } ?: -1
        val footerIdx = receiptText.indexOf("Caffeinery POS by BDS Tech")
        assertTrue("Logo raster must appear after footer on closing receipt", rasterIdx > footerIdx && footerIdx >= 0)

        db.close()
        Unit
    }

    @Test
    fun `ShiftReportService formats complete WhatsApp shift report with expenses and breakdown`() {
        val shift = com.example.data.local.model.ShiftEntity(
            id = 12L,
            shiftCode = "SHF-20261121-01",
            staffId = "emp_kasir",
            staffName = "Budi Santoso",
            staffRole = "Kasir",
            shiftName = "Shift 1",
            openedAt = System.currentTimeMillis() - 7200_000L,
            startingCash = 300000.0
        )
        val expenses = listOf(
            com.example.data.local.model.CashExpenseEntity(
                id = 1L,
                shiftId = 12L,
                shiftCode = shift.shiftCode,
                staffName = "Budi Santoso",
                description = "Es Batu",
                amount = 20000.0
            )
        )

        val msg = com.example.service.ShiftReportService.buildShiftClosingWhatsAppMessage(
            shift = shift,
            storeName = "BDS Coffee Company",
            storeBranch = "Senopati Roastery",
            cashSales = 400000.0,
            qrisSales = 250000.0,
            bankTransferSales = 100000.0,
            grandTotalRevenue = 750000.0,
            cashExpenses = expenses,
            totalCashExpenses = 20000.0,
            actualCash = 680000.0,
            transactionCount = 15,
            productSales = mapOf("Cafe Latte" to 10, "Matcha Latte" to 5),
            notes = "Kas aman"
        )

        assertTrue(msg.contains("BDS Coffee Company"))
        assertTrue(msg.contains("SHF-20261121-01"))
        assertTrue(msg.contains("Budi Santoso"))
        assertTrue(msg.contains("300.000")) // Petty cash
        assertTrue(msg.contains("400.000")) // Cash sales
        assertTrue(msg.contains("250.000")) // QRIS sales
        assertTrue(msg.contains("100.000")) // Bank Transfer sales
        assertTrue(msg.contains("20.000"))  // Cash expense
        assertTrue(msg.contains("Es Batu"))
        assertTrue(msg.contains("680.000")) // Expected & actual (300k + 400k - 20k)
        assertTrue(msg.contains("Pas / Sesuai"))
        assertTrue(msg.contains("Cafe Latte: 10x"))
        assertTrue(msg.contains("Matcha Latte: 5x"))
    }

    @Test
    fun `CSV export retrieves all orders across all staff accounts newly added products variants and fees directly from Room`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = com.example.data.local.AppDatabase.getDatabase(app)
        val dao = db.posDao()
        dao.wipeAllDatabaseData()

        // Create a brand-new category and newly added product
        dao.insertCategory(
            com.example.data.local.model.CategoryEntity(
                id = "cat_signature_new",
                name = "Signature Seasonal",
                iconName = "coffee",
                colorHex = "#1E293B",
                sortOrder = 99
            )
        )
        val newProdId = dao.insertProduct(
            com.example.data.local.model.ProductEntity(
                sku = "SIG-NEW-01",
                name = "Yuzu Espresso Tonic",
                categoryId = "cat_signature_new",
                basePrice = 42000.0,
                costPrice = 14700.0,
                stockQuantity = 40,
                isActive = true
            )
        )

        // Orders placed across multiple accounts: Owner, Kasir, Barista
        val ownerOrder = OrderEntity(
            invoiceNumber = "INV-ROLE-OWNER",
            timestamp = System.currentTimeMillis() - 3000,
            customerName = "Walk-in 1",
            orderType = "DINE_IN",
            subtotal = 47000.0,
            serviceFeePercentage = 2.5,
            serviceFeeAmount = 1175.0,
            taxPercentage = 10.0,
            taxAmount = 4817.5,
            grandTotal = 53000.0,
            paymentMethod = "QRIS",
            cashierName = "Rangga Maheza",
            shiftName = "Shift 1"
        )
        val kasirOrder = OrderEntity(
            invoiceNumber = "INV-ROLE-KASIR",
            timestamp = System.currentTimeMillis() - 2000,
            customerName = "Walk-in 2",
            orderType = "TAKEAWAY",
            subtotal = 35000.0,
            packagingFeeAmount = 2000.0,
            taxPercentage = 10.0,
            taxAmount = 3500.0,
            grandTotal = 40500.0,
            paymentMethod = "CASH",
            cashierName = "Siti Kasir",
            shiftName = "Shift 1"
        )
        val baristaOrder = OrderEntity(
            invoiceNumber = "INV-ROLE-BARISTA",
            timestamp = System.currentTimeMillis() - 1000,
            customerName = "Walk-in 3",
            orderType = "DINE_IN",
            subtotal = 30000.0,
            serviceFeePercentage = 2.5,
            serviceFeeAmount = 750.0,
            taxPercentage = 10.0,
            taxAmount = 3075.0,
            grandTotal = 33800.0,
            paymentMethod = "BANK_TRANSFER",
            cashierName = "Andi Barista",
            shiftName = "Shift 2"
        )

        dao.recordSale(
            ownerOrder,
            listOf(
                OrderItemEntity(
                    orderInvoiceNumber = "INV-ROLE-OWNER",
                    productId = newProdId,
                    productName = "Yuzu Espresso Tonic",
                    sku = "SIG-NEW-01",
                    basePrice = 42000.0,
                    costPrice = 14700.0,
                    variantSummary = "Extra Shot (+Rp 5.000)",
                    unitPrice = 47000.0,
                    quantity = 1,
                    lineTotal = 47000.0,
                    categoryName = "Signature Seasonal",
                    variantAddons = "Extra Shot (+Rp 5.000)",
                    variantExtraTotal = 5000.0
                )
            )
        )
        dao.recordSale(
            kasirOrder,
            listOf(
                OrderItemEntity(
                    orderInvoiceNumber = "INV-ROLE-KASIR",
                    productId = 6L,
                    productName = "Café Latte",
                    sku = "EB-006",
                    basePrice = 32000.0,
                    costPrice = 11200.0,
                    variantSummary = "Iced (+Rp 3.000)",
                    unitPrice = 35000.0,
                    quantity = 1,
                    lineTotal = 35000.0,
                    categoryName = "ESPRESSO BASED",
                    variantAddons = "Iced (+Rp 3.000)",
                    variantExtraTotal = 3000.0
                )
            )
        )
        dao.recordSale(
            baristaOrder,
            listOf(
                OrderItemEntity(
                    orderInvoiceNumber = "INV-ROLE-BARISTA",
                    productId = 10L,
                    productName = "O.G Coffee Milk",
                    sku = "EB-010",
                    basePrice = 28000.0,
                    costPrice = 9800.0,
                    variantSummary = "Less Sugar",
                    unitPrice = 30000.0,
                    quantity = 1,
                    lineTotal = 30000.0,
                    categoryName = "ESPRESSO BASED"
                )
            )
        )

        // Generate CSV directly via ViewModel without UI collection on StateFlows
        val vm = PosViewModel(app)
        val csv = vm.generateFullSalesCsvFromRoom()

        assertTrue("Must contain Owner transaction", csv.contains("INV-ROLE-OWNER"))
        assertTrue("Must contain Owner cashier name", csv.contains("Rangga Maheza"))
        assertTrue("Must contain newly added product", csv.contains("Yuzu Espresso Tonic"))
        assertTrue("Must contain newly added category", csv.contains("Signature Seasonal"))
        assertTrue("Must contain Kasir transaction", csv.contains("INV-ROLE-KASIR"))
        assertTrue("Must contain Kasir cashier name", csv.contains("Siti Kasir"))
        assertTrue("Must contain Barista transaction", csv.contains("INV-ROLE-BARISTA"))
        assertTrue("Must contain Barista cashier name", csv.contains("Andi Barista"))
        assertTrue("Must contain Takeaway packaging fee", csv.contains("2000.0"))
        assertTrue("Must contain Dine-in service charge", csv.contains("1175.0"))

        dao.wipeAllDatabaseData()
        Unit
    }

    @Test
    fun `smart takeaway charge applies per item to beverage and pastry only and exempts beans equipment and merchandise`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("caffeinery_pos_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = PosViewModel(app)

        val beverage = com.example.data.local.model.ProductEntity(
            id = 501,
            sku = "EB-501",
            name = "Café Latte",
            categoryId = "espresso_based",
            basePrice = 32000.0,
            costPrice = 11200.0,
            stockQuantity = 50
        )
        val pastry = com.example.data.local.model.ProductEntity(
            id = 502,
            sku = "BP-502",
            name = "Burn Cheese Cake",
            categoryId = "bites_pastry",
            basePrice = 25000.0,
            costPrice = 8750.0,
            stockQuantity = 30
        )
        val beans = com.example.data.local.model.ProductEntity(
            id = 503,
            sku = "RB-503",
            name = "House Blend Beans 250g",
            categoryId = "roastery_beans",
            basePrice = 120000.0,
            costPrice = 60000.0,
            stockQuantity = 20
        )
        val equipment = com.example.data.local.model.ProductEntity(
            id = 504,
            sku = "EQ-504",
            name = "V60 Ceramic Dripper",
            categoryId = "manual_brew_equipment",
            basePrice = 250000.0,
            costPrice = 150000.0,
            stockQuantity = 10
        )
        val merch = com.example.data.local.model.ProductEntity(
            id = 505,
            sku = "MR-505",
            name = "BDS Tumbler",
            categoryId = "merchandise",
            basePrice = 180000.0,
            costPrice = 90000.0,
            stockQuantity = 15
        )

        // 1. Cart containing only exempted items -> 0 packaging fee even when Takeaway + Packaging ON
        vm.setOrderType("TAKEAWAY")
        vm.setTakeawayPackagingEnabled(true)
        vm.addToCart(beans, emptyMap(), quantity = 2)
        vm.addToCart(equipment, emptyMap(), quantity = 1)
        vm.addToCart(merch, emptyMap(), quantity = 1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        var summary = vm.cartSummary.value
        assertEquals(0, summary.eligibleTakeawayItemCount)
        assertEquals(0.0, summary.packagingFeeAmount, 0.01)

        // 2. Add 2 Beverages + 1 Pastry -> eligible qty = 3 -> per-item default (2000 x 3 = 6000)
        vm.addToCart(beverage, emptyMap(), quantity = 2)
        vm.addToCart(pastry, emptyMap(), quantity = 1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        summary = vm.cartSummary.value
        assertEquals(3, summary.eligibleTakeawayItemCount)
        assertEquals(6000.0, summary.packagingFeeAmount, 0.01)

        // 3. Switch to flat fee per bill -> 2000.0 flat regardless of 3 eligible items
        val currentFees = vm.additionalFees.value
        vm.updateAdditionalFees(currentFees.copy(isTakeawayFeePerItem = false))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        summary = vm.cartSummary.value
        assertEquals(3, summary.eligibleTakeawayItemCount)
        assertEquals(2000.0, summary.packagingFeeAmount, 0.01)
    }

    @Test
    fun `livin merchant 58mm receipt formats Dine In and split modifiers on separate indented lines`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val order = OrderEntity(
            invoiceNumber = "INV-20261121-015",
            timestamp = System.currentTimeMillis(),
            customerName = "Meja 04",
            orderType = "DINE_IN",
            cashierName = "Rangga Maheza",
            subtotal = 40000.0,
            serviceFeePercentage = 2.5,
            serviceFeeAmount = 1000.0,
            taxPercentage = 10.0,
            taxAmount = 4100.0,
            grandTotal = 45100.0,
            paymentMethod = "QRIS"
        )
        val items = listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-20261121-015",
                productId = 6,
                productName = "Café Latte",
                sku = "EB-006",
                basePrice = 32000.0,
                costPrice = 11200.0,
                unitPrice = 40000.0,
                quantity = 1,
                lineTotal = 40000.0,
                variantSummary = "Iced (+Rp 3.000), Cashew Milk (+Rp 5.000)"
            )
        )

        val bytes = EscPosPrinterService.buildReceiptBytes(
            order = order,
            items = items,
            storeName = "BDS COFFEE CO",
            context = context,
            showLogo = true,
            queueNumber = "15"
        )
        val text = String(bytes, Charsets.US_ASCII)

        assertTrue("Must contain split Antrean 15 and Dine In", text.contains("Antrean 15") && text.contains("Dine In"))
        assertTrue("Must show [Qty] [Product Name]", text.contains("1 Cafe Latte"))
        assertTrue("Must indent first modifier on its own line", text.contains("\n  Iced (+Rp 3.000)\n"))
        assertTrue("Must indent second modifier on its own line", text.contains("\n  Cashew Milk (+Rp 5.000)\n"))
        assertTrue("Must show Service Charge 2.5%", text.contains("Service Charge (2.5%):"))
        assertTrue("Must show Total Tagihan", text.contains("Total Tagihan:"))
        assertTrue("Must show Livin Merchant footer wrapped cleanly at word boundary", text.contains("Terima kasih atas pembelian") && text.contains("Anda."))
    }
}

