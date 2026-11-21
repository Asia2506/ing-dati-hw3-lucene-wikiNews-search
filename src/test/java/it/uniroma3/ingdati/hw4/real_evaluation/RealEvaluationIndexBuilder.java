package it.uniroma3.ingdati.hw4.real_evaluation;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.ingestion.NdjsonArticleReader;
import it.uniroma3.ingdati.hw4.ingestion.extractor.MainSectionJsoupExtractor;
import it.uniroma3.ingdati.hw4.ingestion.filter.MetadataPreFilterStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.MinContentLengthFilterStrategy;
import it.uniroma3.ingdati.hw4.model.NewsArticle;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Rebuilds an isolated Lucene index from the same filtered WikiNews dump as the application. */
public final class RealEvaluationIndexBuilder {

    public static final int DEFAULT_MIN_CONTENT_LENGTH = 50;

    public IndexBuildSummary build(Path dumpPath, Path indexPath, Analyzer analyzer) throws IOException {
        if (!Files.isRegularFile(dumpPath)) {
            throw new IOException("WikiNews NDJSON dump not found: " + dumpPath.toAbsolutePath());
        }
        if (Files.exists(indexPath)) {
            throw new IOException("Evaluation index path must not already exist: " + indexPath.toAbsolutePath());
        }

        long inputLines;
        try (Stream<String> lines = Files.lines(dumpPath)) {
            inputLines = lines.count();
        }

        NdjsonArticleReader articleReader = new NdjsonArticleReader(
                new MetadataPreFilterStrategy(),
                new MainSectionJsoupExtractor(),
                new MinContentLengthFilterStrategy(DEFAULT_MIN_CONTENT_LENGTH)
        );
        List<NewsArticle> articles = articleReader.readArticles(dumpPath);

        Files.createDirectories(indexPath);
        int indexedCount = new WikiNewsIndexer(indexPath, analyzer).indexArticles(articles);
        int documentCount;
        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            documentCount = reader.numDocs();
        }

        return new IndexBuildSummary(inputLines, articles.size(), indexedCount, documentCount);
    }

    public record IndexBuildSummary(long inputLines, int acceptedArticles, int indexedOperations, int documentsInIndex) {
    }
}
