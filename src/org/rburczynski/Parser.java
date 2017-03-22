package org.rburczynski;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

class Parser {
    public static void init(){
        System.setProperty("http.agent", "Mozilla/5.0 (X11; Linux x86_64; rv:50.0) Gecko/20100101 Firefox/50.0");
    }

    public static BufferedReader getReader(String url) throws IOException {
        URLConnection yc = new URL(url).openConnection();
        if(!yc.getContentType().startsWith("text/html"))
            throw new IOException("Not a HTML page: " + yc.getContentType());
        return new BufferedReader(new InputStreamReader(yc.getInputStream()));
    }

    public static Set<String> getLinks(String html, String url){
        Set<String> S = new HashSet<>();
        for(Element x : Jsoup.parse(html,url).getElementsByTag("a")) {
            String s = x.absUrl("href");
            if(!s.isEmpty())
                S.add(s);
        }
        return S;
    }

    public static Set<String> getImages(String html, String url) {
        Set<String> S = new HashSet<>();
        for(Element x : Jsoup.parse(html, url).getElementsByTag("img")) {
            String s = x.absUrl("src");
            if(!s.isEmpty())
                S.add(s);
        }
        return S;
    }

    public static long getImageSize(String url) throws IOException{
        URL image = new URL(url);
        HttpURLConnection imageconn = (HttpURLConnection) image.openConnection();
        imageconn.setRequestMethod("HEAD");
        long ret = imageconn.getContentLengthLong();
        imageconn.getInputStream().close();
        return ret;
    }
}
