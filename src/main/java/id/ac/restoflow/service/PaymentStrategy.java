package id.ac.restoflow.service;

/**
 * Kontrak strategi pembayaran (Polymorphism & Strategy Pattern).
 * Memisahkan aturan validasi nominal, kembalian, dan perilaku input antara pembayaran tunai dan non-tunai.
 */
public interface PaymentStrategy {
    String method();
    boolean allowsCustomAmount();
    long defaultReceivedAmount(long totalBill);
    long validateAndCalculateChange(long totalBill, long amountReceived);

    static PaymentStrategy forMethod(String method) {
        if (method == null) throw new IllegalArgumentException("Pilih metode pembayaran.");
        return switch (method) {
            case "Cash" -> new CashPaymentStrategy();
            case "Debit", "QRIS" -> new NonCashPaymentStrategy(method);
            default -> throw new IllegalArgumentException("Metode pembayaran tidak dikenal: " + method);
        };
    }
}
