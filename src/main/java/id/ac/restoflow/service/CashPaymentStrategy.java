package id.ac.restoflow.service;

/** Strategi pembayaran tunai (Cash): menerima nominal >= tagihan dan menghitung uang kembalian. */
public final class CashPaymentStrategy implements PaymentStrategy {
    @Override
    public String method() {
        return "Cash";
    }

    @Override
    public boolean allowsCustomAmount() {
        return true;
    }

    @Override
    public long defaultReceivedAmount(long totalBill) {
        return Math.max(0L, totalBill);
    }

    @Override
    public long validateAndCalculateChange(long totalBill, long amountReceived) {
        if (totalBill < 0) throw new IllegalArgumentException("Tagihan tidak valid.");
        if (amountReceived < totalBill) {
            throw new IllegalArgumentException("Nominal diterima kurang.");
        }
        return Math.subtractExact(amountReceived, totalBill);
    }
}
