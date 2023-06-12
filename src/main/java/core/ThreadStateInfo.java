package core;

import java.io.Serial;
import java.io.Serializable;

public class ThreadStateInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int i;
    private String url;

    public int getI() {
        return i;
    }

    public void setI(int i) {
        this.i = i;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
