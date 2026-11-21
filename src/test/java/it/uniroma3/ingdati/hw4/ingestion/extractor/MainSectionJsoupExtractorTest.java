package it.uniroma3.ingdati.hw4.ingestion.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Test unitari per MainSectionJsoupExtractor (strategia A1: estrazione sezione principale e pulizia rumore).
class MainSectionJsoupExtractorTest {

    private MainSectionJsoupExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MainSectionJsoupExtractor();
    }

    @Test
    void extractCleanText_shouldExtractOnlyMainSectionAndRemoveNoise() {
        // Simula un frammento HTML di WikiNews con sezione principale (id=0),
        // elementi di disturbo (#commentrequest, table.messagebox, table.avviso, figure)
        // e una seconda sezione (id=1) che non deve entrare nel testo principale.
        String rawHtml = """
                <div class="mw-parser-output">
                    <section data-mw-section-id="0">
                        <div id="commentrequest">Richiesta commenti da rimuovere</div>
                        <table class="messagebox"><tr><td>Box messaggio da rimuovere</td></tr></table>
                        <table class="avviso"><tr><td>Avviso da rimuovere</td></tr></table>
                        <figure><img src="foto.jpg" alt="didascalia"/><figcaption>Didascalia figura</figcaption></figure>
                        <p>Il governo ha approvato oggi la nuova legge sul digitale.</p>
                        <p>Il provvedimento entrera in vigore il mese prossimo.</p>
                    </section>
                    <section data-mw-section-id="1">
                        <h2>Fonti</h2>
                        <p>Testo di un'altra sezione da non includere.</p>
                    </section>
                </div>
                """;

        String cleanText = extractor.extractCleanText(rawHtml);

        assertTrue(cleanText.contains("Il governo ha approvato oggi la nuova legge sul digitale."));
        assertTrue(cleanText.contains("Il provvedimento entrera in vigore il mese prossimo."));

        assertFalse(cleanText.contains("Richiesta commenti da rimuovere"));
        assertFalse(cleanText.contains("Box messaggio da rimuovere"));
        assertFalse(cleanText.contains("Avviso da rimuovere"));
        assertFalse(cleanText.contains("Didascalia figura"));
        assertFalse(cleanText.contains("Testo di un'altra sezione da non includere."));
    }

    @Test
    void extractCleanText_shouldReturnEmptyStringWhenInputIsNullOrBlank() {
        assertEquals("", extractor.extractCleanText(null));
        assertEquals("", extractor.extractCleanText("   \n\t  "));
    }
}