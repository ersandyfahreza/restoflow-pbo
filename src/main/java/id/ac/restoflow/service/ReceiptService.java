package id.ac.restoflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import id.ac.restoflow.ui.Ui;
import java.util.ArrayList;
import java.util.List;

public final class ReceiptService {
    private ReceiptService() {}
    public static String render(JsonNode order,JsonNode settings) {
        List<String> lines=new ArrayList<>();
        lines.add(settings.path("display_name").asText("RestoFlow"));
        lines.add("NOTA #"+order.path("id").asText()+" · Antrean "+Ui.text(order,"queue"));
        lines.add(Ui.time(order.path("time"))+" WIB");
        lines.add("Jenis: "+Ui.text(order,"type")+" · "+Ui.text(order,"table_name"));
        lines.add("Pembayaran: "+Ui.text(order,"payment_status")+" / "+Ui.text(order,"method"));
        lines.add("------------------------------------------");
        long reconstructedTotal=0;boolean reconstructed=false;
        for(JsonNode item:order.path("items")) {
            long sub=Math.multiplyExact(item.path("price").asLong(),item.path("quantity").asLong());
            reconstructedTotal=Math.addExact(reconstructedTotal,sub);reconstructed|=item.path("reconstructed").asBoolean();
            lines.add(item.path("name").asText());
            lines.add("  "+item.path("quantity").asInt()+" x "+Ui.money(item.path("price").asLong())+" = "+Ui.money(sub));
        }
        lines.add("------------------------------------------");lines.add("TOTAL TERCATAT: "+Ui.money(order.path("total").asLong()));
        if(!order.path("amount_received").isNull()&&order.has("amount_received")) {
            lines.add("Diterima: "+Ui.money(order.path("amount_received").asLong()));
            lines.add("Kembali: "+Ui.money(order.path("amount_received").asLong()-order.path("total").asLong()));
        }
        if(reconstructed)lines.add("Harga item lama memakai harga sumber terkini; bukan bukti harga saat transaksi.");
        if(reconstructedTotal!=order.path("total").asLong())lines.add("SELISIH DATA LAMA: rincian "+Ui.money(reconstructedTotal)+"; total historis dipertahankan.");
        if(!"Lunas".equals(order.path("payment_status").asText()))lines.add("BELUM LUNAS — nota ini bukan bukti pembayaran berhasil.");
        lines.add(settings.path("receipt_footer").asText("Terima kasih."));
        return String.join("\n",lines);
    }
}
