/*
 * ItemLeilao.java — Representa um item em leilão.
 * Guarda o nome, a descrição e o lance mínimo inicial.
 */
public class ItemLeilao {
    private final String nome;
    private final String descricao;
    private final double lanceMinimo;

    public ItemLeilao(String nome, String descricao, double lanceMinimo) {
        this.nome = nome;
        this.descricao = descricao;
        this.lanceMinimo = lanceMinimo;
    }

    public String getNome()         { return nome; }
    public String getDescricao()    { return descricao; }
    public double getLanceMinimo()  { return lanceMinimo; }
}