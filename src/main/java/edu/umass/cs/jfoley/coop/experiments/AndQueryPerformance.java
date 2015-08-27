package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.random.ReservoirSampler;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PositionsIndexFile;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class AndQueryPerformance {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory input = Directory.Read(argp.get("input", "robust.ints"));

    IOMap<Integer, PostingMover<PositionsList>> index = PositionsIndexFile.openReader(FixedSize.ints, input);
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
    for (Pair<String, Integer> kv : vocab.reverseReader.getInBulk(uniqueTerms)) {
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
    long startMovement;
    long endMovement;

    // calculate document intersection time:
    ReservoirSampler<Integer> highestNums = new ReservoirSampler<>(1000);
    StreamingStats performance = new StreamingStats();
    for (int k = 0; k < 500; k++) {
      startMovement = System.nanoTime();
      andMover.execute(highestNums);
      andMover.reset();
      endMovement = System.nanoTime();
      performance.push((endMovement - startMovement) / 1e9);
    }
    System.out.println("Time to compute AND: " + performance);

  }
}
