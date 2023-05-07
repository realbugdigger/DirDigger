package service;

import org.apache.hc.core5.http.HttpEntity;

public interface IHttpService {

    void addResponseToBucket(String url, HttpEntity httpEntity);
}
