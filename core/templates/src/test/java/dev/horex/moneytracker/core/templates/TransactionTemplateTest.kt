package dev.horex.moneytracker.core.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionTemplateTest {
    @Test
    fun amountModeMapsToStorageFlag() {
        assertEquals(TransactionTemplateAmountMode.Fixed, TransactionTemplateAmountMode.fromAmountFixed(true))
        assertEquals(TransactionTemplateAmountMode.Variable, TransactionTemplateAmountMode.fromAmountFixed(false))
        assertEquals(true, TransactionTemplateAmountMode.Fixed.amountFixed)
        assertEquals(false, TransactionTemplateAmountMode.Variable.amountFixed)
    }

    @Test
    fun fixedApplyUsesStoredAmount() {
        assertEquals(
            1_250L,
            resolveTemplateApplyAmount(
                amountMode = TransactionTemplateAmountMode.Fixed,
                storedAmountCents = 1_250,
                variableAmountCents = null,
            ),
        )
    }

    @Test
    fun variableApplyRequiresPositiveRuntimeAmount() {
        assertEquals(
            2_500L,
            resolveTemplateApplyAmount(
                amountMode = TransactionTemplateAmountMode.Variable,
                storedAmountCents = 1_000,
                variableAmountCents = 2_500,
            ),
        )
        assertThrows(InvalidTransactionTemplateAmountException::class.java) {
            resolveTemplateApplyAmount(
                amountMode = TransactionTemplateAmountMode.Variable,
                storedAmountCents = 1_000,
                variableAmountCents = null,
            )
        }
        assertThrows(InvalidTransactionTemplateAmountException::class.java) {
            resolveTemplateApplyAmount(
                amountMode = TransactionTemplateAmountMode.Variable,
                storedAmountCents = 1_000,
                variableAmountCents = 0,
            )
        }
    }

    @Test
    fun reorderRequiresSameIdsExactlyOnce() {
        assertEquals(emptyList<Long>(), stableTemplateReorder(emptyList(), emptyList()))
        assertEquals(listOf(3L, 1L, 2L), stableTemplateReorder(listOf(1L, 2L, 3L), listOf(3L, 1L, 2L)))
        assertThrows(InvalidTransactionTemplateReorderException::class.java) {
            stableTemplateReorder(listOf(1L, 2L, 3L), listOf(3L, 1L))
        }
        assertThrows(InvalidTransactionTemplateReorderException::class.java) {
            stableTemplateReorder(listOf(1L, 2L, 3L), listOf(3L, 1L, 1L))
        }
    }
}
