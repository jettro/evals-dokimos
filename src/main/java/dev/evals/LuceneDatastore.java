package dev.evals;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LuceneDatastore implements DocumentWriter {

    @Value("${lucene.index.path:lucene-index}")
    private String indexPath;

    private final Analyzer analyzer = new StandardAnalyzer();

    @Override
    public void accept(List<Document> documents) {
        try {
            indexSpringAiDocuments(documents, false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index documents", e);
        }
    }

    public void indexSpringAiDocuments(List<Document> documents, boolean recreate) throws IOException {
        Directory directory = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        if (recreate) {
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        } else {
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        }

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Document springAiDoc : documents) {
                org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
                
                // Add content
                luceneDoc.add(new TextField("content", springAiDoc.getText(), Field.Store.YES));
                
                // Add metadata
                for (Map.Entry<String, Object> entry : springAiDoc.getMetadata().entrySet()) {
                    String key = entry.getKey();
                    String value = String.valueOf(entry.getValue());
                    // Heuristic: name and tags are likely important for searching
                    if (key.equalsIgnoreCase("name") || key.equalsIgnoreCase("tags")) {
                        luceneDoc.add(new TextField(key, value, Field.Store.YES));
                    } else {
                        luceneDoc.add(new StringField(key, value, Field.Store.YES));
                    }
                }
                
                writer.addDocument(luceneDoc);
            }
            writer.commit();
        }
    }

    public SearchResponse search(SearchRequest request) throws Exception {
        System.out.println("SEARCHING: " + request.query());
        Directory directory = FSDirectory.open(Paths.get(indexPath));
        if (!DirectoryReader.indexExists(directory)) {
            return new SearchResponse(List.of());
        }

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", analyzer);
            Query query = parser.parse(request.query());

            TopDocs topDocs = searcher.search(query, request.topK());
            List<SearchResponse.SearchResult> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document luceneDoc = searcher.doc(scoreDoc.doc);
                Map<String, Object> metadata = new HashMap<>();
                for (IndexableField field : luceneDoc.getFields()) {
                    metadata.put(field.name(), field.stringValue());
                }
                results.add(new SearchResponse.SearchResult(luceneDoc.get("content"), metadata, scoreDoc.score));
            }

            return new SearchResponse(results);
        }
    }

    public int count() throws IOException {
        IndexInfo info = getIndexInfo();
        return info.numDocs();
    }

    public IndexInfo getIndexInfo() throws IOException {
        Directory directory = FSDirectory.open(Paths.get(indexPath));
        if (!DirectoryReader.indexExists(directory)) {
            return new IndexInfo(0, 0, 0, false);
        }

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return new IndexInfo(
                    reader.numDocs(),
                    reader.maxDoc(),
                    reader.numDeletedDocs(),
                    reader.hasDeletions()
            );
        }
    }
}
