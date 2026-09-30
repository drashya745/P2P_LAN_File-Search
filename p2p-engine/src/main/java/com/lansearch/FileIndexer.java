package com.lansearch;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;

import java.io.File;
import java.nio.file.Paths;

public class FileIndexer {
    public static final String INDEX_DIR = System.getProperty("user.home") + File.separator + "P2P_Index";
    public static final String DATA_DIR = System.getProperty("user.home") + File.separator + "P2P_Shared";

    // The traffic light: prevents WatchService from running this twice simultaneously
    private static final Object indexLock = new Object();

    public void indexFiles() {
        synchronized (indexLock) {
            try {
                Directory dir = FSDirectory.open(Paths.get(INDEX_DIR));
                IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
                IndexWriter writer = new IndexWriter(dir, config);
                writer.deleteAll(); // Clear the old index before rebuilding

                File folder = new File(DATA_DIR);
                File[] files = folder.listFiles();

                // If folder is empty or missing, safely close and exit
                if (files == null) {
                    writer.close();
                    return;
                }

                Tika tika = new Tika();
                for (File f : files) {
                    if (f.isFile()) {
                        Document doc = new Document();
                        
                        // CHANGED: TextField instead of StringField so the filename gets tokenized and becomes searchable
                        doc.add(new TextField("filename", f.getName(), Field.Store.YES));
                        
                        // Path stays as StringField because we don't want to tokenize file paths
                        doc.add(new StringField("path", f.getAbsolutePath(), Field.Store.YES));

                        try {
                            String content = tika.parseToString(f);
                            doc.add(new TextField("content", content, Field.Store.NO));
                        } catch (Exception e) {
                            System.out.println("Could not parse content for: " + f.getName());
                        }
                        writer.addDocument(doc);
                    }
                }
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}