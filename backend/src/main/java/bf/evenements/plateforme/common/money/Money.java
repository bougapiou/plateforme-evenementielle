package bf.evenements.plateforme.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * A monetary amount with its ISO currency. Default currency is XOF (FCFA);
 * the type keeps the door open for other currencies later.
 */
public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "XOF";

    public Money {
        amount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.toUpperCase();
    }

    public static Money xof(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean isZeroOrLess() {
        return amount.signum() <= 0;
    }

    public String formatted() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMaximumFractionDigits(currency.equals(DEFAULT_CURRENCY) ? 0 : 2);
        String suffix = currency.equals(DEFAULT_CURRENCY) ? "FCFA" : currency;
        return nf.format(amount) + " " + suffix;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Devises différentes : " + currency + " / " + other.currency);
        }
    }
}
