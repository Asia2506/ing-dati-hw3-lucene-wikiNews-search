package it.uniroma3.ingdati.hw4.config;

import java.nio.file.Path;

import org.apache.lucene.analysis.Analyzer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.ingestion.NdjsonArticleReader;
import it.uniroma3.ingdati.hw4.ingestion.extractor.HtmlExtractorStrategy;
import it.uniroma3.ingdati.hw4.ingestion.extractor.MainSectionJsoupExtractor;
import it.uniroma3.ingdati.hw4.ingestion.filter.MetadataPreFilterStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.MinContentLengthFilterStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.ParsedArticleFilterStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.RawArticleFilterStrategy;
import it.uniroma3.ingdati.hw4.search.NewsQueryBuilder;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;

// Configurazione Spring per collegare i componenti di ingestione, indicizzazione e ricerca.
@Configuration
public class WikiNewsAppConfig {

    @Bean
    public Analyzer wikiNewsAnalyzer() {
        WikiNewsAnalyzerFactory factory = new WikiNewsAnalyzerFactory();
        return factory.createAnalyzer();
    }

    @Bean
    public RawArticleFilterStrategy rawArticleFilterStrategy() {
        return new MetadataPreFilterStrategy();
    }

    @Bean
    public HtmlExtractorStrategy htmlExtractorStrategy() {
        return new MainSectionJsoupExtractor();
    }

    @Bean
    public ParsedArticleFilterStrategy parsedArticleFilterStrategy(
            @Value("${wikinews.min-content-length:50}") int minContentLength
    ) {
        return new MinContentLengthFilterStrategy(minContentLength);
    }

    @Bean
    public NdjsonArticleReader ndjsonArticleReader(
            RawArticleFilterStrategy rawFilter,
            HtmlExtractorStrategy htmlExtractor,
            ParsedArticleFilterStrategy parsedFilter
    ) {
        return new NdjsonArticleReader(rawFilter, htmlExtractor, parsedFilter);
    }

    @Bean
    public WikiNewsIndexer wikiNewsIndexer(
            @Value("${wikinews.index-path:index}") String indexPathStr,
            Analyzer wikiNewsAnalyzer
    ) {
        return new WikiNewsIndexer(Path.of(indexPathStr), wikiNewsAnalyzer);
    }

    @Bean
    public NewsQueryBuilder newsQueryBuilder(Analyzer wikiNewsAnalyzer) {
        return new NewsQueryBuilder(wikiNewsAnalyzer);
    }

    @Bean
    public WikiNewsSearchService wikiNewsSearchService(
            @Value("${wikinews.index-path:index}") String indexPathStr,
            Analyzer wikiNewsAnalyzer,
            NewsQueryBuilder newsQueryBuilder
    ) {
        return new WikiNewsSearchService(Path.of(indexPathStr), wikiNewsAnalyzer, newsQueryBuilder);
    }
}