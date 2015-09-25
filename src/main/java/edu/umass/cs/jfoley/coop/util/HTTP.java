package edu.umass.cs.jfoley.coop.util;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StreamUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author jfoley.
 */
public class HTTP {

  private final static Logger log =Logger.getLogger(HTTP.class.getName());

  public static String encode(String data) {
    try {
      return URLEncoder.encode(data, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Build a list of form values from a Parameters object, if possible
   * @param p the parameters object
   * @return a list of NameValuePair objects for Apache's HttpClient
   */
  private static List<NameValuePair> fromParameters(Parameters p) {
    List<NameValuePair> parms = new ArrayList<>();
    for(String key : p.keySet()) {
      if(p.isList(key)) {
        for(Object val : p.getList(key)) {
          parms.add(new BasicNameValuePair(key, val.toString()));
        }
      } else {
        parms.add(new BasicNameValuePair(key, p.getAsString(key)));
      }
    }
    return parms;
  }

  public static String encodeInURL(String url, Parameters p) {
    StringBuilder urlb = new StringBuilder();
    urlb.append(url);
    char before = '?';
    for(NameValuePair kv : fromParameters(p)) {
      urlb.append(before)
        .append(encode(kv.getName()))
        .append('=')
        .append(encode(kv.getValue()));
      before = '&';
    }
    return urlb.toString();
  }

  public static Response get(String url) throws IOException {
    return get(fixURLParameters(url), "");
  }

  public static String fixURLParameters(String url) {
    int params = url.indexOf("?");
    if(params != -1) {
      try {
        return url.substring(0, params) + "?" + URLEncoder.encode(url.substring(params + 1), "UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return url;
  }
  public static Response get(String url, String path) throws IOException {
    return get(url, fixURLParameters(path), Parameters.create());
  }

  public static Response get(String url, String path, Parameters p) throws IOException {
    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      String urlWithParams = encodeInURL(url + path, p);
      HttpGet get = new HttpGet(urlWithParams);
      HttpResponse response = client.execute(get);
      return new Response(response);
    }
  }

  public static class Response {
    public final int status;
    public final String reason;
    public final String body;

    Response(HttpResponse response) throws IOException {
      this.status = response.getStatusLine().getStatusCode();
      this.reason = response.getStatusLine().getReasonPhrase();
      this.body = StreamUtil.copyStreamToString(response.getEntity().getContent());
    }

    @Override
    public String toString() {
      return String.format("%d %s\n%s", status, reason, body);
    }
  }

}