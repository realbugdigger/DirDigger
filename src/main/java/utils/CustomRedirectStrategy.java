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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.tree.DefaultMutableTreeNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class CustomRedirectStrategy implements RedirectStrategy {

    private static final Logger log = LoggerFactory.getLogger(CustomRedirectStrategy.class);

    public static final int SC_PERMANENT_REDIRECT = 308;

    private final String[] redirectMethods = {
            HttpGet.METHOD_NAME,
            HttpPost.METHOD_NAME,
            HttpHead.METHOD_NAME,
            HttpDelete.METHOD_NAME
    };

    private final String targetUrl;
    private final WrappedJTree tree;

    public CustomRedirectStrategy(String scheme, String hostname, WrappedJTree tree) {
        targetUrl = scheme + "://" + hostname;
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
        String redirectedUrl = targetUrl + request.getRequestLine().getUri();
        String redirectedToUrl = locationHeader.getValue();

        log.debug("Location: {}", locationHeader.getValue());
        if (!UrlUtils.getScheme(redirectedToUrl).equals("http") && !UrlUtils.getScheme(redirectedToUrl).equals("https")) {
            if (redirectedToUrl.charAt(0) == '/') {
                redirectedToUrl = targetUrl + redirectedToUrl;
            } else {
                redirectedToUrl = targetUrl + "/" + redirectedToUrl;
            }
        }
        log.debug("[Start of editTree] variable redirectedToUrl ===> {}", redirectedToUrl);

        // if redirectedToUrl contains params, remove them (there's chance that param values are volatile)
        try {
            redirectedToUrl = UrlUtils.getUrlWithoutParameters(redirectedToUrl);
            log.debug("[REMOVING PARAMS] From {} ==> {}", locationHeader.getValue(), redirectedToUrl);
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }

        log.debug("[REDIRECT] {} ===========> {}", redirectedUrl, redirectedToUrl);

        DefaultMutableTreeNode treeRoot = (DefaultMutableTreeNode) tree.getTree().getModel().getRoot();
        DefaultMutableTreeNode redirectedTreeRoot = (DefaultMutableTreeNode) tree.getRedirectTree().getModel().getRoot();

        // this first check is for when multiple redirects happen: http://example.org/directoryOne => http://example.org/directoryTwo => http://example.org/directoryThree
        //                              In this case http://example.org/directoryTwo will be at root level of redirectTree
        if (JTreeUtils.containsAtRootLevel(redirectedUrl, redirectedTreeRoot) && UrlUtils.notEqualUrl(redirectedUrl, redirectedToUrl)) {
            DefaultMutableTreeNode redirectedNode = JTreeUtils.getNode(redirectedUrl, redirectedTreeRoot);
            log.debug("{} is present in redirect tree at root level, removing it from parent {}", redirectedUrl, redirectedNode.getUserObject());
            JTreeUtils.removeChildFromParent(redirectedNode, redirectedTreeRoot);

            DefaultMutableTreeNode redirectedNodeParent = JTreeUtils.getNodeV2(redirectedToUrl, redirectedTreeRoot);
            // if redirectedNodeParent is null, create redirectedToUrlNode at redirect tree root level
            if (redirectedNodeParent == null) {
                log.debug("{} is not present in redirect tree, adding it now as parent for {}", redirectedToUrl, redirectedUrl);
                DiggerNode redirectedDiggerNodeParent = new DiggerNode(redirectedTreeRoot, redirectedToUrl, UrlUtils.HttpResponseCodeStatus.SUCCESS);
                redirectedNodeParent = new DefaultMutableTreeNode(redirectedDiggerNodeParent);

                JTreeUtils.addChildToParent(redirectedNode, redirectedNodeParent);
                JTreeUtils.addChildToParent(redirectedNodeParent, redirectedTreeRoot);
            } else {
                log.debug("{} is present in redirect tree, modifying it now as parent for {}", redirectedToUrl, redirectedUrl);
                JTreeUtils.addChildToParent(redirectedNode, redirectedNodeParent);
            }
        }
        // if redirected url is not present in redirected tree and redirected url is not equal to destination url, add it
        else if (JTreeUtils.notContained(redirectedUrl, redirectedTreeRoot) && UrlUtils.notEqualUrl(redirectedUrl, redirectedToUrl)) {
            log.debug("{} is not present in redirect tree, adding it now ...", redirectedUrl);

            // maybe parent was there before child
            DefaultMutableTreeNode redirectedNodeParent = JTreeUtils.getNodeV2(redirectedToUrl, redirectedTreeRoot);
            DiggerNode redirectedDiggerNode = new DiggerNode(redirectedNodeParent, redirectedUrl, UrlUtils.getResponseStatus(response.getStatusLine().getStatusCode()));
            // if redirectedNodeParent is null (parent wasn't there before child), create redirectedToUrlNode at redirect tree root level
            if (redirectedNodeParent == null) {
                log.debug("{} is not present in redirect tree, adding it now as parent for {}", redirectedToUrl, redirectedUrl);
                DiggerNode redirectedDiggerNodeParent = new DiggerNode(redirectedTreeRoot, redirectedToUrl, UrlUtils.HttpResponseCodeStatus.SUCCESS);
                redirectedNodeParent = new DefaultMutableTreeNode(redirectedDiggerNodeParent);

                DefaultMutableTreeNode child = new DefaultMutableTreeNode(redirectedDiggerNode);
                JTreeUtils.addChildToParent(child, redirectedNodeParent);
                JTreeUtils.addChildToParent(redirectedNodeParent, redirectedTreeRoot);

                log.debug("   node  {}", child.getUserObject());
                log.debug("   parent  {}", redirectedNodeParent.getUserObject());
            } else {
                log.debug("{} is present in redirect tree, modifying it now as parent for {}", redirectedToUrl, redirectedUrl);

                DefaultMutableTreeNode child = new DefaultMutableTreeNode(redirectedDiggerNode);
                JTreeUtils.addChildToParent(child, redirectedNodeParent);

                log.debug("   node {}", child.getUserObject());
                log.debug("   parent {}", redirectedNodeParent.getUserObject());
            }
        }

        if (JTreeUtils.notContainedV2(redirectedToUrl, treeRoot)) {
            log.debug("Adding {} to regular tree [from redirect]", redirectedToUrl);

            DefaultMutableTreeNode redirectedToNodeParent = JTreeUtils.findParentNode(redirectedToUrl, treeRoot);
            DiggerNode redirectedToNode = new DiggerNode(redirectedToNodeParent, redirectedToUrl, UrlUtils.HttpResponseCodeStatus.SUCCESS);
            JTreeUtils.addNode(redirectedToNode, tree.getTree());
        }

        // if redirected url is present in regular tree, remove it
        //      additional check if e.g. http://example.com/blog => http://example.com/blog/ (don't remove)
        if (JTreeUtils.contains(redirectedUrl, treeRoot) && UrlUtils.notEqualUrl(redirectedUrl, redirectedToUrl) && UrlUtils.notEqualUrl(targetUrl, redirectedToUrl)) {
            log.debug("Removing {} from regular tree",  redirectedUrl);

            DefaultMutableTreeNode node = JTreeUtils.getNode(redirectedUrl, treeRoot);
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

            log.debug("   node {}", node.getUserObject());
            log.debug("   parent {}", parent.getUserObject());
            JTreeUtils.removeChildFromParent(node, parent);
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
