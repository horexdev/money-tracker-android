package dev.horex.moneytracker.core.testing

data class TestMoney(
    val cents: Long,
    val currencyCode: String,
)

data class TestAccountFixture(
    val localId: Long,
    val name: String,
    val balance: TestMoney,
)

data class TestCategoryFixture(
    val localId: Long,
    val name: String,
    val type: TestCategoryType,
)

enum class TestCategoryType {
    Expense,
    Income,
}

object MoneyTrackerTestFixtures {
    val cashAccount = TestAccountFixture(
        localId = 1L,
        name = "Cash",
        balance = TestMoney(cents = 125_00, currencyCode = "USD"),
    )

    val cardAccount = TestAccountFixture(
        localId = 2L,
        name = "Card",
        balance = TestMoney(cents = 250_00, currencyCode = "USD"),
    )

    val foodCategory = TestCategoryFixture(
        localId = 1L,
        name = "Food",
        type = TestCategoryType.Expense,
    )

    val salaryCategory = TestCategoryFixture(
        localId = 2L,
        name = "Salary",
        type = TestCategoryType.Income,
    )
}
