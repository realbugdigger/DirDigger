package utils;

import core.DiggerNode;
import core.WrappedJTree;
import org.apache.http.*;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class CustomRedirectStrategy implements RedirectStrategy {

    public static final int SC_PERMANENT_REDIRECT = 308;

    private final String[] redirectMethods = {
            HttpGet.METHOD_NAME,
            HttpPost.METHOD_NAME,
            HttpHead.METHOD_NAME,
            HttpDelete.METHOD_NAME
    };

    private String url;
    private final WrappedJTree tree;

    public CustomRedirectStrategy(String scheme, String hostname, WrappedJTree tree) {
        url = scheme + "://" + hostname;
        this.tree = tree;
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");

        final int statusCode = response.getStatusLine().getStatusCode();
        final String method = request.getRequestLine().getMethod();
        final Header locationHeader = response.getFirstHeader("location");
        switch (statusCode) {
            case HttpStatus.SC_MOVED_TEMPORARILY:
                if (isRedirectable(method) && locationHeader != null) {
                    editTree(request, response, locationHeader);
                    return true;
                }
                return false;
            case HttpStatus.SC_MOVED_PERMANENTLY:
            case HttpStatus.SC_TEMPORARY_REDIRECT:
            case SC_PERMANENT_REDIRECT:
                if (isRedirectable(method)) {
                    editTree(request, response, locationHeader);
                    return true;
                }
                return false;
            case HttpStatus.SC_SEE_OTHER:
                return true;
            default:
                return false;
        }
    }

    private synchronized void editTree(HttpRequest request, HttpResponse response, Header locationHeader) {
        String redirectedUrl = url + request.getRequestLine().getUri();
        String redirectedToUrl = locationHeader.getValue();

        System.out.println("[REDIRECT] " + redirectedUrl + " ===========> " + redirectedToUrl);

        DefaultMutableTreeNode treeRoot = (DefaultMutableTreeNode) tree.getTree().getModel().getRoot();
        DefaultMutableTreeNode redirectedTreeRoot = (DefaultMutableTreeNode) tree.getRedirectTree().getModel().getRoot();

        // if redirected url is not present in redirected tree and redirected url is not equal to destination url, add it
        if (JTreeUtils.notContained(redirectedUrl, redirectedTreeRoot) && UrlUtils.notEqualUrl(redirectedUrl, redirectedToUrl)) {
            System.out.println("Adding " + redirectedUrl + " to redirected tree");

            DefaultMutableTreeNode redirectedNodeParent = JTreeUtils.findRedirectParentNode(redirectedUrl, redirectedTreeRoot);
            DiggerNode redirectedNode = new DiggerNode(redirectedNodeParent, redirectedUrl, UrlUtils.getResponseStatus(response.getStatusLine().getStatusCode()));
            JTreeUtils.addNode(redirectedNode, tree.getRedirectTree());
        }

        // this is done in case
        String redirectedToUrlWithoutSlash = redirectedToUrl;
        if (redirectedToUrl.endsWith("/"))
            redirectedToUrlWithoutSlash = redirectedToUrlWithoutSlash.substring(0, redirectedToUrlWithoutSlash.length() - 1);

        if (JTreeUtils.notContained(redirectedToUrl, redirectedTreeRoot) && JTreeUtils.notContained(redirectedToUrl, treeRoot) &&
                    JTreeUtils.notContained(redirectedToUrlWithoutSlash, treeRoot) && JTreeUtils.notContained(redirectedToUrl + "/", treeRoot)) {
            System.out.println("Adding " + redirectedToUrl + " to regular tree [from redirect]");

            DefaultMutableTreeNode redirectedToNodeParent = JTreeUtils.findParentNode(redirectedToUrl, treeRoot);
            DiggerNode redirectedToNode = new DiggerNode(redirectedToNodeParent, redirectedToUrl, UrlUtils.getResponseStatus(response.getStatusLine().getStatusCode()));
            JTreeUtils.addNode(redirectedToNode, tree.getRedirectTree());
        }

        // if redirected url is present in regular tree, remove it
        //      additional check if e.g. http://example.com/blog => http://example.com/blog/ (don't remove)
        if (JTreeUtils.contains(redirectedUrl, treeRoot) && UrlUtils.notEqualUrl(redirectedUrl, redirectedToUrl)) {
            System.out.println("Removing " + redirectedUrl + " from regular tree");

            DefaultMutableTreeNode node = JTreeUtils.getNode(redirectedUrl, treeRoot);
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
//            DiggerNode diggerNode = (DiggerNode) node.getUserObject();
//            diggerNode.setUrl("removed");
//            node.setUserObject(diggerNode);
            parent.remove(node);

            DefaultTreeModel model = (DefaultTreeModel) tree.getTree().getModel();
            model.reload();
        }
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
}
