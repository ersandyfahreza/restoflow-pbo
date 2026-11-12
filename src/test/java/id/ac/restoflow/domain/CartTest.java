package id.ac.restoflow.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CartTest {
    @Test void decreasingLastUnitRemovesLine(){Cart c=new Cart();c.add(1,"Nasi",25000,2);c.decrement(1);assertEquals(25000,c.total());c.decrement(1);assertTrue(c.isEmpty());c.decrement(1);assertEquals(0,c.total());}
    @Test void mergesSameMenuAndCalculatesExactRupiah(){Cart c=new Cart();c.add(1,"Nasi",25000,2);c.add(1,"Nasi",25000,1);c.add(2,"Teh",6000,1);assertEquals(81000,c.total());assertEquals(2,c.items().size());}
    @Test void invalidQuantitiesDoNotChangeCart(){Cart c=new Cart();c.add(1,"Nasi",25000,99);assertThrows(IllegalArgumentException.class,()->c.add(1,"Nasi",25000,1));assertEquals(2475000,c.total());assertThrows(IllegalArgumentException.class,()->c.add(2,"Teh",6000,-1));}
    @Test void invalidPriceRejected(){Cart c=new Cart();assertThrows(IllegalArgumentException.class,()->c.add(1,"Nasi",-1,1));assertThrows(IllegalArgumentException.class,()->c.add(1,"Nasi",1000000001L,1));}
    @Test void exportedItemsCannotMutateCart(){Cart c=new Cart();c.add(1,"Nasi",1,1);assertThrows(UnsupportedOperationException.class,()->c.items().clear());assertFalse(c.isEmpty());c.remove(1);assertTrue(c.isEmpty());}
}
