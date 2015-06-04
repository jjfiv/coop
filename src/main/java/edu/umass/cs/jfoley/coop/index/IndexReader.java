package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.fn.SinkFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
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
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley.
 */
public class IndexReader extends AbstractIndex implements Closeable {
  final Directory indexDir;
  final ZipArchive rawCorpus;
  final ZipArchive tokensCorpus;
  final ListCoder<String> tokensCodec;
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
    this.tokensCorpus = ZipArchive.open(indexDir.child("tokens.zip"));
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
    this.tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
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

  public List<Pair<TermSlice, List<String>>> pullTermSlices(List<TermSlice> requests) throws IOException {
    List<List<String>> slices = new ArrayList<>();
    for (TermSlice request : requests) {
      List<String> termvec = pullTermIdSlice(request);
      slices.add(termvec);
    }
    return ListFns.zip(requests, slices);
  }

  /**
   * Only pull each document once.
   */
  public List<Pair<TermSlice, List<String>>> pullTermSlices1(List<TermSlice> requests) throws IOException {
    // sort by document.
    Collections.sort(requests);

    // cache a document a time in memory so we don't pull it twice.
    List<String> currentDocTerms = null;
    int currentDocId = -1;

    // slices:
    List<List<String>> slices = new ArrayList<>();
    for (TermSlice request : requests) {
      if(request.document != currentDocId) {
        currentDocId = request.document;
        currentDocTerms = pullTokens(currentDocId);
      }
      // make a copy here so that the documents don't end up leaked into memory via subList.
      slices.add(new ArrayList<>(ListFns.slice(currentDocTerms, request.start, request.end)));
    }
    return ListFns.zip(requests, slices);
  }

  public void forTermInSlice(List<TermSlice> requests, SinkFn<String> onTerm) {
    // sort by document.
    Collections.sort(requests);

    // cache a document a time in memory so we don't pull it twice.
    List<String> currentDocTerms = null;
    int currentDocId = -1;

    // slices:
    List<List<String>> slices = new ArrayList<>();
    for (TermSlice request : requests) {
      if(request.document != currentDocId) {
        currentDocId = request.document;
        currentDocTerms = pullTokens(currentDocId);
      }
      for (String term : ListFns.slice(currentDocTerms, request.start, request.end)) {
        onTerm.process(term);
      }
    }
  }


  public List<String> pullTokens(int document) {
    ZipArchiveEntry entry = tokensCorpus.getByName(Integer.toString(document));
    if(entry == null) return null;
    try {
      return tokensCodec.readImpl(entry.getInputStream());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Raw access to corpus structure.
   * @param slice the coordinates of the data to pull.
   * @return a list of term ids corresponding to the data in the given slice.
   */
  public List<String> pullTermIdSlice(TermSlice slice) {
    return new ArrayList<>(
        ListFns.slice(
            pullTokens(slice.document),
            slice.start,
            slice.end));
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
