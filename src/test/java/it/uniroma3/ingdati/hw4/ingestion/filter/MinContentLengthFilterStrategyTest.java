package it.uniroma3.ingdati.hw4.ingestion.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Test unitari per il filtro post-parsing sulla lunghezza minima del testo (MinContentLengthFilterStrategy).
class MinContentLengthFilterStrategyTest {

    @Test
    void accept_shouldReturnTrueWhenContentMeetsMinimumLength() {
        MinContentLengthFilterStrategy filter = new MinContentLengthFilterStrategy(10);
        NewsArticle article = createArticleWithContent("Questo testo ha piu di dieci caratteri.");

        assertTrue(filter.accept(article));
    }

    @Test
    void accept_shouldReturnFalseWhenContentIsShorterThanMinimumLength() {
        MinContentLengthFilterStrategy filter = new MinContentLengthFilterStrategy(20);
        NewsArticle article = createArticleWithContent("Testo breve");

        assertFalse(filter.accept(article));
    }

    @Test
    void accept_shouldReturnFalseWhenContentIsNull() {
        MinContentLengthFilterStrategy filter = new MinContentLengthFilterStrategy(5);
        NewsArticle article = createArticleWithContent(null);

        assertFalse(filter.accept(article));
    }

    @Test
    void constructor_shouldThrowWhenMinLengthIsNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new MinContentLengthFilterStrategy(-1);
            }
        });

        assertEquals("minLength must be >= 0", ex.getMessage());
    }

    // Metodo di supporto interno per creare velocemente un NewsArticle di test.
    private NewsArticle createArticleWithContent(String content) {
        return new NewsArticle(
                1L,
                "Titolo test",
                "https://it.wikinews.org/wiki/Test",
                Instant.parse("2024-01-01T00:00:00Z"),
                null,
                List.of("Cronaca"),
                content
        );
    }
}