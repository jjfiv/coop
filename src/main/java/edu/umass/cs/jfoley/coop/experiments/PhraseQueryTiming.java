package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PositionsIndexFile;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class PhraseQueryTiming {

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
      if(first_pos == i) {
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
      System.out.println("termId: "+termId+" numDocs: "+numDocuments);
      if(iter != null) iters.add(iter);
    }

    AllOfMover<?> andMover = new AllOfMover<>(iters);
    long startMovement; long endMovement;

    // calculate document intersection time:
    TopKHeap<Integer> highestNums = new TopKHeap<>(1000);
    startMovement = System.currentTimeMillis();
    andMover.execute((doc) -> {
      if(highestNums.peek() != null && doc <= highestNums.peek()) {
        throw new RuntimeException("Saw document #"+doc+" again!");
      }
      highestNums.process(doc);
    });
    endMovement = System.currentTimeMillis();
    System.out.println("Time to compute AND: "+(endMovement - startMovement));
    andMover.reset();

    // calculate document intersection time + phrase calculation time:
    StreamingStats stats = new StreamingStats();
    startMovement = System.currentTimeMillis();

    List<PositionsIterator> piter = new ArrayList<>(termIdMapping.size());
    for (int i = 0; i < termIdMapping.size(); i++) {
      piter.add(null);
    }

    for (int k = 0; k < 10; k++) {
      // function call overhead here doesn't seem to matter
      // hooray for invokedynamic
      // for(andMover.start(); !andMover.isDone(); andMover.next()) {
      andMover.execute((doc) -> {
        // phrase calculation:
        for (int i = 0; i < termIdMapping.size(); i++) {
          int iterIndex = termIdMapping.getQuick(i);
          piter.set(i, iters.get(iterIndex).getCurrentPosting().getSpanIterator());
        }
        int count = OrderedWindow.countIter(piter, 1);
        if (count > 0) {
          stats.process((double) count);
        }
      });
      andMover.reset();
    }

    endMovement = System.currentTimeMillis();
    System.out.println("Time to compute AND.od1: " + (endMovement - startMovement));
    System.out.println(stats);
    stats.clear();


    // unless there's really good boost from vector instructions, bitmap version is 2x worse... and not always accurate.
    //Time to compute AND.od1: 3759
    //{total=280.0, min=1.0, variance=0.0, max=1.0, mean=1.0, count=280.0, stddev=0.0}
    //Time to compute AND.od1: 7108
    //{total=60.0, min=1.0, variance=0.0, max=1.0, mean=1.0, count=60.0, stddev=0.0}
    /*
    for (int k = 0; k < 10; k++) {
      // function call overhead here doesn't seem to matter
      // hooray for invokedynamic
      for(andMover.start(); !andMover.isDone(); andMover.next()) {
        // phrase calculation:
        BitVector first = new BitVector(4000);
        first.or(((PositionsListCoder.ArrayPosList) iters.get(0).getCurrentPosting()).myBitVector);

        for (int i = 1; i < termIdMapping.size(); i++) {
          //BitVector second = bitVectors.get(i);
          //second.or(((PositionsListCoder.ArrayPosList) iters.get(termIdMapping.getQuick(i)).getCurrentPosting()).myBitVector);
          //second.shiftLeft(i);
          //first.and(second);
          first.andShl(((PositionsListCoder.ArrayPosList) iters.get(termIdMapping.getQuick(i)).getCurrentPosting()).myBitVector, i);
          if(first.zeros()) {
            break;
          }
        }
        int count = first.count();
        if (count > 0) {
          stats.process((double) count);
        }
      }
      andMover.reset();
    }
    */

    endMovement = System.currentTimeMillis();
    System.out.println("Time to compute AND.od1: " + (endMovement - startMovement));
    andMover.reset();
    System.out.println(stats);
    stats.clear();

  }
}
