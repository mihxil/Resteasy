package org.jboss.resteasy.specimpl;

import org.jboss.resteasy.spi.ResteasyUriBuilder;
import org.jboss.resteasy.util.Encode;
import org.jboss.resteasy.util.PathHelper;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.RuntimeDelegate;

/**
 * UriInfo implementation with some added extra methods to help process requests.
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ResteasyUriInfo implements UriInfo
{
   private String path;
   private String encodedPath;
   private String matchingPath;
   private MultivaluedMap<String, String> queryParameters;
   private MultivaluedMap<String, String> encodedQueryParameters;
   private MultivaluedMap<String, String> pathParameters;
   private MultivaluedMap<String, String> encodedPathParameters;
   private MultivaluedMap<String, PathSegment[]> pathParameterPathSegments;
   private MultivaluedMap<String, PathSegment[]> encodedPathParameterPathSegments;

   private List<PathSegment> pathSegments;
   private List<PathSegment> encodedPathSegments;
   private URI absolutePath;
   private URI requestURI;
   private URI baseURI;
   private List<String> matchedUris;
   private List<String> encodedMatchedUris;
   private List<String> encodedMatchedPaths = new LinkedList<String>();
   private List<Object> ancestors;
   private String absoluteString;
   private String contextPath;
   private int queryIdx;
   private int endPath;
   private int pathStart;


   public ResteasyUriInfo(final String absoluteUri, final String contextPath) {
      initialize(absoluteUri, contextPath);
   }

   protected void initialize(String absoluteUri, String contextPath) {
      this.absoluteString = absoluteUri;
      this.contextPath = contextPath;

      int pathIdx = absoluteUri.indexOf('/');
      if (pathIdx > 0 && absoluteUri.length() > 3) {
         if (absoluteUri.charAt(pathIdx - 1) == ':' && absoluteUri.charAt(pathIdx + 1) == '/') {
            pathIdx = pathIdx + 2;
            int tmp = absoluteUri.indexOf('/', pathIdx);
            if (tmp > -1) pathIdx = tmp;
         }
      }
      queryIdx = pathIdx > -1 ? absoluteUri.indexOf('?', pathIdx) : absoluteUri.indexOf('?');
      endPath = queryIdx > -1 ? queryIdx : absoluteUri.length();
      pathStart = pathIdx > -1 ? pathIdx : 0;
      String tmpEncodedPath = pathStart >= 0 && endPath > pathStart ? absoluteUri.substring(pathStart, endPath) : "";
      encodedPath = PathHelper.getEncodedPathInfo(tmpEncodedPath, contextPath);

      if (encodedPath.length() == 0 || encodedPath.charAt(0) != '/')
      {
         encodedPath = "/" + encodedPath;
      }
      path = Encode.decodePath(encodedPath);
      processPath();
   }

   private void processUris() {
      requestURI = URI.create(absoluteString);
      absolutePath = queryIdx < 0 ? requestURI : URI.create(absoluteString.substring(0, queryIdx));
      baseURI = absolutePath;
      String tmpContextPath = contextPath;
      if (!tmpContextPath.endsWith("/")) tmpContextPath += "/";
      if (!tmpContextPath.startsWith("/")) tmpContextPath = "/" + tmpContextPath;
      String baseString = absoluteString.substring(0, pathStart);
      baseString += tmpContextPath;
      baseURI = URI.create(baseString);
   }

   protected void initialize(CharSequence absoluteUri, String queryString, String contextPath)
   {
      ResteasyUriBuilder absoluteBuilder = (ResteasyUriBuilder) ((ResteasyUriBuilder) RuntimeDelegate.getInstance()
            .createUriBuilder()).uriFromCharSequence((CharSequence)absoluteUri);
      absolutePath = absoluteBuilder.build();
      requestURI = absoluteBuilder.replaceQuery(queryString).build();
      encodedPath = PathHelper.getEncodedPathInfo(absolutePath.getRawPath(), contextPath);
      baseURI = absolutePath;
      if (!encodedPath.trim().equals(""))
      {
         String tmpContextPath = contextPath;
         if (!tmpContextPath.endsWith("/")) tmpContextPath += "/";
         baseURI = absoluteBuilder.clone().replacePath(tmpContextPath).replaceQuery(null).build();
      }
//      // make sure there is no trailing '/'
//      if (encodedPath.length() > 1 && encodedPath.endsWith("/"))
//         encodedPath = encodedPath.substring(0, encodedPath.length() - 1);

      // make sure path starts with '/'
      if (encodedPath.length() == 0 || encodedPath.charAt(0) != '/')
      {
         encodedPath = "/" + encodedPath;
      }
      path = UriBuilder.fromPath(encodedPath).build().getPath();
      processPath();
   }



   public ResteasyUriInfo(final URI base, final URI relative)
   {
      String b = base.toString();
      if (!b.endsWith("/")) b += "/";
      String r = relative.getRawPath();
      if (r.startsWith("/"))
      {
         encodedPath =  r;
         path = relative.getPath();
      }
      else
      {
         encodedPath = "/" + r;
         path = "/" + relative.getPath();
      }
      UriBuilder requestUriBuilder = UriBuilder.fromUri(base).path(relative.getRawPath()).replaceQuery(relative.getRawQuery());
      requestURI = requestUriBuilder.build();
      absolutePath = requestUriBuilder.replaceQuery(null).build();
      baseURI = base;
      processPath();
   }

   public void setUri(URI base, URI relative)
   {
      clearQueryParameters(true);
      clearQueryParameters(false);

      URI rel = base.resolve(relative);
      String absoluteUri = UriBuilder.fromUri(rel).replaceQuery(null).toTemplate();
      initialize(absoluteUri, rel.getRawQuery(), base.getRawPath());
   }

   protected void processPath()
   {
      PathSegmentImpl.SegmentParse parse = PathSegmentImpl.parseSegmentsOptimization(encodedPath, false);
      encodedPathSegments = parse.segments;
      this.pathSegments = new ArrayList<PathSegment>(encodedPathSegments.size());
      for (PathSegment segment : encodedPathSegments)
      {
         pathSegments.add(new PathSegmentImpl(((PathSegmentImpl) segment).getOriginal(), true));
      }
      if (parse.hasMatrixParams) extractMatchingPath(encodedPathSegments);
      else
      {
         matchingPath = encodedPath;
         if (matchingPath.length() > 1 && matchingPath.endsWith("/"))
         {
            matchingPath = matchingPath.substring(0, matchingPath.length() - 1);
         }
      }

   }

   public ResteasyUriInfo(final URI requestURI)
   {
      initializeFromRequest(requestURI);

   }

   public void initializeFromRequest(URI requestURI)
   {
      String r = requestURI.getRawPath();
      if (r.startsWith("/"))
      {
         encodedPath = r;
         path = requestURI.getPath();
      }
      else
      {
         encodedPath = "/" + r;
         path = "/" + requestURI.getPath();
      }
      this.requestURI = requestURI;
      baseURI = UriBuilder.fromUri(requestURI).replacePath("").build();
      absolutePath = UriBuilder.fromUri(requestURI).replaceQuery(null).build();
      processPath();
   }

   /**
    * Matching path without matrix parameters.
    *
    * @param encodedPathSegments list of path segments
    */
   protected void extractMatchingPath(List<PathSegment> encodedPathSegments)
   {
      StringBuilder preprocessedPath = new StringBuilder();
      for (PathSegment pathSegment : encodedPathSegments)
      {
         preprocessedPath.append("/").append(pathSegment.getPath());
      }
      matchingPath = preprocessedPath.toString();
   }

   /**
    * Encoded path without matrix parameters.
    *
    * @return matching path
    */
   public String getMatchingPath()
   {
      return matchingPath;
   }

   /**
    * Create a UriInfo from the baseURI.
    *
    * @param relative relative uri
    */
   public void setRequestUri(URI relative)
   {
      if (baseURI == null) processUris();
      setUri(baseURI, relative);
   }

   public String getPath()
   {
      return path;
   }

   public String getPath(boolean decode)
   {
      if (decode) return getPath();
      return encodedPath;
   }

   public List<PathSegment> getPathSegments()
   {
      return pathSegments;
   }

   public List<PathSegment> getPathSegments(boolean decode)
   {
      if (decode) return getPathSegments();
      return encodedPathSegments;
   }

   public URI getRequestUri()
   {
      if (requestURI == null) processUris();
      return requestURI;
   }

   public UriBuilder getRequestUriBuilder()
   {
      return UriBuilder.fromUri(getRequestUri());
   }

   public URI getAbsolutePath()
   {
      if (absolutePath == null) processUris();
      return absolutePath;
   }

   public UriBuilder getAbsolutePathBuilder()
   {
      return UriBuilder.fromUri(getAbsolutePath());
   }

   public URI getBaseUri()
   {
      if (baseURI == null) processUris();
      return baseURI;
   }

   public UriBuilder getBaseUriBuilder()
   {
      return UriBuilder.fromUri(getBaseUri());
   }

   public MultivaluedMap<String, String> getPathParameters()
   {
      if (pathParameters == null)
      {
         pathParameters = new MultivaluedMapImpl<String, String>();
      }
      return pathParameters;
   }

   public void addEncodedPathParameter(String name, String value)
   {
      getEncodedPathParameters().add(name, value);
      String value1 = Encode.decodePath(value);
      getPathParameters().add(name, value1);
   }

   private MultivaluedMap<String, String> getEncodedPathParameters()
   {
      if (encodedPathParameters == null)
      {
         encodedPathParameters = new MultivaluedMapImpl<String, String>();
      }
      return encodedPathParameters;
   }

   public MultivaluedMap<String, PathSegment[]> getEncodedPathParameterPathSegments()
   {
      if (encodedPathParameterPathSegments == null)
      {
         encodedPathParameterPathSegments = new MultivaluedMapImpl<String, PathSegment[]>();
      }
      return encodedPathParameterPathSegments;
   }

   public MultivaluedMap<String, PathSegment[]> getPathParameterPathSegments()
   {
      if (pathParameterPathSegments == null)
      {
         pathParameterPathSegments = new MultivaluedMapImpl<String, PathSegment[]>();
      }
      return pathParameterPathSegments;
   }

   public MultivaluedMap<String, String> getPathParameters(boolean decode)
   {
      if (decode) return getPathParameters();
      return getEncodedPathParameters();
   }

   public MultivaluedMap<String, String> getQueryParameters()
   {
      if (queryParameters == null) {
         extractParameters();
      }
      return new UnmodifiableMultivaluedMap<>(queryParameters);
   }

   protected MultivaluedMap<String, String> getEncodedQueryParameters()
   {
      if (encodedQueryParameters == null) {
         extractParameters();
      }
      return new UnmodifiableMultivaluedMap<>(encodedQueryParameters);
   }


   public MultivaluedMap<String, String> getQueryParameters(boolean decode)
   {
      if (decode) return getQueryParameters();
      else return getEncodedQueryParameters();
   }

   private void clearQueryParameters(boolean decode) {
      queryParameters = null;
      encodedQueryParameters = null;
   }

   protected void extractParameters()
   {
      queryParameters = new MultivaluedMapImpl<>();
      encodedQueryParameters = new MultivaluedMapImpl<>();
      String queryString = getRequestUri().getRawQuery();
      if (queryString == null || queryString.equals("")) return;

      String[] params = queryString.split("&");

      for (String param : params)
      {
         if (param.indexOf('=') >= 0)
         {
            String[] nv = param.split("=", 2);
            try
            {
               String name = URLDecoder.decode(nv[0], StandardCharsets.UTF_8.name());
               String val = nv.length > 1 ? nv[1] : "";
               encodedQueryParameters.add(name, val);
               queryParameters.add(name, URLDecoder.decode(val, StandardCharsets.UTF_8.name()));
            }
            catch (UnsupportedEncodingException e)
            {
               throw new RuntimeException(e);
            }
         }
         else
         {
            try
            {
               String name = URLDecoder.decode(param, StandardCharsets.UTF_8.name());
               encodedQueryParameters.add(name, "");
               queryParameters.add(name, "");
            }
            catch (UnsupportedEncodingException e)
            {
               throw new RuntimeException(e);
            }
         }
      }
   }

   public List<String> getMatchedURIs(boolean decode)
   {
      if (decode)
      {
         if (matchedUris == null) matchedUris = new LinkedList<String>();
         return matchedUris;
      }
      else
      {
         if (encodedMatchedUris == null) encodedMatchedUris = new LinkedList<String>();
         return encodedMatchedUris;
      }
   }

   public List<String> getMatchedURIs()
   {
      return getMatchedURIs(true);
   }

   public List<Object> getMatchedResources()
   {
      if (ancestors == null) ancestors = new LinkedList<Object>();
      return ancestors;
   }


   public void pushCurrentResource(Object resource)
   {
      if (ancestors == null) ancestors = new LinkedList<Object>();
      ancestors.add(0, resource);
   }

   public void pushMatchedPath(String encoded)
   {
      encodedMatchedPaths.add(0, encoded);
   }

   public List<String> getEncodedMatchedPaths()
   {
      return encodedMatchedPaths;
   }

   public void popMatchedPath()
   {
      encodedMatchedPaths.remove(0);
   }


   public void pushMatchedURI(String encoded)
   {
      int start = (encoded.startsWith("/")) ? 1 : 0;
      int end = (encoded.endsWith("/")) ? encoded.length() - 1 : encoded.length();
      encoded = start < end ? encoded.substring(start, end) : "";
      String decoded = Encode.decode(encoded);
      if (encodedMatchedUris == null) encodedMatchedUris = new LinkedList<String>();
      encodedMatchedUris.add(0, encoded);

      if (matchedUris == null) matchedUris = new LinkedList<String>();
      matchedUris.add(0, decoded);
   }

   @Override
   public URI resolve(URI uri)
   {
      return getBaseUri().resolve(uri);
   }

   @Override
   public URI relativize(URI uri)
   {
      URI from = getRequestUri();
      URI to = uri;
      if (uri.getScheme() == null && uri.getHost() == null)
      {
         to = getBaseUriBuilder().replaceQuery(null).path(uri.getPath()).replaceQuery(uri.getQuery()).fragment(uri.getFragment()).build();
      }
      return ResteasyUriBuilderImpl.relativize(from, to);
   }

}
