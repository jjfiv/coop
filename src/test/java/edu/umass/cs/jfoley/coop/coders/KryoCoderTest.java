package edu.umass.cs.jfoley.coop.coders;

import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class KryoCoderTest {

  @Test
  public void testSimpleKryoCoder() {
    Coder<String> coder = new KryoCoder<>(String.class);
    List<String> data = Sample.strings(new Random(), 1000);
    for (String testStr : data) {
      assertEquals(testStr, coder.read(coder.write(testStr)));
    }
  }

  @Test
  public void testKryoCoderClosesThingsWeNeed() throws IOException {
    Coder<String> coder = new KryoCoder<>(String.class);
    List<String> data = Sample.strings(new Random(), 1000);
    ListCoder<String> listDataCoder = new ListCoder<>(coder);

    assertEquals(data, listDataCoder.read( listDataCoder.writeData(data)));
  }

}