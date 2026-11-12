package id.ac.restoflow.domain;

public final class Courier extends Staff {
    public Courier(int id,String name,boolean active){super(id,name,active);}
    @Override public String roleLabel(){return "Kurir";}
}
