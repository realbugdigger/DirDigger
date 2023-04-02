package utils;

import core.DiggerNode;
import org.apache.http.*;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

public class DebugRedirectStrategy implements RedirectStrategy {

    public static final int SC_PERMANENT_REDIRECT = 308;

    private final String[] redirectMethods = {
            HttpGet.METHOD_NAME,
            HttpPost.METHOD_NAME,
            HttpHead.METHOD_NAME,
            HttpDelete.METHOD_NAME
    };

    private final JTree tree;

    public DebugRedirectStrategy(JTree tree) {
        this.tree = tree;
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");

        final int statusCode = response.getStatusLine().getStatusCode();
        final String method = request.getRequestLine().getMethod();
        final Header locationHeader = response.getFirstHeader("location");
        switch (statusCode) {
            case HttpStatus.SC_MOVED_TEMPORARILY:
                if (isRedirectable(method) && locationHeader != null) {
                    System.out.println("[REDIRECT]" + request.getRequestLine().getUri() + " ===========> " + locationHeader.getValue());
                    if (treeContainsUrl(locationHeader.getValue()))
                        return false;
                    return true;
                }
                return false;
            case HttpStatus.SC_MOVED_PERMANENTLY:
            case HttpStatus.SC_TEMPORARY_REDIRECT:
            case SC_PERMANENT_REDIRECT:
                if (isRedirectable(method)) {
                    System.out.println("[REDIRECT]" + request.getRequestLine().getUri() + " ===========> " + locationHeader);
                    if (treeContainsUrl(locationHeader.getValue()))
                        return false;
                    return true;
                }
                return false;
            case HttpStatus.SC_SEE_OTHER:
                return true;
            default:
                return false;
        } //end of switch
    }

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        final URI uri = getLocationURI(request, response, context);
        final String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
            return new HttpHead(uri);
        } else if (method.equalsIgnoreCase(HttpGet.METHOD_NAME)) {
            return new HttpGet(uri);
        } else {
            final int status = response.getStatusLine().getStatusCode();
            return (status == HttpStatus.SC_TEMPORARY_REDIRECT || status == SC_PERMANENT_REDIRECT)
                    ? RequestBuilder.copy(request).setUri(uri).build()
                    : new HttpGet(uri);
        }
    }

    public URI getLocationURI(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        //get the location header to find out where to redirect to
        final Header locationHeader = response.getFirstHeader("location");
        if (locationHeader == null) {
            // got a redirect response, but no location header
            throw new ProtocolException(
                    "Received redirect response " + response.getStatusLine()
                            + " but no location header");
        }
        final String location = locationHeader.getValue();

        final RequestConfig config = clientContext.getRequestConfig();

        URI uri = createLocationURI(location);

        try {
            if (config.isNormalizeUri()) {
                uri = URIUtils.normalizeSyntax(uri);
            }

            // rfc2616 demands the location value be a complete URI
            // Location       = "Location" ":" absoluteURI
            if (!uri.isAbsolute()) {
                if (!config.isRelativeRedirectsAllowed()) {
                    throw new ProtocolException("Relative redirect location '"
                            + uri + "' not allowed");
                }
                // Adjust location URI
                final HttpHost target = clientContext.getTargetHost();
                Asserts.notNull(target, "Target host");
                final URI requestURI = new URI(request.getRequestLine().getUri());
                final URI absoluteRequestURI = URIUtils.rewriteURI(requestURI, target,
                        config.isNormalizeUri() ? URIUtils.NORMALIZE : URIUtils.NO_FLAGS);
                uri = URIUtils.resolve(absoluteRequestURI, uri);
            }
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }

        RedirectLocations redirectLocations = (RedirectLocations) clientContext.getAttribute(
                HttpClientContext.REDIRECT_LOCATIONS);
        if (redirectLocations == null) {
            redirectLocations = new RedirectLocations();
            context.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, redirectLocations);
        }
        if (!config.isCircularRedirectsAllowed()) {
            if (redirectLocations.contains(uri)) {
                throw new CircularRedirectException("Circular redirect to '" + uri + "'");
            }
        }
        redirectLocations.add(uri);
        return uri;
    }

    protected URI createLocationURI(final String location) throws ProtocolException {
        try {
            return new URI(location);
        } catch (final URISyntaxException ex) {
            throw new ProtocolException("Invalid redirect URI: " + location, ex);
        }
    }

    protected boolean isRedirectable(final String method) {
        return Arrays.binarySearch(redirectMethods, method) >= 0;
    }

    private boolean treeContainsUrl(String url) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode parent = JTreeUtils.findParentNode(url, root);
        DiggerNode parentDiggerNode = (DiggerNode) parent.getUserObject();
        return parentDiggerNode.getUrl().equals(url);
    }
}
