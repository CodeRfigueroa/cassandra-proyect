/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.kundera.index;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import com.impetus.kundera.Constants;
import com.impetus.kundera.cache.ElementCollectionCacheManager;
import com.impetus.kundera.metadata.model.EmbeddedColumn;
import com.impetus.kundera.metadata.model.EntityMetadata;
import com.impetus.kundera.property.PropertyAccessException;
import com.impetus.kundera.property.PropertyAccessorHelper;

/**
 * Provides indexing functionality using lucene library.
 * 
 * @author amresh.singh
 */
public class LuceneIndexer extends DocumentIndexer
{

    /** log for this class. */
    private static Log log = LogFactory.getLog(LuceneIndexer.class);

    /** The w. */
    private static IndexWriter w;

    /** The reader. */
    private static IndexReader reader;

    /** The index. */
    private static Directory index;

    /** The is initialized. */
    private static boolean isInitialized;

    /** The indexer. */
    private static LuceneIndexer indexer;

    /** The ready for commit. */
    private static boolean readyForCommit;

    /** The lucene dir path. */
    private static String luceneDirPath;

    /**
     * Instantiates a new lucene indexer.
     * 
     * @param analyzer
     *            the analyzer
     * @param lucDirPath
     *            the luc dir path
     */
    private LuceneIndexer(Analyzer analyzer, String lucDirPath)
    {
        super(analyzer);
        try
        {
            luceneDirPath = lucDirPath;
            File file = new File(luceneDirPath);
            if (file.exists())
            {
                Directory sourceDir = FSDirectory.open(getIndexDirectory());
                index = new RAMDirectory(sourceDir);
            }
            else
            {
                index = new RAMDirectory();
            }
            /*
             * FSDirectory.open(getIndexDirectory( ))
             */
            // isInitialized
            /* writer */
            w = new IndexWriter(index, new IndexWriterConfig(Version.LUCENE_34, analyzer));
            /* reader = */
            w.setMergePolicy(new LogDocMergePolicy());
            w.setMergeFactor(1000);
            w.getConfig().setRAMBufferSizeMB(32);
        }
        catch (CorruptIndexException e)
        {
            throw new LuceneIndexingException(e);
        }
        catch (LockObtainFailedException e)
        {
            throw new LuceneIndexingException(e);
        }
        catch (IOException e)
        {
            throw new LuceneIndexingException(e);
        }
    }

    /**
     * Gets the single instance of LuceneIndexer.
     * 
     * @param analyzer
     *            the analyzer
     * @param lucDirPath
     *            the luc dir path
     * @return single instance of LuceneIndexer
     */
    public static synchronized LuceneIndexer getInstance(Analyzer analyzer, String lucDirPath)
    {
        // super(analyzer);
        if (indexer == null && lucDirPath != null)
        {
            indexer = new LuceneIndexer(analyzer, lucDirPath);

        }
        return indexer;
    }

    /**
     * Added for HBase support.
     * 
     * @return default index writer
     */
    private IndexWriter getIndexWriter()
    {
        return w;
    }

    /**
     * Returns default index reader.
     * 
     * @return index reader.
     */
    private IndexReader getIndexReader()
    {
        flushInternal();

        if (reader == null)
        {
            try
            {
                if (!isInitialized)
                {
                    Directory sourceDir = FSDirectory.open(getIndexDirectory());
                    sourceDir.copy(sourceDir, index, true);
                    isInitialized = true;
                }
                reader = IndexReader.open(index, true);
            }
            catch (CorruptIndexException e)
            {
                throw new LuceneIndexingException(e);
            }
            catch (IOException e)
            {
                throw new LuceneIndexingException(e);
            }
        }
        return reader;
    }

    /**
     * Creates a Lucene index directory if it does not exist.
     * 
     * @return the index directory
     */
    private File getIndexDirectory()
    {
        File file = new File(luceneDirPath);

        if (!file.isDirectory())
        {
            file.mkdir();
        }
        return file;
    }

    @Override
    public final void index(EntityMetadata metadata, Object object)
    {
        indexDocument(metadata, object, null, null);
        onCommit();
    }

