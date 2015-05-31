package edu.umass.cs.jfoley.coop;

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
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.index.AbstractIndex;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.SimplePostingListFormat;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

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
  }

  @Override
  public void close() throws IOException {
    rawCorpus.close();
    tokensCorpus.close();
    lengths.close();
    names.close();
    vocab.close();
    termIdCorpus.close();
  }

  @Override
  public int getCollectionLength() {
    return 0;
  }

  @Override
  public int getDocumentCount() {
    return 0;
  }

  @Override
  public List<Integer> getAllDocumentIds() {
    return null;
  }

  @Override
  public PostingMover<Integer> getCountsMover(String term) {
    return null;
  }

  @Override
  public PostingMover<PositionsList> getPositionsMover(String term) {
    return null;
  }

  @Override
  public String getDocumentName(int id) {
    return null;
  }

  @Override
  public int getDocumentId(String documentName) {
    return 0;
  }

  @Override
  public Feature<Integer> getLengths() {
    return null;
  }
}
