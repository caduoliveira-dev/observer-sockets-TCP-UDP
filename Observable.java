/*
 * Observable.java — Define a interface do "sujeito observado".
 * Exige três métodos: registrar (adicionar um observer),
 * remover (tirar um observer) e notificar (avisar todos os
 * observers que algo mudou).
 */
public interface Observable {
    void registrar(Observer o);
    void remover(Observer o);
    void notificar(String evento, String dados);
}