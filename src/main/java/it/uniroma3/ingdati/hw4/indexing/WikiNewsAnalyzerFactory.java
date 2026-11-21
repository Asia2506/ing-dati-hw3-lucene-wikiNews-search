package it.uniroma3.ingdati.hw4.indexing;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;

// Costruisce l'Analyzer composito di Lucene (scelta L2):
// - ItalianAnalyzer di default per campi testuali in italiano (title, content)
// - KeywordAnalyzer per "categories" così ogni categoria resta un token indivisibile
public class WikiNewsAnalyzerFactory {

    public static final String FIELD_IDENTIFIER = "identifier";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_URL = "url";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_CATEGORIES = "categories";
    public static final String FIELD_DATE_CREATED = "date_created_epoch";
    public static final String FIELD_DATE_MODIFIED = "date_modified_epoch";
    public static final String FIELD_YEAR = "year";

    // Crea e restituisce il PerFieldAnalyzerWrapper configurato per lo schema di WikiNews.
    public Analyzer createAnalyzer() {
        // Analyzer di default per i campi testuali (title, content, ecc.).
        Analyzer defaultAnalyzer = new ItalianAnalyzer();

        // Mappa campo -> analyzer specifico per i campi che richiedono trattamento diverso.
        Map<String, Analyzer> perFieldAnalyzers = new HashMap<>();
        perFieldAnalyzers.put(FIELD_CATEGORIES, new KeywordAnalyzer());

        return new PerFieldAnalyzerWrapper(defaultAnalyzer, perFieldAnalyzers);
    }
}