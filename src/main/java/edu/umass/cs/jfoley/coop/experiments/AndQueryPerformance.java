package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.random.ReservoirSampler;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.ints.IntsCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class AndQueryPerformance {
  public static PostingsConfig<Integer, PositionsList> getCfg(String what) {
    int blockSize;
    IntsCoder docsCoder;

    switch (what) {
      case "positions":
        blockSize = 128;
        docsCoder = new DeltaIntListCoder();
        break;

      //Time to compute AND.pull: {total=138.21037495599998, min=0.259082729, variance=0.0014720888876883459, max=1.065861554, mean=0.2764207499120004, count=500.0, stddev=0.0383678105667804}
      case "p128":
      case "upositions":
        blockSize = 128;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;

//Time to compute AND.pull: {total=135.042183165, min=0.258258485, max=0.494070098, variance=2.0519558900570072E-4, mean=0.2700843663300001, count=500.0, stddev=0.014324649699231766}
//Results: {total=7.83225E7, min=156645.0, variance=0.0, max=156645.0, mean=156645.0, count=500.0, stddev=0.0}
//Sizes: {total=3.7155275E9, min=1.0, variance=125.23970931428381, max=405.0, mean=7.906471107711957, count=4.69935E8, stddev=11.191054879424183}
      case "p256":
        blockSize = 256;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;
      case "p512":
        blockSize = 512;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;

      case "p8":
        blockSize = 1;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;
      default: throw new RuntimeException();
    }

    PostingsConfig<Integer, PositionsList> config = new PostingsConfig<>(
        FixedSize.ints,
        new PositionsListCoder(),
        Comparing.defaultComparator(),
        new PositionsCountMetadata()
    );

    config.blockSize = blockSize;
    config.docsCoder = docsCoder;

    return config;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory input = Directory.Read(argp.get("input", "/mnt/scratch/jfoley/int-corpora/robust.ints/"));

    String target = "p128";
    PostingsConfig<Integer,PositionsList> cfg = getCfg(target);
    IOMap<Integer, PostingMover<PositionsList>> index = cfg.openReader(input, target);
    IdMaps.Reader<String> vocab = GalagoIO.openIdMapsReader(input.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8);

    List<String> phraseTerms = Arrays.asList("to", "be", "or", "not", "to", "be");

    // position -> index:
    IntList termIdMapping = new IntList();
    List<String> uniqueTerms = new ArrayList<>();

    for (int i = 0; i < phraseTerms.size(); i++) {
      String t_i = phraseTerms.get(i);
      int first_pos = phraseTerms.indexOf(t_i);
      termIdMapping.add(first_pos);
      if (first_pos == i) {
        uniqueTerms.add(t_i);
      }
    }

    IntList phraseTermIds = new IntList();
    Map<String, Integer> termMapping = new HashMap<>();
    for (Pair<String, Integer> kv : vocab.getReverse(uniqueTerms)) {
      termMapping.put(kv.getKey(), kv.getValue());
    }
    for (String phraseTerm : phraseTerms) {
      phraseTermIds.add(termMapping.get(phraseTerm));
    }
    System.out.println(phraseTermIds);
    System.out.println(termIdMapping);

    // and iters:
    List<PostingMover<PositionsList>> iters = new ArrayList<>();
    for (Integer termId : phraseTermIds) {
      PostingMover<PositionsList> iter = index.get(termId);
      int numDocuments = iter != null ? iter.totalKeys() : 0;
      System.out.println("termId: " + termId + " numDocs: " + numDocuments);
      if (iter != null) iters.add(iter);
    }

    AllOfMover<?> andMover = new AllOfMover<>(iters);
    //AnyOfMover<?> andMover = new AnyOfMover<>(iters);
    long startMovement;
    long endMovement;

    // calculate document intersection time:
    ReservoirSampler<Integer> highestNums = new ReservoirSampler<>(1000);
    StreamingStats sizes = new StreamingStats();
    StreamingStats performance = new StreamingStats();
    StreamingStats results = new StreamingStats();
    for (int k = 0; k < 500; k++) {
      highestNums.clear();
      startMovement = System.nanoTime();
      for(andMover.start(); !andMover.isDone(); andMover.next()) {
        int x = andMover.currentKey();
        highestNums.add(x);
        for (PostingMover<PositionsList> iter : iters) {
          sizes.push((double) iter.getPosting(x).size());
        }
      }
      andMover.reset();
      endMovement = System.nanoTime();
      performance.push((endMovement - startMovement) / 1e9);
      results.push(highestNums.total());
    }
    System.out.println("Time to compute AND.pull: " + performance);
    System.out.println("Results: "+results);
    System.out.println("Sizes: "+sizes);

  }
}
