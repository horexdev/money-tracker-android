# Money Math

`core:money` owns shared money parsing and arithmetic for Android domain and UI layers.

## Contract

- Money amounts are stored as integer cents in `Long`.
- Domain parsing uses `MoneyParser.parsePositiveCents()` and accepts positive decimal input with at most two fraction digits.
- UI input can use `MoneyParser.sanitizeAmountInput()` and `MoneyParser.parseCentsOrZero()` for source-compatible lenient behavior.
- Expression input is parsed by `MoneyExpressionParser` with `BigDecimal`, not binary floating point.
- Expression grammar supports `+`, `-`, `*`, `/`, parentheses, unary signs, `%`, comma decimal separators, and Unicode multiply/divide/minus equivalents.
- Percentage in addition/subtraction follows the Mini App behavior: `200+10%` means `200 + 10% of 200`.
- Conversion uses `rate_e8`, where `100_000_000` means `1.0`, and rounds with `RoundingMode.HALF_UP`.
- `core:money` must not depend on Room, DataStore, UI, or source/server identity fields.