    @Override
    public final void unindex(EntityMetadata metadata, String id) throws LuceneIndexingException
    {
        log.debug("Unindexing @Entity[" + metadata.getEntityClazz().getName() + "] for key:" + id);
        try
        {
            /* String indexName, Query query, boolean autoCommit */
            getIndexWriter().deleteDocuments(new Term(KUNDERA_ID_FIELD, getKunderaId(metadata, id)));
        }
        catch (CorruptIndexException e)
        {
            throw new LuceneIndexingException(e);
        }
        catch (IOException e)
        {
            throw new LuceneIndexingException(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public final Map<String, String> search(String luceneQuery, int start, int count, boolean fetchRelation)
    {

        reader = getIndexReader();
        if (Constants.INVALID == count)
        {
            count = 100;
        }

        log.debug("Searching index with query[" + luceneQuery + "], start:" + start + ", count:" + count);

        // Set<String> entityIds = new HashSet<String>();
        Map<String, String> indexCol = new HashMap<String, String>();

        if (reader == null)
        {
            throw new LuceneIndexingException("Index reader is not initialized!");
        }

        IndexSearcher searcher = new IndexSearcher(reader);
        QueryParser qp = new QueryParser(Version.LUCENE_34, DEFAULT_SEARCHABLE_FIELD, new StandardAnalyzer(
                Version.LUCENE_34));

        try
        {
            qp.setLowercaseExpandedTerms(false);
            qp.setAllowLeadingWildcard(true);
            // qp.set
            Query q = qp.parse(luceneQuery);
            TopDocs docs = searcher.search(q, count);

            int nullCount = 0;
            // Assuming Supercol will be null in case if alias only.
            // This is a quick fix
            for (ScoreDoc sc : docs.scoreDocs)
            {
                Document doc = searcher.doc(sc.doc);
                String entityId = doc.get(fetchRelation ? PARENT_ID_FIELD : ENTITY_ID_FIELD);
                String superCol = doc.get(SUPERCOLUMN_INDEX);

                if (superCol == null)
                {
                    superCol = "SuperCol" + nullCount++;
                }
                // In case of super column and association.
                indexCol.put(superCol + "|" + entityId, entityId);
            }
        }
        catch (ParseException e)
        {
            throw new LuceneIndexingException("Error while parsing Lucene Query " + luceneQuery, e);
        }
        catch (IOException e)
        {
            throw new LuceneIndexingException(e);
        }

        reader = null;
        return indexCol;
    }

    /**
     * Indexes document in file system using lucene.
     * 
     * @param metadata
     *            the metadata
     * @param document
     *            the document
     */
    public void indexDocument(EntityMetadata metadata, Document document)
    {

        log.debug("Indexing document: " + document + " for " + metadata.getDBType() + " in file system using Lucene");

        IndexWriter w = getIndexWriter();
        try
        {
            // w.setR
            w.addDocument(document);
            // w.optimize();
            // w.commit();
            // w.close();
        }
        catch (CorruptIndexException e)
        {
            log.error("Error while indexing document " + document + " into Lucene. Details:" + e.getMessage());
            throw new LuceneIndexingException("Error while indexing document " + document + " into Lucene.", e);
        }
        catch (IOException e)
        {
            log.error("Error while indexing document " + document + " into Lucene. Details:" + e.getMessage());
            throw new LuceneIndexingException("Error while indexing document " + document + " into Lucene.", e);
        }
    }

    /**
     * Flush internal.
     */
    private void flushInternal()
    {
        try
        {
            if (w != null && readyForCommit)
            {
                w.commit();
                index.copy(index, FSDirectory.open(getIndexDirectory()), false);
                readyForCommit = false;
            }
        }

        catch (CorruptIndexException e)
        {
            log.error("Error while Flushing Lucene Indexes. Details:" + e.getMessage());
            throw new LuceneIndexingException("Error while Flushing Lucene Indexes", e);
        }
        catch (IOException e)
        {
            log.error("Error while Flushing Lucene Indexes" + e.getMessage());
            throw new LuceneIndexingException("Error while Flushing Lucene Indexes", e);
        }
    }

    /**
     * Close of transaction.
     */
    public void close()
    {
        try
        {
            if (w != null && readyForCommit)
            {
                w.commit();
                index.copy(index, FSDirectory.open(getIndexDirectory()), false);
            }
        }

        catch (CorruptIndexException e)
        {
            log.error("Error while closing lucene indexes. Details:" + e.getMessage());
            throw new LuceneIndexingException("Error while closing lucene indexes.", e);
        }
        catch (IOException e)
        {
            log.error("Error while closing lucene indexes. Details:" + e.getMessage());
            throw new LuceneIndexingException("Error while closing lucene indexes.", e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.impetus.kundera.index.Indexer#flush()
     */
    @Override
    public void flush()
    {
/*        if (w != null)
        {

            // w.commit();
            // w.close();
            // index.copy(index, FSDirectory.open(getIndexDirectory()),
            // false);
        }*/
    }

    @Override
    public void index(EntityMetadata metadata, Object object, String parentId, Class<?> clazz)
    {

        indexDocument(metadata, object, parentId, clazz);
        onCommit();
    }

    @Override
    public boolean entityExistsInIndex(Class<?> entityClass)
    {
        String luceneQuery = "+" + ENTITY_CLASS_FIELD + ":" + entityClass.getCanonicalName().toLowerCase();
        Map<String, String> results;
        try
        {
            results = search(luceneQuery, 0, 10, false);
        }
        catch (LuceneIndexingException e)
        {
            return false;
        }
        if (results == null || results.isEmpty())
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * Index document.
     * 
     * @param metadata
     *            the metadata
     * @param object
     *            the object
     * @param parentId
     *            the parent id
     * @param clazz
     *            the clazz
     * @return the document
     */
    private Document indexDocument(EntityMetadata metadata, Object object, String parentId, Class<?> clazz)
    {
        if (!metadata.isIndexable())
        {
            return null;
        }

        log.debug("Indexing @Entity[" + metadata.getEntityClazz().getName() + "] " + object);

        Document currentDoc = null;
        Object embeddedObject = null;
        String rowKey = null;
        try
        {
            rowKey = PropertyAccessorHelper.getId(object, metadata);
        }
        catch (PropertyAccessException e1)
        {
            throw new LuceneIndexingException("Can't access Primary key property from " + metadata.getEntityClazz(), e1);
        }

        // In case defined entity is Super column family.
        // we need to create seperate lucene document for indexing.

        if (metadata.getType().equals(EntityMetadata.Type.SUPER_COLUMN_FAMILY))
        {
            Map<String, EmbeddedColumn> embeddedColumnMap = metadata.getEmbeddedColumnsMap();

            for (String embeddedColumnName : embeddedColumnMap.keySet())
            {
                EmbeddedColumn embeddedColumn = embeddedColumnMap.get(embeddedColumnName);
                try
                {

                    embeddedObject = PropertyAccessorHelper.getObject(object, embeddedColumn.getField());

                    // If embeddedObject is not set, no point of indexing, move
                    // to next super column
                    if (embeddedObject == null)
                    {
                        continue;
                    }
                    if (embeddedObject instanceof Collection<?>)
                    {
                        ElementCollectionCacheManager ecCacheHandler = ElementCollectionCacheManager.getInstance();
                        // Check whether it's first time insert or updation
                        if (ecCacheHandler.isCacheEmpty())
                        { // First time
                          // insert
                            int count = 0;
                            for (Object obj : (Collection<?>) embeddedObject)
                            {
                                String elementCollectionObjectName = embeddedColumnName
                                        + Constants.EMBEDDED_COLUMN_NAME_DELIMITER + count;

                                currentDoc = prepareDocumentForSuperColumn(metadata, object,
                                        elementCollectionObjectName, parentId, clazz);
                                indexSuperColumn(metadata, object, currentDoc, obj, embeddedColumn);
                                count++;
                            }
                        }
                        else
                        {
                            // Updation, Check whether this object is already in
                            // cache, which means we already have an embedded
                            // column
                            // Otherwise we need to generate a fresh embedded
                            // column name
                            int lastEmbeddedObjectCount = ecCacheHandler.getLastElementCollectionObjectCount(rowKey);
                            for (Object obj : (Collection<?>) embeddedObject)
                            {
                                String elementCollectionObjectName = ecCacheHandler.getElementCollectionObjectName(
                                        rowKey, obj);
                                if (elementCollectionObjectName == null)
                                { // Fresh
                                  // row
                                    elementCollectionObjectName = embeddedColumnName
                                            + Constants.EMBEDDED_COLUMN_NAME_DELIMITER + (++lastEmbeddedObjectCount);
                                }

                                currentDoc = prepareDocumentForSuperColumn(metadata, object,
                                        elementCollectionObjectName, parentId, clazz);
                                indexSuperColumn(metadata, object, currentDoc, obj, embeddedColumn);
                            }
                        }

                    }
                    else
                    {
                        currentDoc = prepareDocumentForSuperColumn(metadata, object, embeddedColumnName, parentId,
                                clazz);
                        indexSuperColumn(metadata, object, currentDoc,
                                metadata.isEmbeddable(embeddedObject.getClass()) ? embeddedObject : object,
                                embeddedColumn);
                    }
                }
                catch (PropertyAccessException e)
                {
                    log.error("Error while accesing embedded Object:" + embeddedColumnName);
                    throw new LuceneIndexingException("Error while accesing embedded Object:" + embeddedColumnName, e);
                }

            }
        }
        else
        {
            currentDoc = new Document();

            // Add entity class, PK info into document
            addEntityClassToDocument(metadata, object, currentDoc);

            // Add all entity fields(columns) into document
            addEntityFieldsToDocument(metadata, object, currentDoc);

            indexParentKey(parentId, currentDoc, clazz);
            // Store document into index
            indexDocument(metadata, currentDoc);
        }

        return currentDoc;
    }

    /**
     * On commit.
     */
    private void onCommit()
    {
        // TODO: Sadly this required to keep lucene happy, in case of indexing
        // and searching with same entityManager.
        // Other alternative would be to issue flush on each search
        // try
        // {
        // w.commit();
        isInitialized = true;
        readyForCommit = true;
        // }
        // catch (CorruptIndexException e)
        // {
        // throw new IndexingException(e.getMessage());
        // }
        // catch (IOException e)
        // {
        // throw new IndexingException(e.getMessage());
        // }
    }

}
