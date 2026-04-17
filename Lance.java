/*
 * Lance.java — Representa um lance dado em um leilão:
 * autor (quem enviou), valor, e o momento em que foi feito.
 */
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Lance {
    private final String autor;
    private final double valor;
    private final LocalDateTime timestamp;

    public Lance(String autor, double valor) {
        this.autor = autor;
        this.valor = valor;
        this.timestamp = LocalDateTime.now();
    }

    public String getAutor()             { return autor; }
    public double getValor()             { return valor; }
    public LocalDateTime getTimestamp()  { return timestamp; }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        return String.format("[%s] %-15s R$ %.2f",
                timestamp.format(fmt), autor, valor);
    }
}