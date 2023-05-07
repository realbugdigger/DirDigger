package service;

import org.apache.hc.core5.http.HttpEntity;

import java.util.*;

public class HttpService implements IHttpService {

    private Map<Long, List<String>> bucketContent = new HashMap<>();
    private Map<Long, List<String>> bucketContentLength = new HashMap<>();

    @Override
    public void addResponseToBucket(String url, HttpEntity httpEntity) {

        long hashContentLength = Objects.hash(httpEntity.getContentLength());
        if (bucketContentLength.containsKey(hashContentLength)) {

        } else {
            List<String> urlList = new ArrayList<>();
            urlList.add(url);
            bucketContentLength.put(hashContentLength, urlList);
        }
    }
}
