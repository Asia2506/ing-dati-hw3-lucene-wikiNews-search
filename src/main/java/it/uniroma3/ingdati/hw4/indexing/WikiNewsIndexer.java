package it.uniroma3.ingdati.hw4.indexing;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Indicizza una collezione di NewsArticle dentro un indice Apache Lucene (scelte L2 + D2).
public class WikiNewsIndexer {

    private final Path indexPath;
    private final Analyzer analyzer;

    public WikiNewsIndexer(Path indexPath, Analyzer analyzer) {
        this.indexPath = indexPath;
        this.analyzer = analyzer;
    }

    // Indicizza tutti gli articoli passati in ingresso e rende le modifiche visibili con commit().
    public int indexArticles(List<NewsArticle> articles) throws IOException {
        int indexedCount = 0;

        // FSDirectory apre l'indice su disco nella cartella indexPath.
        // IndexWriterConfig configura l'analyzer e la modalità di creazione/apertura (CREATE_OR_APPEND).
        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {

            if (articles != null) {
                for (NewsArticle article : articles) {
                    if (article == null) {
                        continue;
                    }
                    Document doc = toDocument(article);
                    // updateDocument aggiorna se esiste già lo stesso identifier, oppure inserisce nuovo.
                    writer.updateDocument(
                            new Term(WikiNewsAnalyzerFactory.FIELD_IDENTIFIER, Long.toString(article.identifier())),
                            doc
                    );
                    indexedCount++;
                }
            }

            // Salva su disco tutti i segmenti scritti.
            writer.commit();
        }

        return indexedCount;
    }

    // Converte un NewsArticle in un Document Lucene con campi indicizzati, ordinabili/filtrabili e memorizzati.
    public Document toDocument(NewsArticle article) {
        Document doc = new Document();

        // 1. Identificativo numerico dell'articolo WikiNews
        String idStr = Long.toString(article.identifier());
        doc.add(new StringField(WikiNewsAnalyzerFactory.FIELD_IDENTIFIER, idStr, Field.Store.YES));

        // 2. Titolo (analizzato in italiano + memorizzato per visualizzazione)
        String safeTitle = (article.title() != null) ? article.title() : "";
        doc.add(new TextField(WikiNewsAnalyzerFactory.FIELD_TITLE, safeTitle, Field.Store.YES));

        // 3. URL originale (solo memorizzato, utile nella Web UI)
        String safeUrl = (article.url() != null) ? article.url() : "";
        doc.add(new StoredField(WikiNewsAnalyzerFactory.FIELD_URL, safeUrl));

        // 4. Testo ripulito (analizzato in italiano + memorizzato per highlighter/snippet)
        String safeContent = (article.content() != null) ? article.content() : "";
        doc.add(new TextField(WikiNewsAnalyzerFactory.FIELD_CONTENT, safeContent, Field.Store.YES));

        // 5. Categorie: StringField per match esatto, SortedSetDocValuesField codificato con FacetsConfig per faccette, StoredField per UI
        for (String category : article.categories()) {
            if (category == null || category.isBlank()) {
                continue;
            }
            String trimmedCat = category.trim();
            doc.add(new StringField(WikiNewsAnalyzerFactory.FIELD_CATEGORIES, trimmedCat, Field.Store.NO));
            String encodedFacetPath = FacetsConfig.pathToString(
                    WikiNewsAnalyzerFactory.FIELD_CATEGORIES,
                    trimmedCat
            );
            doc.add(new SortedSetDocValuesField(
                    WikiNewsAnalyzerFactory.FIELD_CATEGORIES,
                    new BytesRef(encodedFacetPath)
            ));
            doc.add(new StoredField(WikiNewsAnalyzerFactory.FIELD_CATEGORIES, trimmedCat));
        }

        // 6. Date in epoch seconds (LongPoint per range query, SortedNumericDocValuesField per sort, StoredField per lettura)
        addInstantFields(
                doc,
                article.dateCreated(),
                WikiNewsAnalyzerFactory.FIELD_DATE_CREATED
        );
        addInstantFields(
                doc,
                article.dateModified(),
                WikiNewsAnalyzerFactory.FIELD_DATE_MODIFIED
        );

        // 7. Campo derivato "year" (scelta D2) calcolato tramite article.effectiveYear()
        OptionalInt effectiveYear = article.effectiveYear();
        if (effectiveYear.isPresent()) {
            int yearValue = effectiveYear.getAsInt();
            doc.add(new IntPoint(WikiNewsAnalyzerFactory.FIELD_YEAR, yearValue));
            doc.add(new SortedNumericDocValuesField(WikiNewsAnalyzerFactory.FIELD_YEAR, yearValue));
            doc.add(new StoredField(WikiNewsAnalyzerFactory.FIELD_YEAR, yearValue));
        }

        return doc;
    }

    // Aggiunge i campi numerici per un Instant (se presente) sotto il nome base indicato.
    private void addInstantFields(Document doc, Instant instant, String fieldName) {
        if (instant == null) {
            return;
        }
        long epochSeconds = instant.getEpochSecond();
        doc.add(new LongPoint(fieldName, epochSeconds));
        doc.add(new SortedNumericDocValuesField(fieldName, epochSeconds));
        doc.add(new StoredField(fieldName, epochSeconds));
    }
}