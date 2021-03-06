package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.chained.ChaiIterable;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.errors.NotHandledNow;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarInt;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.feature.Feature;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.index.AbstractIndex;
import edu.umass.cs.ciir.waltz.index.mem.CountsOfPositionsMover;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.SpanListCoder;
import edu.umass.cs.ciir.waltz.io.postings.format.BlockedPostingsCoder;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.component.DocumentLabelIndexReader;
import edu.umass.cs.jfoley.coop.index.component.KryoCoopDocCorpusWriter;
import edu.umass.cs.jfoley.coop.index.corpus.AbstractCorpusReader;
import edu.umass.cs.jfoley.coop.index.corpus.ZipTokensCorpusReader;
import edu.umass.cs.jfoley.coop.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import edu.umass.cs.jfoley.coop.schema.IntegerVarSchema;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley.
 */
public class IndexReader extends AbstractIndex implements Closeable {
  final Directory indexDir;
  final ZipArchive rawCorpus;
  ZipTokensCorpusReader tokensCorpus;
  final IOMap<String, PostingMover<Integer>> lengths;
  final IdMaps.Reader<String> names;
  final Map<String, IOMap<String, PostingMover<PositionsList>>> positionSets;
  final IOMap<String, PostingMover<Integer>> numbers;
  final IOMap<String, PostingMover<SpanList>> tags;
  final CoopTokenizer tokenizer;
  final Map<String, DocVarSchema> fieldSchema;
  final DocumentLabelIndexReader docLabels;
  final Parameters meta;
  final IOMap<Integer, CoopDoc> corpus;
  private WeakHashMap<String, Integer> termFrequencies;


  public IndexReader(@Nonnull Directory indexDir) throws IOException {
    this.indexDir = indexDir;
    this.meta = Parameters.parseFile(indexDir.child("meta.json"));

    this.fieldSchema = new HashMap<>();
    Parameters schema = meta.get("schema", Parameters.create());
    for (String field : schema.keySet()) {
      this.fieldSchema.put(field, DocVarSchema.create(field, schema.getMap(field)));
    }
    this.docLabels = new DocumentLabelIndexReader(indexDir);
    this.corpus = KryoCoopDocCorpusWriter.getReader(indexDir);

    this.tokenizer = CoopTokenizer.create(meta);
    this.rawCorpus = ZipArchive.open(indexDir.child("raw.zip"));
    this.tokensCorpus = new ZipTokensCorpusReader(ZipArchive.open(indexDir.child("tokens.zip")), new ListCoder<>(CharsetCoders.utf8));
    this.lengths = GalagoIO.openIOMap(
        CharsetCoders.utf8,
        new BlockedPostingsCoder<>(VarUInt.instance),
        indexDir.childPath("lengths")
    );
    this.positionSets = new HashMap<>();
    for (File file : indexDir.children()) {
      if(file.getName().endsWith(".positions")) {
        String termSet = StrUtil.takeBefore(file.getName(), ".positions");
        positionSets.put(
            termSet,
            GalagoIO.openIOMap(
                CharsetCoders.utf8,
                new BlockedPostingsCoder<>(new PositionsListCoder()),
                file.getPath()
            ));
      }
    }
    this.names = GalagoIO.openIdMapsReader(indexDir.childPath("names"), VarUInt.instance, CharsetCoders.utf8);
    this.tags = GalagoIO.openIOMap(
        CharsetCoders.utf8,
        new BlockedPostingsCoder<>(new SpanListCoder()),
        indexDir.childPath("tags")
    );
    this.numbers = GalagoIO.openIOMap(
        CharsetCoders.utf8,
        new BlockedPostingsCoder<>(VarInt.instance),
        indexDir.childPath("numbers")
    );
    termFrequencies = new WeakHashMap<>();
  }

  @Nonnull
  public Set<String> fieldNames() {
    return fieldSchema.keySet();
  }

  @Nullable
  public DocVarSchema getFieldSchema(String fieldName) {
    return fieldSchema.get(fieldName);
  }

  @Nonnull
  public CoopTokenizer getTokenizer() {
    return tokenizer;
  }

  @Override
  public void close() throws IOException {
    rawCorpus.close();
    tokensCorpus.close();
    lengths.close();
    names.close();
    positionSets.values().forEach(IO::close);
  }

