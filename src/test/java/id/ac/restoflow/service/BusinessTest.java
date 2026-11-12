package id.ac.restoflow.service;

import id.ac.restoflow.domain.Cart;
import id.ac.restoflow.integration.Json;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BusinessTest {
    @Test void checkoutRequiresTableAndDeliveryAddress(){
        Cart c=new Cart();c.add(1,"Menu",100,1);
        assertThrows(IllegalArgumentException.class,()->OrderService.prepare(c,UUID.randomUUID(),"dine_in",null,"","",null,""));
        assertThrows(IllegalArgumentException.class,()->OrderService.prepare(c,UUID.randomUUID(),"delivery",null,"","",null,""));
    }
    @Test void checkoutDoesNotTrustClientPrices(){
        Cart c=new Cart();c.add(1,"Menu",999,1);var payload=OrderService.prepare(c,UUID.randomUUID(),"takeaway",null,"","",null,"");
        assertFalse(payload.path("items").get(0).has("price"));assertEquals(1,payload.path("items").get(0).path("quantity").asInt());
    }
    @Test void csvEscapesQuotesAndSpreadsheetFormulas(){
        assertEquals("\"'=SUM(A1)\"",CsvExport.cell("=SUM(A1)"));assertEquals("\"'  @test\"",CsvExport.cell("  @test"));
        assertTrue(CsvExport.cell("=SUM(A1)\nmultiline").startsWith("\"'="));
        assertEquals("\"a\"\"b,c\"",CsvExport.cell("a\"b,c"));assertTrue(CsvExport.render(List.of(List.of("Nama","Jumlah"))).startsWith("\ufeff"));
    }
    @Test void receiptPreservesLegacyTotalAndLabelsDiscrepancy(){
        var order=Json.read(""" 
            {"id":1,"total":75000,"payment_status":"Pending","items":[{"name":"Nasi","quantity":2,"price":25000,"reconstructed":true}]}
            """);String receipt=ReceiptService.render(order,Json.object());
        assertTrue(receipt.contains("SELISIH DATA LAMA"));assertTrue(receipt.contains("BELUM LUNAS"));assertTrue(receipt.contains("bukan bukti harga saat transaksi"));
    }
}
