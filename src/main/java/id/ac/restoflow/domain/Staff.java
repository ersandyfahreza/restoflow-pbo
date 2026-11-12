package id.ac.restoflow.domain;

/** Identitas staf disembunyikan; subtype merepresentasikan keanggotaan peran yang nyata. */
public abstract sealed class Staff permits KitchenStaff,Courier {
    private final int id;
    private final String name;
    private final boolean active;
    protected Staff(int id,String name,boolean active){
        if(id<=0||name==null||name.isBlank())throw new IllegalArgumentException("Identitas staf tidak valid.");
        this.id=id;this.name=name;this.active=active;
    }
    public final int id(){return id;}
    public final String name(){return name;}
    public final boolean active(){return active;}
    public abstract String roleLabel();
    public final String displayLabel(){return name+" · "+roleLabel()+(active?"":" (arsip)");}
}
