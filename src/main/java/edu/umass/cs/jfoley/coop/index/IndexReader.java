package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipArchive;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.feature.Feature;
import edu.umass.cs.ciir.waltz.feature.MoverFeature;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.index.AbstractIndex;
import edu.umass.cs.ciir.waltz.index.mem.CountsOfPositionsMover;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.SimplePostingListFormat;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.index.corpus.AbstractCorpusReader;
import edu.umass.cs.jfoley.coop.index.corpus.ZipTokensCorpusReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class IndexReader extends AbstractIndex implements Closeable {
  final Directory indexDir;
  final ZipArchive rawCorpus;
  ZipTokensCorpusReader tokensCorpus;
  final IOMap<String, PostingMover<Integer>> lengths;
  final IdMaps.Reader<String> names;
  final IOMap<String, PostingMover<PositionsList>> positions;
  final CoopTokenizer tokenizer;
  final Map<String, DocVarSchema> fieldSchema;
  final DocumentLabelIndex.Reader docLabels;
  final Parameters meta;

  public IndexReader(Directory indexDir) throws IOException {
    this.indexDir = indexDir;
    this.meta = Parameters.parseFile(indexDir.child("meta.json"));

    this.fieldSchema = new HashMap<>();
    Parameters schema = meta.get("schema", Parameters.create());
    for (String field : schema.keySet()) {
      this.fieldSchema.put(field, DocVarSchema.create(field, schema.getMap(field)));
    }
    this.docLabels = new DocumentLabelIndex.Reader(indexDir.childPath("doclabels"));

    this.tokenizer = CoopTokenizer.create(meta);
    this.rawCorpus = ZipArchive.open(indexDir.child("raw.zip"));
    this.tokensCorpus = new ZipTokensCorpusReader(ZipArchive.open(indexDir.child("tokens.zip")), new ListCoder<>(CharsetCoders.utf8LengthPrefixed));
    this.lengths = GalagoIO.openIOMap(
        CharsetCoders.utf8Raw,
        new SimplePostingListFormat.PostingCoder<>(VarUInt.instance),
        indexDir.childPath("lengths")
    );
    this.positions = GalagoIO.openIOMap(
        CharsetCoders.utf8Raw,
        new SimplePostingListFormat.PostingCoder<>(new PositionsListCoder()),
        indexDir.childPath("positions")
    );
    this.names = IdMaps.openReader(indexDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8Raw);
  }

  public CoopTokenizer getTokenizer() {
    return tokenizer;
  }

  @Override
  public void close() throws IOException {
    rawCorpus.close();
    tokensCorpus.close();
    lengths.close();
    names.close();
    positions.close();
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
  public List<Integer> getAllDocumentIds() {
    try {
      return IterableFns.collect(names.forwardReader.keys(), new IntList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public PostingMover<Integer> getCountsMover(String term) {
    PostingMover<PositionsList> mover = getPositionsMover(term);
    if(mover == null) return null;
    return new CountsOfPositionsMover(mover);
  }

  @Override
  public PostingMover<PositionsList> getPositionsMover(String term) {
    try {
      return positions.get(term);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getDocumentName(int id) {
    try {
      return names.forwardReader.get(id);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getDocumentId(String documentName) {
    try {
      Integer id = names.reverseReader.get(documentName);
      if(id == null) return -1;
      return id;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public AbstractCorpusReader getCorpus() {
    return tokensCorpus;
  }

  @Override
  public Feature<Integer> getLengths() {
    try {
      return new MoverFeature<>(lengths.get("doc"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int collectionFrequency(String term) throws IOException {
    PostingMover<PositionsList> mover = positions.get(term);
    if(mover == null) return 0;
    int count = 0;
    for(; mover.hasNext(); mover.next()) {
      count += mover.getCurrentPosting().size();
    }
    return count;
  }
}
