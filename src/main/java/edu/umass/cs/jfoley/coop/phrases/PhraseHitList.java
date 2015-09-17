package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;

/**
 * @author jfoley
 */
public class PhraseHitList {
  IntList memData;

  public PhraseHitList() {
    this(200); // mean
  }

  public PhraseHitList(int count) {
    memData = new IntList(count * 3);
  }

  public void add(PhraseHit hit) {
    this.add(hit.start, hit.size, hit.id);
  }

  public void add(int start, int size, int id) {
    memData.add(start);
    memData.add(size);
    memData.add(id);
  }

  public IntList find(int start, int size) {
    IntList matching = new IntList();
    int q_end = start + size;
    for (int i = 0; i < memData.size(); i += 3) {
      int cstart = memData.getQuick(i);
      if (q_end < cstart) continue;
      int csize = memData.getQuick(i + 1);
      int cid = memData.getQuick(i + 2);
      int cend = cstart + csize;
      if (cend < start) break;
      matching.add(cid);
    }
    return matching;
  }
}
