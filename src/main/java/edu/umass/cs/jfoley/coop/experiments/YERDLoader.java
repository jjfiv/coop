package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.LinesIterable;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class YERDLoader {
  public static class YERDQuery {
    final String qid;
    final String text;
    final char difficulty;
    final List<String> entities;

    public YERDQuery(String qid, String text, char difficulty) {
      this.qid = qid;
      this.text = text;
      this.difficulty = difficulty;
      this.entities = new ArrayList<>();
    }

    @Override
    public String toString() {
      return qid+"\t"+text+"\t"+entities;
    }

    public void addEntity(String ent) {
      entities.add(ent);
    }
  }
  public static void main(String[] args) throws IOException {
    List<String> lines = LinesIterable.fromFile("Y-ERD_spell-corrected.tsv").slurp();

    Map<String, YERDQuery> queryInfo = new HashMap<>();
    String[] header = lines.get(0).split("\t");
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      String[] data = line.split("\t");

      char difficulty = data[0].charAt(0);
      String qid = data[1];
      String query = data[2];
      YERDQuery q = queryInfo.computeIfAbsent(qid, (ignored -> new YERDQuery(qid, query, difficulty)));
      if(data.length > 3) {
        String ent = data[4];
        q.addEntity(ent);
      }
    }


    for (YERDQuery q : queryInfo.values()) {
      if(q.entities.isEmpty()) continue;
      System.err.println(q.qid);
      System.err.println("\t"+q.text);
      System.err.println("\t"+q.entities);
    }
  }
}
