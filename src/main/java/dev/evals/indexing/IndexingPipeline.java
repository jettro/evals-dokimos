package dev.evals.indexing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexingPipeline {

    private final LuceneDatastore luceneDatastore;

    @Value("classpath:data/whisky.csv")
    private Resource csvResource;

    public IndexingPipeline(LuceneDatastore luceneDatastore) {
        this.luceneDatastore = luceneDatastore;
    }

    public void runIndexing(boolean recreate) throws IOException {
        List<Document> documents = readCsv(csvResource);
        luceneDatastore.indexSpringAiDocuments(documents, recreate);
    }

    private List<Document> readCsv(Resource resource) throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        
        com.fasterxml.jackson.databind.MappingIterator<Map<String, String>> it = mapper.readerFor(new TypeReference<Map<String, String>>() {})
                .with(schema)
                .readValues(resource.getInputStream());
        List<Map<String, String>> rows = it.readAll();
        
        return rows.stream().map(row -> {
            String content = row.getOrDefault("name", "") + " " + row.getOrDefault("tags", "");
            Map<String, Object> metadata = new HashMap<>(row);
            return new Document(content, metadata);
        }).toList();
    }
}
