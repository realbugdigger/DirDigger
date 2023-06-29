package core;

import java.io.Serial;
import java.io.Serializable;

public class ThreadStateInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int iterator;
    private int currentDepth;
    private String url;

    public int getIterator() {
        return iterator;
    }

    public void setIterator(int iterator) {
        this.iterator = iterator;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public void setCurrentDepth(int currentDepth) {
        this.currentDepth = currentDepth;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "ThreadStateInfo{" +
                "iterator=" + iterator +
                ", currentDepth=" + currentDepth +
                ", url='" + url + '\'' +
                '}';
    }
}
