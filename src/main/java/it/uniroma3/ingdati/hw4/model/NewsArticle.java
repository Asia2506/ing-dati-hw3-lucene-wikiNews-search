package it.uniroma3.ingdati.hw4.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;

//record = contenitore immutabile di dati (Information Expert sui campi della notizia)
public record NewsArticle(
        long identifier,          // ID numerico della pagina WikiNews (es. 48882)
        String title,             // Titolo della notizia ("name" nel JSON)
        String url,               // Link alla pagina WikiNews originale (utile in Fase 4 quando apri l'articolo)
        Instant dateCreated,      // Timestamp ISO-8601 di creazione ("date_created"), può essere null
        Instant dateModified,     // Timestamp ISO-8601 di ultima modifica ("date_modified")
        List<String> categories,  // Lista delle categorie tematiche (es. "Politica", "Belgio")
        String content            // Testo ripulito da Jsoup secondo la strategia A1
) {

    //Costruttore compatto: eseguito prima dell'assegnazione finale ai campi del record
    public NewsArticle {
        // Evita null e impedisce modifiche esterne: se qualcuno altera la lista originale fuori,
        // il record resta protetto grazie a List.copyOf(...)
        categories = (categories == null) ? List.of() : List.copyOf(categories);
    }

    //Metodo di dominio basato sui campi interni del record (coerente con la scelta D2 sul campo "year")
    public OptionalInt effectiveYear() {
        // Se manca dateCreated, ripiega su dateModified (utile su articoli del dump dove date_created è assente)
        Instant referenceDate = (dateCreated != null) ? dateCreated : dateModified;

        // Se non c'è nessuna data valida, restituisce un OptionalInt vuoto invece di un valore fittizio tipo 0 o -1
        if (referenceDate == null) {
            return OptionalInt.empty();
        }

        // Converte in UTC prima di estrarre l'anno così il risultato non dipende dal fuso orario locale
        return OptionalInt.of(referenceDate.atZone(ZoneOffset.UTC).getYear());
    }
}