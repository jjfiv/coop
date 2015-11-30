package edu.umass.cs.jfoley.coop.experiments.wordco;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import org.lemurproject.galago.contrib.hash.UniversalStringHashFunction;

import java.io.IOException;
import java.util.Random;

/**
 * @author jfoley
 */
public class HashCollisionCounter {
  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("/mnt/scratch3/jfoley/clue12a.sdm.ints"));

    long clen = target.getCollectionLength();
    long universe = 256; // byte[]
    double errCount = 1.0;

    UniversalStringHashFunction sjhHash = UniversalStringHashFunction.generate(clen, universe, errCount, new Random());
    TIntIntHashMap hashToNumKeys = new TIntIntHashMap();
    TLongIntHashMap uhashToNumKeys = new TLongIntHashMap();

    Debouncer msg = new Debouncer();
    long total = target.getTermVocabulary().size();
    long processed = 0;
    for (String key : target.getTermVocabulary().values()) {
      int hash = key.hashCode();
      hashToNumKeys.adjustOrPutValue(hash, 1, 1);
      long uhash = (sjhHash.hash(key)) & 0xffffffffL;
      uhashToNumKeys.adjustOrPutValue(uhash, 1, 1);
      processed++;
      if(msg.ready()) {
        System.err.println(msg.estimate(processed, total));
      }
    }

    System.err.println("Original Vocabulary Size: "+total);
    System.err.println("Hashed Vocabulary Size: "+hashToNumKeys.size());
    System.err.println("UHashed Vocabulary Size: "+uhashToNumKeys.size());

  }
}
