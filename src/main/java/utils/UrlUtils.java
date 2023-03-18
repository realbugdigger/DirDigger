package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlUtils {

    private static final String DIRECTORY_PATH_SEPARATOR = "/";

    public static List<String> getDirectoriesOfUrl(String urlPath) {
        ArrayList<String> directories = new ArrayList<String>();
        Pattern regex = Pattern.compile("[^" + DIRECTORY_PATH_SEPARATOR + "]+");
        Matcher matcher = regex.matcher(urlPath);
        while (matcher.find())
            directories.add(matcher.group());

        return directories;
    }
}
