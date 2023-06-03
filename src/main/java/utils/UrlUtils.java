package utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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

    public static String getHostname(String url) {
        return url.replaceFirst("^(http[s]?://\\.|http[s]?://|\\.)","");
    }

    public static String getScheme(String url) {
        int colonIndex = url.indexOf(":");
        if (colonIndex == -1)
            return url;
        return url.substring(0, colonIndex);
    }

    public static int getPort(String url) {
        try {
            return new URL(url).getPort();
        } catch (Exception e) {
            return 80;
        }
    }

    /**
     * http://example.org/blog is equal to http://example.org/blog/
     */
    public static boolean equalUrl(String url1, String url2) {
        return url1.equals(url2) || url1.equals(url2 + "/") || url2.equals(url1 + "/");
    }

    public static boolean notEqualUrl(String url1, String url2) {
        return !equalUrl(url1, url2);
    }

    // Probably not the most efficient way, but type safe
    public static String getUrlWithoutParameters(String url) throws URISyntaxException {
        URI uri = new URI(url);
        return new URI(uri.getScheme(),
                uri.getAuthority(),
                uri.getPath(),
                null, // Ignore the query part of the input url
                uri.getFragment()).toString();
    }
}
