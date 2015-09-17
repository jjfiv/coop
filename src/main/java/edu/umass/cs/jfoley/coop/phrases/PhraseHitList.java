package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.AChaiList;
import ciir.jfoley.chai.collections.list.IntList;

/**
 * @author jfoley
 */
public class PhraseHitList extends AChaiList<PhraseHit> {
  IntList memData;

  public PhraseHitList() {
    this(200); // mean
  }

  @Override
  public int size() {
    return memData.size() / 3;
  }

  @Override
  public PhraseHit get(int index) {
    int i = index * 3;
    return new PhraseHit(memData.getQuick(i), memData.getQuick(i+1), memData.getQuick(i+2));
  }

  public PhraseHitList(int count) {
    memData = new IntList(count * 3);
  }

  public boolean add(PhraseHit hit) {
    this.add(hit.start, hit.size, hit.id);
    return true;
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
