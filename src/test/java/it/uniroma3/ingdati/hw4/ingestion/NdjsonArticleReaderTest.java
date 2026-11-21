package it.uniroma3.ingdati.hw4.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.uniroma3.ingdati.hw4.ingestion.extractor.MainSectionJsoupExtractor;
import it.uniroma3.ingdati.hw4.ingestion.filter.MetadataPreFilterStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.MinContentLengthFilterStrategy;
import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Test di integrazione mirato per NdjsonArticleReader (lettura NDJSON + pre-filtro + Jsoup + post-filtro + pulizia categorie).
class NdjsonArticleReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readArticles_shouldFilterAndExtractValidArticlesFromNdjson() throws IOException {
        // Riga 1: articolo valido con rumore HTML dentro section[data-mw-section-id=0].
        String validLine = """
                {"identifier":101,"name":"Nuova scoperta scientifica","url":"https://it.wikinews.org/wiki/Test1","date_created":"2024-03-10T09:00:00Z","date_modified":"2024-03-10T10:00:00Z","categories":["Scienza","Categoria:Tecnologia"],"article_body":{"html":"<section data-mw-section-id=\\"0\\"><div id=\\"commentrequest\\">Commenti</div><p>I ricercatori hanno annunciato oggi un risultato importante nel campo della robotica e dell'intelligenza artificiale applicata.</p></section>"}}
                """;

        // Riga 2: scartata dal filtro preventivo per categoria esclusa.
        String excludedCategoryLine = """
                {"identifier":102,"name":"Pagina da rimuovere","url":"https://it.wikinews.org/wiki/Test2","date_created":"2024-03-10T09:00:00Z","categories":["Da cancellare subito"],"article_body":{"html":"<section data-mw-section-id=\\"0\\"><p>Testo lungo abbastanza ma categoria esclusa.</p></section>"}}
                """;

        // Riga 3: scartata dal filtro post-parsing perché il testo ripulito è troppo corto (< 30 caratteri).
        String shortContentLine = """
                {"identifier":103,"name":"Articolo troppo breve","url":"https://it.wikinews.org/wiki/Test3","date_created":"2024-03-10T09:00:00Z","categories":["Cronaca"],"article_body":{"html":"<section data-mw-section-id=\\"0\\"><p>Ciao</p></section>"}}
                """;

        Path ndjsonFile = tempDir.resolve("sample.ndjson");
        String fileContent = validLine.trim() + System.lineSeparator()
                + excludedCategoryLine.trim() + System.lineSeparator()
                + shortContentLine.trim() + System.lineSeparator();
        Files.writeString(ndjsonFile, fileContent, StandardCharsets.UTF_8);

        NdjsonArticleReader reader = new NdjsonArticleReader(
                new MetadataPreFilterStrategy(),
                new MainSectionJsoupExtractor(),
                new MinContentLengthFilterStrategy(30)
        );

        List<NewsArticle> result = reader.readArticles(ndjsonFile);

        assertEquals(1, result.size());
        NewsArticle article = result.get(0);
        assertEquals(101L, article.identifier());
        assertEquals("Nuova scoperta scientifica", article.title());
        assertEquals(Instant.parse("2024-03-10T09:00:00Z"), article.dateCreated());
        assertEquals(List.of("Scienza", "Tecnologia"), article.categories());
        assertEquals(
                "I ricercatori hanno annunciato oggi un risultato importante nel campo della robotica e dell'intelligenza artificiale applicata.",
                article.content()
        );
    }

    @Test
    void readArticles_shouldCleanCategoriaObjectsAndDropDatesOrServiceCategories() throws IOException {
        String lineWithCategoriaField = """
                {"identifier":201,"name":"Partita di rugby a Roma","url":"https://it.wikinews.org/wiki/Test201","date_created":"2006-01-13T12:00:00Z","categoria":[{"name":"Categoria:Sport"},{"name":"Categoria:Rugby_italiano"},{"name":"Categoria:Pubblicati"},{"name":"Categoria:Articoli_archiviati"},{"name":"Categoria:Notizie_da_prima_pagina"},{"name":"Categoria:13_gennaio_2006"}],"article_body":{"html":"<section data-mw-section-id=\\"0\\"><p>Si è svolta oggi nella capitale una partita intensa tra due squadre del campionato nazionale.</p></section>"}}
                """;

        Path ndjsonFile = tempDir.resolve("categoria_sample.ndjson");
        Files.writeString(ndjsonFile, lineWithCategoriaField.trim() + System.lineSeparator(), StandardCharsets.UTF_8);

        NdjsonArticleReader reader = new NdjsonArticleReader(
                new MetadataPreFilterStrategy(),
                new MainSectionJsoupExtractor(),
                new MinContentLengthFilterStrategy(20)
        );

        List<NewsArticle> result = reader.readArticles(ndjsonFile);

        assertEquals(1, result.size());
        assertEquals(List.of("Sport", "Rugby italiano"), result.get(0).categories());
    }
}