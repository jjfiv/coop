package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.ArrayListMap;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.classifier.ClassifiedToken;
import edu.umass.cs.jfoley.coop.conll.classifier.Classifier;
import edu.umass.cs.jfoley.coop.conll.classifier.SparseBooleanFeatures;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class RankByClassifierFn extends IndexServerFn {
  private final int totalTokens;

  public RankByClassifierFn(TermBasedIndexReader index) {
    super(index);
    this.totalTokens = IntMath.fromLong(index.tokenCorpus.keyCount());
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    int classifierId = input.getInt("classifier");
    int count = input.get("count", 100);
    int timeLimit = input.get("timeLimit", 500);
    int featureLimit = input.get("features", 100);
    int startFrom = input.get("startFrom", input.get("lastScored", -1)+1);

    Classifier classifier = index.classifiers.getOrTrain(classifierId);
    if(classifier == null) throw new IllegalArgumentException("No such classifier: "+classifierId);

    IntList features = new IntList();
    for (int fid : classifier.getStrongestFeatures(featureLimit).keys()) {
      features.add(fid);
    }
    if(features.size() == 0) {
      throw new IllegalArgumentException("No featurs for given classifier!");
    }
    Map<Integer, Mover> featurePostings = new ArrayListMap<>();
    for (Pair<Integer, String> kv : index.features.forwardReader.getInBulk(features)) {
      featurePostings.put(kv.getKey(), index.featureIndex.get(kv.getValue()));
    }
    if(featurePostings.size() == 0) {
      throw new RuntimeException("No features for given classifier!");
    }

    List<Mover> actualFeatures = new ArrayList<>(featurePostings.values());
    AnyOfMover<Mover> orMover = new AnyOfMover<>(actualFeatures);

    Parameters response = Parameters.create();
    int numScored = 0;
    int numPositive = 0;
    IntList active = new IntList(featurePostings.size());
    long startTime = System.currentTimeMillis();
    TopKHeap<ClassifiedToken> heap = new TopKHeap<>(count, Comparing.defaultComparator());

    // continue from here:
    orMover.moveToAbsolute(startFrom);

    // score in order
    for( ;!orMover.isDone(); orMover.next()) {
      int id = orMover.currentKey();
      numScored++;
      long current = System.currentTimeMillis();

      active.clear();
      for (Map.Entry<Integer, Mover> kv : featurePostings.entrySet()) {
        if(kv.getValue().matches(id)) {
          active.add(kv.getKey());
        }
      }
      SparseBooleanFeatures fv = new SparseBooleanFeatures(active);
      boolean pred = classifier.predict(fv);
      if(pred) numPositive++;
      double score = classifier.rank(fv);
      ClassifiedToken ctoken = new ClassifiedToken(classifierId, pred, score, null);
      ctoken.tokenId = id;
      heap.offer(ctoken);

      if(current - startTime > timeLimit) {
        System.out.println("Time limit exceeded.");
        response.put("timeLimitExceeded", true);
        response.put("lastScored", orMover.currentKey());
        // only score for up to 4s.
        break;
      }
    }

    Map<Integer, ClassifiedToken> tokensById = new HashMap<>(count);
    IntList resultIds = new IntList(count);
    for (ClassifiedToken ctoken : heap.getUnsortedList()) {
      resultIds.add(ctoken.getTokenId());
      tokensById.put(ctoken.getTokenId(), ctoken);
    }

    for (Pair<Integer, CoopToken> kv : index.tokenCorpus.getInBulk(resultIds)) {
      tokensById.get(kv.getKey()).token = kv.getValue();
    }

    List<Parameters> output = new ArrayList<>(count);
    for (ClassifiedToken classifiedToken : heap.getSorted()) {
      Parameters ctoken = Parameters.create();
      ctoken.put("token", classifiedToken.token.toJSON());
      ctoken.put("positive", classifiedToken.positive);
      ctoken.put("score", classifiedToken.score);
      output.add(ctoken);
    }

    response.put("numScored", numScored);
    response.put("numPositive", numPositive);
    response.put("results", output);
    response.put("totalTokens", totalTokens);
    return response;
  }
}