  @Override
  public int getCollectionLength() {
    return meta.getInt("collectionLength");
  }

  @Override
  public int getDocumentCount() {
    return meta.getInt("documentCount");
  }

  @Override
  @Nonnull
  public List<Integer> getAllDocumentIds() {
    try {
      return IterableFns.collect(names.ids(), new IntList());
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Override
  public PostingMover<Integer> getCountsMover(String term) {
    PostingMover<PositionsList> mover = getPositionsMover(term);
    if(mover == null) return null;
    return new CountsOfPositionsMover(mover);
  }
  public PostingMover<Integer> getCountsMover(String termKind, String queryTerm) {
    PostingMover<PositionsList> mover = getPositionsMover(termKind, queryTerm);
    if(mover == null) return null;
    return new CountsOfPositionsMover(mover);
  }

  @Override
  @Nullable
  public PostingMover<PositionsList> getPositionsMover(String term) {
    return getPositionsMover(tokenizer.getDefaultTermSet(), term);
  }

  @Nullable
  public PostingMover<PositionsList> getPositionsMover(String type, String term) {
    try {
      IOMap<String, PostingMover<PositionsList>> typeTerms = positionSets.get(type);
      if(typeTerms == null) { return null; }
      return typeTerms.get(term);
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Nullable
  public PostingMover<SpanList> getTag(String tagName) {
    try {
      return tags.get(tagName);
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Nullable
  public Mover getLabeledDocuments(String name, Object value) {
    DocVarSchema docVarSchema = getFieldSchema(name);
    if(docVarSchema == null) return null;
    if(docVarSchema instanceof CategoricalVarSchema) {
      String val = (String) value;
      try {
        return docLabels.getMatchingDocs(name, val);
      } catch (IOException e) {
        throw new IndexErrorException(e);
      }
    } else throw new NotHandledNow("schema", docVarSchema.toString());
  }

  @Override
  @Nullable
  public String getDocumentName(int id) {
    try {
      return names.getForward(id);
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Override
  public int getDocumentId(String documentName) {
    try {
      Integer id = names.getReverse(documentName);
      if(id == null) return -1;
      return id;
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Nonnull
  public AbstractCorpusReader getCorpus() {
    return tokensCorpus;
  }

  @Override
  @Nonnull
  public Feature<Integer> getLengths() {
    PostingMover<Integer> lengthsMover = getLengths(tokenizer.getDefaultTermSet());
    if(lengthsMover == null) throw new IndexErrorException("Should have a lengths posting list for the default term set: "+tokenizer.getDefaultTermSet());
    return lengthsMover.getFeature();
  }

  @Nullable
  public PostingMover<Integer> getLengths(String tokenType) {
    try {
      return lengths.get(tokenType);
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  public int collectionFrequency(String term) {
    Integer cachedFreq = termFrequencies.get(term);
    if(cachedFreq != null) return cachedFreq;

    PostingMover<Integer> mover = getCountsMover(term);
    if (mover == null) return 0;
    int count = 0;
    for (; mover.hasNext(); mover.next()) {
      count += mover.getCurrentPosting();
    }
    termFrequencies.put(term, count);
    return count;
  }

  public Iterable<Pair<Integer,String>> lookupNames(IntList ids) throws IOException {
    return names.getForward(ids);
  }
  public Set<String> getDocumentNames(IntList ids) {
    try {
      return ChaiIterable.create(names.getForward(ids))
          .map(x -> (x.right))
          .intoSet();
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Nullable
  public CoopDoc getDocument(int id) {
    try {
      return corpus.get(id);
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  @Nullable
  public CoopDoc getDocument(String name) {
    return getDocument(getDocumentId(name));
  }

  @Nullable
  public PostingMover<Integer> getNumbers(String numericFieldName) {
    DocVarSchema docVarSchema = getFieldSchema(numericFieldName);
    if(docVarSchema == null) return null;
    if (!(docVarSchema instanceof IntegerVarSchema)) {
      return null;
    }
    try {
      return numbers.get(numericFieldName);
    } catch (IOException e) {
      throw new IndexErrorException(e);
    }
  }

  public Parameters getMetadata() {
    return meta;
  }

}
