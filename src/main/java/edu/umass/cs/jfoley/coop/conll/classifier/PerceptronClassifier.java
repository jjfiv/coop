package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.lang.DoubleFns;
import gnu.trove.map.hash.TIntFloatHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley.
 */
public class PerceptronClassifier extends Classifier {
  private float[] w;
  private int numIterations = 1000;

  public PerceptronClassifier(int numFeatures) {
    super(numFeatures);
    reset();
  }

  public void reset() {
    w = new float[numFeatures + 1];
  }

  @Override
  public double train(List<? extends FeatureVector> pos, List<? extends FeatureVector> neg) {
    List<Pair<Integer, FeatureVector>> data = new ArrayList<>(pos.size() + neg.size());
    for (FeatureVector fv : pos) {
      data.add(Pair.of(1, fv));
    }
    for (FeatureVector fv : neg) {
      data.add(Pair.of(-1, fv));
    }

    Collections.shuffle(data);

    int total = data.size();
    int correct = 0;
    boolean changed = false;
    int numIters;
    for (numIters = 0; numIters < numIterations; numIters++) {
      correct = 0;
      for (Pair<Integer, FeatureVector> labeledData : data) {
        int label = labeledData.left;
        FeatureVector fv = labeledData.right;

        int predicted = predict(fv) ? 1 : -1;

        if (predicted != label) {
          for (Pair<Integer, Float> kv : fv) {
            w[kv.left] += label * kv.right;
          }
          w[w.length - 1] += label;
          changed = true;
        } else {
          correct++;
        }
      } // data-loop

      // skip iterations if we're somehow perfect:
      if (!changed && correct == total) break;
    } // iter-loop

    System.out.println("Number of iterations: " + numIters);
    return (double) correct / (double) total;
  }

  @Override
  public boolean predict(FeatureVector fv) {
    return rank(fv) >= 0;
  }

  @Override
  double rank(FeatureVector fv) {
    double dotP = 0.0;
    for (Pair<Integer, Float> kv : fv) {
      dotP += w[kv.left] * kv.right;
    }
    dotP += w[w.length - 1]; // * 1.0 if we extended all the features.
    return dotP;
  }

  @Override
  int getComplexity() {
    return this.getSparseFeatures().size();
  }

  public TIntFloatHashMap getSparseFeatures() {
    TIntFloatHashMap output = new TIntFloatHashMap();
    for (int i = 0; i < w.length; i++) {
      if (!DoubleFns.equals(w[i], 0.0, 0.01)) {
        output.put(i, w[i]);
      }
    }
    return output;
  }

}
