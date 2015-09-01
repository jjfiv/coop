package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import edu.umass.cs.ciir.waltz.dbindex.DBConfig;
import edu.umass.cs.ciir.waltz.dbindex.SQLIterable;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.classifier.labeldb.LabelInfo;
import edu.umass.cs.jfoley.coop.conll.classifier.labeldb.LabelJudgment;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class ClassifierSystem implements Closeable {
  public final TermBasedIndexReader index;
  public final List<String> featuresAboveThreshold;

  private final ConnectionSource classifierDB;
  Dao<LabelInfo, Integer> labels;
  Dao<LabelJudgment, Integer> judgments;
  Map<Integer, Classifier> cachedTrainedClassifier;

  public ClassifierSystem(TermBasedIndexReader index) throws IOException {
    this.index = index;
    featuresAboveThreshold = IterableFns.intoList(index.features.values());
    this.cachedTrainedClassifier = new HashMap<>();

    try {
      this.classifierDB = DBConfig.h2File(index.input.child("classifierdb")).getConnectionSource();
      this.labels = DaoManager.createDao(classifierDB, LabelInfo.class);
      this.judgments = DaoManager.createDao(classifierDB, LabelJudgment.class);

      TableUtils.createTableIfNotExists(classifierDB, LabelInfo.class);
      TableUtils.createTableIfNotExists(classifierDB, LabelJudgment.class);
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  public Parameters getInfo(int id) throws SQLException {
    Parameters output = Parameters.create();
    LabelInfo labelInfo = labels.queryForId(id);
    if(labelInfo == null) return output;

    output = labelInfo.toJSON();
    Pair<IntList,IntList> judgments = this.getJudgments(labelInfo.id);
    output.put("positives", judgments.left.size());
    output.put("negatives", judgments.right.size());
    return output;
  }

  public void deleteClassifier(int id) throws SQLException {
    // TODO also delete from judgments table.
    this.labels.deleteById(id);
  }

  public void addLabels(int classifier, List<LabeledToken> newLabels) throws IOException, SQLException {
    LabelInfo labelInfo = labels.queryForId(classifier);
    if(labelInfo == null) throw new IllegalArgumentException("No such classifier id="+classifier);

    try {
      judgments.callBatchTasks(() -> {
        for (LabeledToken newLabel : newLabels) {
          judgments.create(new LabelJudgment(classifier, newLabel.tokenId, newLabel.positive, newLabel.time));
        }
        return null;
      });
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public Pair<IntList,IntList> getJudgments(int classifier) throws SQLException {
    PreparedQuery<LabelJudgment> judgmentsForClassifier = judgments.queryBuilder()
        .where().eq("labelId", classifier)
        .prepare();

    SQLIterable<LabelJudgment> iter = new SQLIterable<>(judgmentsForClassifier, judgments);
    IntList positive = new IntList();
    IntList negative = new IntList();
    for (LabelJudgment labelJudgment : iter) {
      if(labelJudgment.positive) {
        positive.add(labelJudgment.tokenId);
      } else {
        negative.add(labelJudgment.tokenId);
      }
    }
    return Pair.of(positive, negative);
  }

  public Classifier train(int classifierId) throws IOException, SQLException {
    LabelInfo labelInfo = labels.queryForId(classifierId);
    if(labelInfo == null) throw new IllegalArgumentException("No such classifier id="+classifierId);

    Pair<IntList,IntList> judgments = getJudgments(classifierId);
    List<FeatureVector> posF = index.pullFeatures(judgments.left);
    List<FeatureVector> negF = index.pullFeatures(judgments.right);

    Classifier classifier = new PerceptronClassifier(index.numFeatures());
    classifier.train(posF, negF);
    cachedTrainedClassifier.put(classifierId, classifier);
    return classifier;
  }

  public synchronized Classifier getOrTrain(int classifierId) throws IOException, SQLException {
    return cachedTrainedClassifier.getOrDefault(classifierId, train(classifierId));
  }


  public synchronized List<ClassifiedToken> classifyTokens(int classifierId, List<Integer> tokens) throws IOException, SQLException {
    Classifier classifier = cachedTrainedClassifier.getOrDefault(classifierId, train(classifierId));
    if(classifier == null) {
      throw new IllegalArgumentException("No such classifier: " + classifierId);
    }

    List<ClassifiedToken> ctokens = new ArrayList<>();
    for (Pair<CoopToken, FeatureVector> kv : index.TokenFeatures(tokens)) {
      CoopToken tok = kv.getKey();
      FeatureVector fv = kv.getValue();
      boolean pred = classifier.predict(fv);
      double score = classifier.rank(fv);
      ctokens.add(new ClassifiedToken(classifierId, pred, score, tok));
    }

    return ctokens;
  }

  public void setName(int classifier, String name) throws SQLException {
    LabelInfo info = labels.queryForId(classifier);
    info.name = name;
    labels.update(info);
  }
  public void setDescription(int classifier, String description) throws SQLException {
    LabelInfo info = labels.queryForId(classifier);
    info.description = description;
    labels.update(info);
  }

  @Override
  public void close() throws IOException {
    try {
      this.classifierDB.close();
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  public int create(String name) throws SQLException {
    return create(name, null);
  }
  public int create(String name, String description) throws SQLException {
    LabelInfo classifier = new LabelInfo();
    classifier.name = name;
    classifier.description = description;
    labels.create(classifier);
    return classifier.id;
  }

  public List<Parameters> getAllInfo() throws SQLException {
    List<Parameters> output = new ArrayList<>();
    for (LabelInfo labelInfo : labels.queryForAll()) {
      Parameters info = labelInfo.toJSON();
      Pair<IntList,IntList> judgments = this.getJudgments(labelInfo.id);
      info.put("positives", judgments.left.size());
      info.put("negatives", judgments.right.size());
      output.add(info);
    }
    return output;
  }

}
