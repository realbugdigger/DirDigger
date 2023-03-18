package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlUtils {

    private static final String DIRECTORY_PATH_SEPARATOR = "/";

    public enum HttpResponseCodeStatus {
        INFORMATIONAL, SUCCESS, REDIRECTION, CLIENT_ERROR, SERVER_ERROR;
    }

    public static HttpResponseCodeStatus getResponseStatus(int responseCode) {
        if (responseCode >= 100 && responseCode < 200) {
            return HttpResponseCodeStatus.INFORMATIONAL;
        } else if (responseCode >= 200 && responseCode < 300) {
            return HttpResponseCodeStatus.SUCCESS;
        } else if (responseCode >= 300 && responseCode < 400) {
            return HttpResponseCodeStatus.REDIRECTION;
        } else if (responseCode >= 400 && responseCode < 500) {
            return HttpResponseCodeStatus.CLIENT_ERROR;
        }
        return HttpResponseCodeStatus.SERVER_ERROR;
    }

    public static List<String> getDirectoriesOfUrl(String urlPath) {
        ArrayList<String> directories = new ArrayList<String>();
        Pattern regex = Pattern.compile("[^" + DIRECTORY_PATH_SEPARATOR + "]+");
        Matcher matcher = regex.matcher(urlPath);
        while (matcher.find())
            directories.add(matcher.group());

        return directories;
    }
}
