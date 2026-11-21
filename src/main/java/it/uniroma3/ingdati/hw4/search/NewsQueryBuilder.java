package it.uniroma3.ingdati.hw4.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;

// Costruisce la Query Lucene combinando ricerca testuale con boost sul titolo (Q2)
// e filtri opzionali per categoria e intervallo di anni.
public class NewsQueryBuilder {

    private static final float DEFAULT_TITLE_BOOST = 2.0f;
    private static final float DEFAULT_CONTENT_BOOST = 1.0f;

    private final Analyzer analyzer;
    private final float titleBoost;
    private final float contentBoost;

    public NewsQueryBuilder(Analyzer analyzer) {
        this(analyzer, DEFAULT_TITLE_BOOST, DEFAULT_CONTENT_BOOST);
    }

    public NewsQueryBuilder(Analyzer analyzer, float titleBoost, float contentBoost) {
        this.analyzer = analyzer;
        this.titleBoost = titleBoost;
        this.contentBoost = contentBoost;
    }

    // Costruisce la query composta a partire dai parametri di ricerca.
    public Query buildQuery(String queryText, String category, Integer minYear, Integer maxYear)
            throws ParseException {

        BooleanQuery.Builder booleanBuilder = new BooleanQuery.Builder();
        boolean hasAnyClause = false;

        // 1. Ricerca testuale su title (con boost) e content
        if (queryText != null && !queryText.isBlank()) {
            String escapedQuery = QueryParser.escape(queryText.trim());

            QueryParser titleParser = new QueryParser(WikiNewsAnalyzerFactory.FIELD_TITLE, analyzer);
            Query titleQuery = titleParser.parse(escapedQuery);
            Query boostedTitleQuery = new BoostQuery(titleQuery, titleBoost);

            QueryParser contentParser = new QueryParser(WikiNewsAnalyzerFactory.FIELD_CONTENT, analyzer);
            Query contentQuery = contentParser.parse(escapedQuery);
            Query boostedContentQuery = new BoostQuery(contentQuery, contentBoost);

            // Almeno uno tra titolo e contenuto deve corrispondere (SHOULD).
            BooleanQuery.Builder textClause = new BooleanQuery.Builder();
            textClause.add(boostedTitleQuery, BooleanClause.Occur.SHOULD);
            textClause.add(boostedContentQuery, BooleanClause.Occur.SHOULD);
            textClause.setMinimumNumberShouldMatch(1);

            booleanBuilder.add(textClause.build(), BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // 2. Filtro opzionale per categoria esatta (MUST)
        if (category != null && !category.isBlank()) {
            Query categoryQuery = new TermQuery(
                    new Term(WikiNewsAnalyzerFactory.FIELD_CATEGORIES, category.trim())
            );
            booleanBuilder.add(categoryQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // 3. Filtro opzionale per intervallo di anni su "year" (MUST)
        if (minYear != null || maxYear != null) {
            int lower = (minYear != null) ? minYear : Integer.MIN_VALUE;
            int upper = (maxYear != null) ? maxYear : Integer.MAX_VALUE;
            Query yearRangeQuery = IntPoint.newRangeQuery(WikiNewsAnalyzerFactory.FIELD_YEAR, lower, upper);
            booleanBuilder.add(yearRangeQuery, BooleanClause.Occur.MUST);
            hasAnyClause = true;
        }

        // Se non sono stati specificati né testo né filtri, restituisce tutti i documenti.
        if (!hasAnyClause) {
            return new MatchAllDocsQuery();
        }

        return booleanBuilder.build();
    }
}