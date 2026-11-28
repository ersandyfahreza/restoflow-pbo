package id.ac.restoflow.service;

/** Strategi pembayaran non-tunai (Debit / QRIS): nominal dikunci tepat sesuai tagihan tanpa kembalian. */
public final class NonCashPaymentStrategy implements PaymentStrategy {
    private final String methodName;

    public NonCashPaymentStrategy(String methodName) {
        if (!"Debit".equals(methodName) && !"QRIS".equals(methodName)) {
            throw new IllegalArgumentException("Metode non-tunai harus Debit atau QRIS.");
        }
        this.methodName = methodName;
    }

    @Override
    public String method() {
        return methodName;
    }

    @Override
    public boolean allowsCustomAmount() {
        return false;
    }

    @Override
    public long defaultReceivedAmount(long totalBill) {
        return Math.max(0L, totalBill);
    }

    @Override
    public long validateAndCalculateChange(long totalBill, long amountReceived) {
        if (totalBill < 0) throw new IllegalArgumentException("Tagihan tidak valid.");
        if (amountReceived != totalBill) {
            throw new IllegalArgumentException("Konfirmasi non-tunai harus sesuai nominal tagihan.");
        }
        return 0L;
    }
}
