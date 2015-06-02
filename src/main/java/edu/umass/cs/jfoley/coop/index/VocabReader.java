package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipArchive;
import edu.umass.cs.ciir.waltz.coders.GenKeyDiskMap;
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
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley.
 */
public class VocabReader extends AbstractIndex implements Closeable {
  final Directory indexDir;
  final ZipArchive rawCorpus;
  final ZipArchive tokensCorpus;
  final ListCoder<String> tokensCodec;
  final IOMap<String, PostingMover<Integer>> lengths;
  final IdMaps.Reader<String> names;
  final IdMaps.Reader<String> vocab;
  final GenKeyDiskMap.Reader<List<Integer>> termIdCorpus;
  final IOMap<Integer, PostingMover<PositionsList>> positions;
  final Parameters meta;

  public VocabReader(Directory indexDir) throws IOException {
    this.indexDir = indexDir;
    this.rawCorpus = ZipArchive.open(indexDir.child("raw.zip"));
    this.tokensCorpus = ZipArchive.open(indexDir.child("tokens.zip"));
    this.lengths = GalagoIO.openIOMap(
        CharsetCoders.utf8Raw,
        new SimplePostingListFormat.PostingCoder<>(VarUInt.instance),
        indexDir.childPath("lengths")
    );
    this.positions = GalagoIO.openIOMap(
        FixedSize.ints,
        new SimplePostingListFormat.PostingCoder<>(new PositionsListCoder()),
        indexDir.childPath("positions")
    );
    this.names = IdMaps.openReader(indexDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8Raw);
    this.vocab = IdMaps.openReader(indexDir.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8Raw);
    tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
    termIdCorpus = GenKeyDiskMap.Reader.openFiles(indexDir.childPath("termIdCorpus"), new ListCoder<>(VarUInt.instance));
    meta = Parameters.parseFile(indexDir.child("meta.json"));
  }

  @Override
  public void close() throws IOException {
    rawCorpus.close();
    tokensCorpus.close();
    lengths.close();
    names.close();
    vocab.close();
    termIdCorpus.close();
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
      Integer termId = vocab.reverseReader.get(term);
      if(termId == null) return null;
      return positions.get(termId);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getDocumentName(int id) {
    try {
      return vocab.forwardReader.get(id);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getDocumentId(String documentName) {
    try {
      Integer id = vocab.reverseReader.get(documentName);
      if(id == null) return -1;
      return id;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<Pair<TermSlice, List<String>>> pullTermSlices(List<TermSlice> requests) throws IOException {
    List<IntList> sliceIds = new ArrayList<>();
    Set<Integer> vocabOfInterest = new HashSet<>();
    for (TermSlice request : requests) {
      IntList termvec = pullTermIdSlice(request);
      sliceIds.add(termvec);
      vocabOfInterest.addAll(termvec);
    }

    // lookup necessary vocab:
    Map<Integer, String> terms = MapFns.fromPairs(vocab.forwardReader.getInBulk(new IntList(vocabOfInterest)));

    // translate pre-loaded list:
    List<List<String>> translatedSlices = new ArrayList<>();
    for (IntList sliceId : sliceIds) {
      List<String> slice = new ArrayList<>(sliceId.size());
      for (int termId : sliceId) {
        slice.add(terms.get(termId));
      }
      translatedSlices.add(slice);
    }

    return ListFns.zip(requests, translatedSlices);
  }

  /**
   * Raw access to corpus structure.
   * @param slice the coordinates of the data to pull.
   * @return a list of term ids corresponding to the data in the given slice.
   */
  public IntList pullTermIdSlice(TermSlice slice) {
    return new IntList(
        ListFns.slice(
            termIdCorpus.get(slice.document),
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
    Integer x = vocab.reverseReader.get(term);
    if(x == null) return 0;
    return collectionFrequency(x);
  }

  public int collectionFrequency(int termId) throws IOException {
    if(termId < 0) return 0;
    PostingMover<PositionsList> mover = positions.get(termId);
    int count = 0;
    for(; mover.hasNext(); mover.next()) {
      count += mover.getCurrentPosting().size();
    }
    return count;
  }

  public IntList getTermIds(List<String> terms) throws IOException {
    Map<String, Integer> lookup = MapFns.fromPairs(vocab.reverseReader.getInBulk(terms));
    IntList results = new IntList(terms.size());
    for (String term : terms) {
      results.add(MapFns.getOrElse(lookup, term, -1));
    }
    return results;
  }

  public String getTerm(int termId) throws IOException {
    return vocab.forwardReader.get(termId);
  }
}
