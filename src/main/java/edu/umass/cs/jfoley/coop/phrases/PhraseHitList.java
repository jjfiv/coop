package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.AChaiList;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.postings.extents.Span;

/**
 * @author jfoley
 */
public class PhraseHitList extends AChaiList<PhraseHit> {
  IntList memData;

  public PhraseHitList() {
    this(200); // mean
  }

  public PhraseHitList(IntList raw) {
    memData = raw;
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
    memData = new IntList(count*3);
  }

  public boolean add(PhraseHit hit) {
    this.add(hit.start, hit.size, hit.id);
    return true;
  }

  public void add(int start, int size, int id) {
    memData.push(start);
    memData.push(size);
    memData.push(id);
  }

  public IntList find(int start, int size) {
    IntList matching = new IntList();
    Span query = new Span(start, start+size);
    for (int i = 0; i < memData.size(); i += 3) {
      int cstart = memData.getQuick(i);
      int csize = memData.getQuick(i + 1);
      int cid = memData.getQuick(i + 2);
      int cend = cstart + csize;

      if(query.overlaps(cstart, cend)) {
        //System.err.printf("  YES: q:[%d,%d) c:[%d,%d,%d)\n", query.begin, query.end, cstart, cend, cid);
        matching.add(cid);
      } else {
        //System.err.printf("  NO:  q:[%d,%d) c:[%d,%d)\n", query.begin, query.end, cstart, cend);
      }
    }
    return matching;
  }
}
