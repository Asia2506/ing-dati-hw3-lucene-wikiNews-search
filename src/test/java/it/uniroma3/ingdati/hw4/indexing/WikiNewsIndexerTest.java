package it.uniroma3.ingdati.hw4.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Test di integrazione mirato per WikiNewsAnalyzerFactory e WikiNewsIndexer (Fase 2: L2 + D2).
class WikiNewsIndexerTest {

    @TempDir
    Path tempIndexDir;

    private Analyzer analyzer;
    private WikiNewsIndexer indexer;

    @BeforeEach
    void setUp() {
        WikiNewsAnalyzerFactory factory = new WikiNewsAnalyzerFactory();
        this.analyzer = factory.createAnalyzer();
        this.indexer = new WikiNewsIndexer(tempIndexDir, this.analyzer);
    }

    @Test
    void indexArticles_shouldIndexFieldsCategoriesAndDerivedYear() throws IOException {
        NewsArticle article1 = new NewsArticle(
                10L,
                "Elezioni e riforme istituzionali",
                "https://it.wikinews.org/wiki/Elezioni",
                Instant.parse("2023-06-15T12:00:00Z"),
                Instant.parse("2023-06-16T08:00:00Z"),
                List.of("Politica", "Italia"),
                "Il parlamento ha discusso oggi le nuove riforme istituzionali."
        );

        // Qui dateCreated è nullo: effectiveYear() deve ricavare 2020 da dateModified.
        NewsArticle article2 = new NewsArticle(
                20L,
                "Accordo europeo sull'energia",
                "https://it.wikinews.org/wiki/Energia",
                null,
                Instant.parse("2020-11-01T11:32:27Z"),
                List.of("Economia"),
                "Nuova intesa europea sulle reti energetiche condivise."
        );

        int count = indexer.indexArticles(List.of(article1, article2));
        assertEquals(2, count);

        // Apre l'indice su disco in sola lettura per verificare i documenti indicizzati.
        try (Directory dir = FSDirectory.open(tempIndexDir);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            assertEquals(2, reader.numDocs());
            IndexSearcher searcher = new IndexSearcher(reader);

            // 1. Ricerca esatta su categoria tramite KeywordAnalyzer.
            Query catQuery = new TermQuery(
                    new Term(WikiNewsAnalyzerFactory.FIELD_CATEGORIES, "Politica")
            );
            TopDocs catHits = searcher.search(catQuery, 10);
            assertEquals(1, catHits.totalHits.value);

            Document foundDoc = searcher.storedFields().document(catHits.scoreDocs[0].doc);
            assertNotNull(foundDoc);
            assertEquals("10", foundDoc.get(WikiNewsAnalyzerFactory.FIELD_IDENTIFIER));
            assertEquals("Elezioni e riforme istituzionali", foundDoc.get(WikiNewsAnalyzerFactory.FIELD_TITLE));
            assertEquals("https://it.wikinews.org/wiki/Elezioni", foundDoc.get(WikiNewsAnalyzerFactory.FIELD_URL));
            assertEquals("2023", foundDoc.get(WikiNewsAnalyzerFactory.FIELD_YEAR));

            // 2. Ricerca per anno derivato (2020, ottenuto da dateModified per article2).
            Query yearQuery = IntPoint.newExactQuery(WikiNewsAnalyzerFactory.FIELD_YEAR, 2020);
            TopDocs yearHits = searcher.search(yearQuery, 10);
            assertEquals(1, yearHits.totalHits.value);

            Document doc2020 = searcher.storedFields().document(yearHits.scoreDocs[0].doc);
            assertEquals("20", doc2020.get(WikiNewsAnalyzerFactory.FIELD_IDENTIFIER));
            assertTrue(doc2020.get(WikiNewsAnalyzerFactory.FIELD_CONTENT).contains("reti energetiche"));
        }
    }
}