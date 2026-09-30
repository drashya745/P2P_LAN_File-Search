package com.lansearch;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser; // NEW IMPORT
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileSearcher {
    public List<String> search(String queryStr) {
        List<String> results = new ArrayList<>();
        try {
            DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(FileIndexer.INDEX_DIR)));
            IndexSearcher searcher = new IndexSearcher(reader);
            
            // CHANGED: Tell Lucene to look in both the content AND the filename buckets
            String[] fieldsToSearch = {"content", "filename"};
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fieldsToSearch, new StandardAnalyzer());
            
            Query query = parser.parse(queryStr);

            ScoreDoc[] hits = searcher.search(query, 10).scoreDocs;
            for (ScoreDoc hit : hits) {
                Document doc = searcher.storedFields().document(hit.doc);
                results.add(doc.get("filename"));
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}