package it.uniroma3.ingdati.hw4.ingestion.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

// Strategia A1: estrae la sezione principale dell'articolo e ripulisce il rumore HTML con Jsoup.
public class MainSectionJsoupExtractor implements HtmlExtractorStrategy {

    // Selettore CSS della sezione principale del corpo notizia in MediaWiki / Parsoid.
    private static final String MAIN_SECTION_SELECTOR = "section[data-mw-section-id=0]";

    // Selettori degli elementi di disturbo da rimuovere prima di estrarre il testo.
    private static final String NOISE_SELECTOR =
            "#commentrequest, table.messagebox, table.avviso, figure";

    @Override
    public String extractCleanText(String rawHtml) {
        // Se non c'è HTML, restituisce stringa vuota per evitare errori.
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        // Trasforma la stringa HTML in un albero DOM navigabile con Jsoup.
        Document doc = Jsoup.parse(rawHtml);

        // Cerca la sezione principale; se non è presente, ripiega sull'intero <body>.
        Element mainSection = doc.selectFirst(MAIN_SECTION_SELECTOR);
        Element targetContainer = (mainSection != null) ? mainSection : doc.body();

        if (targetContainer == null) {
            return "";
        }

        // Cerca tutti i nodi di rumore dentro la sezione scelta e li rimuove dal DOM.
        Elements noiseElements = targetContainer.select(NOISE_SELECTOR);
        noiseElements.remove();

        // Estrae il testo leggibile ripulito e normalizza eventuali spazi multipli/a capo.
        return targetContainer.text().replaceAll("\\s+", " ").trim();
    }
}