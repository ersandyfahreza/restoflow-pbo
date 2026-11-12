package id.ac.restoflow.domain;

public final class KitchenStaff extends Staff {
    public KitchenStaff(int id,String name,boolean active){super(id,name,active);}
    @Override public String roleLabel(){return "Dapur";}
}
