package it.uniroma3.ingdati.hw4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

// Test unitari per il record NewsArticle (modello dati della Fase 1).
class NewsArticleTest {

    @Test
    void effectiveYear_shouldPreferDateCreatedWhenPresent() {
        // Prepara due date diverse: deve vincere dateCreated (2022).
        Instant created = Instant.parse("2022-05-10T10:00:00Z");
        Instant modified = Instant.parse("2024-01-15T08:30:00Z");

        NewsArticle article = new NewsArticle(
                100L,
                "Titolo prova",
                "https://it.wikinews.org/wiki/Prova",
                created,
                modified,
                List.of("Politica"),
                "Contenuto di prova abbastanza lungo."
        );

        OptionalInt year = article.effectiveYear();

        assertTrue(year.isPresent());
        assertEquals(2022, year.getAsInt());
    }

    @Test
    void effectiveYear_shouldFallbackToDateModifiedWhenDateCreatedIsNull() {
        // Quando dateCreated manca, deve usare dateModified (2020).
        Instant modified = Instant.parse("2020-11-01T11:32:27Z");

        NewsArticle article = new NewsArticle(
                48882L,
                "Belgio",
                "https://it.wikinews.org/wiki/Belgio",
                null,
                modified,
                List.of("Belgio", "Politica"),
                "Testo dell'articolo sul Belgio."
        );

        OptionalInt year = article.effectiveYear();

        assertTrue(year.isPresent());
        assertEquals(2020, year.getAsInt());
    }

    @Test
    void effectiveYear_shouldReturnEmptyWhenBothDatesAreNull() {
        // Senza date valide, l'anno effettivo deve essere assente (OptionalInt.empty()).
        NewsArticle article = new NewsArticle(
                1L,
                "Senza data",
                "https://it.wikinews.org/wiki/Senza_data",
                null,
                null,
                List.of(),
                "Testo senza data."
        );

        OptionalInt year = article.effectiveYear();

        assertTrue(year.isEmpty());
    }

    @Test
    void categories_shouldBeImmutableAndDefensivelyCopied() {
        // Lista mutabile passata dall'esterno.
        List<String> sourceCategories = new ArrayList<>();
        sourceCategories.add("Cronaca");

        NewsArticle article = new NewsArticle(
                10L,
                "Articolo cronaca",
                "https://it.wikinews.org/wiki/Cronaca",
                Instant.parse("2023-03-01T12:00:00Z"),
                null,
                sourceCategories,
                "Contenuto cronaca."
        );

        // Modificare la lista originale fuori dal record non deve alterare article.categories().
        sourceCategories.add("ModificaEsterna");
        assertEquals(1, article.categories().size());
        assertEquals("Cronaca", article.categories().get(0));

        // Provare a modificare direttamente article.categories() deve lanciare UnsupportedOperationException.
        assertThrows(UnsupportedOperationException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                article.categories().add("AltraCategoria");
            }
        });
    }
}